package xyz.scootaloo.derpycloud.service.webdav

import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.file.openOptionsOf
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.awaitBlocking
import org.slf4j.LoggerFactory
import xyz.scootaloo.derpycloud.context.Contexts
import xyz.scootaloo.derpycloud.middleware.Middlewares
import xyz.scootaloo.derpycloud.service.file.FileInfo
import xyz.scootaloo.derpycloud.service.file.UFiles
import xyz.scootaloo.derpycloud.service.file.UPaths
import xyz.scootaloo.derpycloud.service.utils.Defer
import xyz.scootaloo.derpycloud.service.utils.DeferRunner
import java.net.URL

/**
 * @author AppleSack
 * @since  2023/02/01
 */
object WebDAV {

    // webdav 服务器的默认路由前缀
    // 由于目前的设计, 管理静态资源的处理器使用了vertx的StaticHandler
    // 但StaticHandler使用了完整的uri作为文件名, 这会导致客户端不能获取到真实的文件
    // 在使用StaticHandler作为静态资源处理器时, 路由前缀只能是"/"
    const val prefix = "/"

    private val log = LoggerFactory.getLogger("webdav")

    private const val defOptionsAllow = "OPTIONS, PROPFIND, GET, HEAD, PUT, " +
            "DELETE, COPY, MOVE, MKCOL, LOCK, UNLOCK, POST"

    fun handleOptions(ctx: RoutingContext): HttpResponseStatus {
        val respHeader = ctx.response().headers()
        respHeader.add("Allow", defOptionsAllow)
        respHeader.add("DAV", "1,2")
        respHeader.add("MS-Author-Via", "DAV")
        respHeader.add("Accept-Ranges", "bytes")
        ctx.end()
        return HttpResponseStatus.OK
    }

    suspend fun handleLock(ctx: RoutingContext) {
        val timeoutHeader = ctx.request().getHeader("Timeout")
        val (valid, duration) = parseTimeout(timeoutHeader ?: "")
        if (!valid) {
            log.warn("错误的参数lock.timeout: {}", timeoutHeader)
            return ctx.terminate(HttpResponseStatus.BAD_REQUEST)
        }
        val (code, lockInfo) = DAVHelper.readLockInfo(ctx.body().asString() ?: "")
        if (code != 0) {
            return ctx.terminate(code)
        }

        var token = ""
        val lockDetails: LockDetails
        var created = false
        val us = Contexts.get(ctx)
        val ls = us.lockSystem
        val response = ctx.response()
        if (lockInfo == LockInfo.NONE) {
            // 空的锁结构代表使用当前token刷新一个锁
            val (success, ifHeader) = parseIfHeader(ctx.request().getHeader("If") ?: "")
            if (!success) {
                return ctx.terminate(HttpResponseStatus.BAD_REQUEST)
            }
            if (ifHeader.lists.size == 1 && ifHeader.lists[0].conditions.size == 1) {
                token = ifHeader.lists[0].conditions[0].token
            }
            val (ld, err) = ls.refresh(token, duration)
            if (err != Errors.None) {
                var errStatus = HttpResponseStatus.INTERNAL_SERVER_ERROR
                if (err == Errors.NoSuchLock) {
                    errStatus = HttpResponseStatus.PRECONDITION_FAILED
                }
                return ctx.terminate(errStatus)
            }
            lockDetails = ld
        } else {
            // 创建锁
            var depth = parseDepth(ctx.request().getHeader("Depth") ?: "")
            if (depth != 0 && depth != infiniteDepth) {
                // 在ms资源管理器下调用某些方法之前, 会提交没有Depth信息的Lock请求 (如Copy)
                // 为了保证这些操作能够继续进行, 将锁深度默认值设为0
                depth = 0
            }
            val reqPath = UPaths.slashClean(ctx.pathParam("*"))
            lockDetails = LockDetails(reqPath, duration, lockInfo.owner, depth == 0)
            val lockCall = ls.create(lockDetails)
            if (lockCall.second != Errors.None) {
                if (lockCall.second == Errors.Locked) {
                    return ctx.terminate(HttpResponseStatus.LOCKED)
                }
                return ctx.terminate(HttpResponseStatus.INTERNAL_SERVER_ERROR)
            }
            token = lockCall.first
            val safe = runCatching {
                // 只有文件不存在时才尝试创建文件, 如果成功状态码201, 否则状态码200
                val fs = Middlewares.vertx.fileSystem()
                val realReqPath = UPaths.realPath(us.storageSpace, reqPath)
                if (fs.exists(realReqPath).await()) {
                    return@runCatching
                }

                val file = fs.open(realReqPath, openOptionsOf(create = true)).await()
                file.close().await()
                created = true
            }
            if (safe.isFailure) {
                ls.unlock(token)
                return ctx.terminate(HttpResponseStatus.INTERNAL_SERVER_ERROR)
            }
            response.putHeader("Lock-Token", token)
        }

        if (created) {
            response.statusCode = HttpResponseStatus.CREATED.code()
        }

        response.putHeader("Content-Type", "application/xml; charset=utf-8")
        response.end(DAVHelper.writeLockInfo(token, lockDetails))
    }

    fun handleUnlock(ctx: RoutingContext) {
        var token = ctx.request().getHeader("Lock-Token")
        val status: HttpResponseStatus
        if (token == null || token.length <= 2 || token[0] != '<' || token.last() != '>') {
            status = HttpResponseStatus.BAD_REQUEST
        } else {
            token = token.substring(1, token.length - 1)
            val ls = Contexts.get(ctx).lockSystem
            status = when (ls.unlock(token)) {
                Errors.None -> HttpResponseStatus.OK
                Errors.Forbidden -> HttpResponseStatus.FORBIDDEN
                Errors.Locked -> HttpResponseStatus.LOCKED
                Errors.NoSuchLock -> HttpResponseStatus.CONFLICT
                else -> HttpResponseStatus.INTERNAL_SERVER_ERROR
            }
        }

        ctx.terminate(status)
    }

    fun handleGet(ctx: RoutingContext) {
        val storage = Contexts.getStorage(ctx)
        storage.staticResources.handle(ctx)
    }

    suspend fun handlePut(ctx: RoutingContext) = Defer.run {
        val reqPath = UPaths.slashClean(ctx.pathParam("*"))
        val (release, code) = confirmLocks(ctx, reqPath, "")
        defer { release() }
        if (code != 0) {
            return@run ctx.terminate(code)
        }

        var status = HttpResponseStatus.OK
        val safe = runCatching {
            val storage = Contexts.get(ctx).storageSpace
            if (!UFiles.isParentPathExists(storage, reqPath)) {
                status = HttpResponseStatus.CONFLICT
                throw RuntimeException()
            }

            val exists = UFiles.isPathExists(storage, reqPath).first
            if (exists) {
                status = HttpResponseStatus.CREATED
            }

            val options = openOptionsOf(
                create = true, read = true, write = true, truncateExisting = true
//                , perms = "rw-rw-rw-" // windows环境不支持使用posix的api来设定访问属性
            )
            val file = UFiles.open(storage, reqPath, options)
            ctx.request().resume()
            ctx.request().pipeTo(file).await()
            val (has, fileInfo) = UFiles.isPathExists(storage, reqPath)
            if (has) {
                ctx.response().putHeader("ETag", UFiles.findETag(fileInfo))
                log.info("upload file: $reqPath, filesize: ${UFiles.readableFilesize(fileInfo.size)}")
            }
        }

        if (safe.isFailure) {
            log.error("webdav put error", safe.exceptionOrNull())
            return@run ctx.terminate(HttpResponseStatus.INTERNAL_SERVER_ERROR)
        }

        return@run ctx.terminate(status)
    }

    suspend fun handleMkCol(ctx: RoutingContext) = Defer.run {
        val reqPath = UPaths.slashClean(ctx.pathParam("*"))
        var (release, status) = confirmLocks(ctx, reqPath, "")
        if (status != 0) {
            return@run ctx.terminate(status)
        }
        defer { release() }

        status = HttpResponseStatus.INTERNAL_SERVER_ERROR.code()
        val safe = runCatching {
            val contentLength = ctx.request().getHeader("Content-Length") ?: ""
            if (contentLength.isNotEmpty() && contentLength.toInt() > 0) {
                status = HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE.code()
                throw RuntimeException("ignore")
            }
            val storage = Contexts.getStorage(ctx)
            UFiles.makeDir(storage, reqPath)
        }

        if (safe.isFailure) {
            val ex = safe.exceptionOrNull()!!
            if (ex.message != "ignore") {
                log.error("webdav mkcol error", ex)
            }
            if (UFiles.isFileNotExists(safe)) {
                return@run ctx.terminate(HttpResponseStatus.CONFLICT)
            }
            return@run ctx.terminate(status)
        }

        ctx.terminate(HttpResponseStatus.CREATED)
    }

    suspend fun handleCopyMove(ctx: RoutingContext) {
        val destHeader = ctx.request().getHeader("Destination") ?: ""
        if (destHeader == "") {
            return ctx.terminate(HttpResponseStatus.BAD_REQUEST)
        }
        val urlParseCall = runCatching { URL(destHeader) }
        if (urlParseCall.isFailure) {
            return ctx.terminate(HttpResponseStatus.BAD_REQUEST)
        }
        val url = urlParseCall.getOrThrow()
        if (!isSameHost(url, ctx.request())) {
            return ctx.terminate(HttpResponseStatus.BAD_GATEWAY)
        }

        val src = UPaths.slashClean(ctx.pathParam("*"))
        val dst = UPaths.decodeUri(url.path ?: "")

        if (dst == "") {
            return ctx.terminate(HttpResponseStatus.BAD_GATEWAY)
        }
        if (dst == src) {
            return ctx.terminate(HttpResponseStatus.FORBIDDEN)
        }

        val storage = Contexts.getStorage(ctx)
        if (ctx.request().method() == HttpMethod.COPY) {
            // 处理 Copy 请求
            return Defer.run {
                val (release, status) = confirmLocks(ctx, "", dst)
                if (status != 0) {
                    return@run ctx.terminate(status)
                }
                defer { release() }

                var depth = infiniteDepth
                val depthHeader = ctx.request().getHeader("Depth") ?: ""
                if (depthHeader != "") {
                    depth = parseDepth(depthHeader)
                    if (depth != 0 && depth != infiniteDepth) {
                        return@run ctx.terminate(HttpResponseStatus.BAD_REQUEST)
                    }
                }
                val overwrite = ctx.request().getHeader("Overwrite") != "F"
                val safeCall = runCatching {
                    UFiles.copyFiles(storage, src, dst, overwrite, depth, 0)
                }

                if (safeCall.isFailure) {
                    if (UFiles.isFileNotExists(safeCall)) {
                        return@run ctx.terminate(HttpResponseStatus.NOT_FOUND)
                    }
                    log.error("webdav copy error", safeCall.exceptionOrNull())
                    return@run ctx.terminate(HttpResponseStatus.FORBIDDEN)
                }
                return@run ctx.terminate(safeCall.getOrThrow())
            }

        }

        // 处理 Move 请求

        return Defer.run {
            val (release, status) = confirmLocks(ctx, src, dst)
            if (status != 0) {
                return@run ctx.terminate(status)
            }
            defer { release() }

            val depthHeader = ctx.request().getHeader("Depth") ?: ""
            if (depthHeader != "" && parseDepth(depthHeader) != infiniteDepth) {
                return@run ctx.terminate(HttpResponseStatus.BAD_REQUEST)
            }

            val overwrite = ctx.request().getHeader("Overwrite") == "T"
            val safeCall = runCatching { UFiles.moveFiles(storage, src, dst, overwrite) }
            if (safeCall.isFailure) {
                if (UFiles.isFileNotExists(safeCall)) {
                    return@run ctx.terminate(HttpResponseStatus.NOT_FOUND)
                }
                log.error("webdav move error", safeCall.exceptionOrNull())
                return@run ctx.terminate(HttpResponseStatus.FORBIDDEN)
            }
            return@run ctx.terminate(safeCall.getOrThrow())
        }
    }

    suspend fun handleDelete(ctx: RoutingContext) = Defer.run {
        val reqPath = UPaths.slashClean(ctx.pathParam("*"))
        val (release, status) = confirmLocks(ctx, reqPath, "")
        if (status != 0) {
            return@run ctx.terminate(status)
        }
        defer { release() }

        val fs = Middlewares.vertx.fileSystem()
        val storage = Contexts.getStorage(ctx)
        val reqRealPath = UPaths.realPath(storage, reqPath)
        val reqRealPathExists = fs.exists(reqRealPath).await()
        if (!reqRealPathExists) {
            return@run ctx.terminate(HttpResponseStatus.NOT_FOUND)
        }
        val safeDelete = runCatching { fs.deleteRecursive(reqRealPath, true).await() }
        if (safeDelete.isFailure) {
            return@run ctx.terminate(HttpResponseStatus.METHOD_NOT_ALLOWED)
        }
        ctx.terminate(HttpResponseStatus.NO_CONTENT)
    }

    suspend fun handlePropFind(ctx: RoutingContext) {
        val reqPath = UPaths.slashClean(ctx.pathParam("*"))
        val storage = Contexts.getStorage(ctx)
        val (exists, fi) = UFiles.isPathExists(storage, reqPath)
        if (!exists) {
            return ctx.terminate(HttpResponseStatus.NOT_FOUND)
        }
        var depth = infiniteDepth
        val depthHeader = ctx.request().getHeader("Depth") ?: ""
        if (depthHeader.isNotEmpty()) {
            depth = parseDepth(depthHeader)
        }
        val (success, pf) = awaitBlocking { DAVHelper.readPropFind(ctx.body().asString() ?: "") }
        if (!success) {
            return ctx.terminate(HttpResponseStatus.BAD_REQUEST)
        }

        val render = MultiStatusRender()
        val walkFun = fun(_: String, info: FileInfo): Errors {
            var pStats: MutableList<PropStat> = ArrayList()
            if (pf.propName) {
                val pStat = PropStat()
                for (name in propNames()) {
                    pStat.props.add(Property(name, info, { _, _ -> }))
                }
                pStats.add(pStat)
            } else if (pf.allProp) {
                pStats = allProp(info, pf.props)
            } else {
                pStats = props(info, pf.props)
            }
            var href = info.path
            if (href != "/" && info.isDir) {
                href += "/"
            }

            render.addResp(makePropStatResponse(href, pStats))
            return Errors.None
        }

        val fs = Middlewares.vertx.fileSystem()
        val walkErr = UFiles.walkFS(storage, fs, fi, depth, reqPath, walkFun)
        if (walkErr != Errors.None) {
            return ctx.terminate(HttpResponseStatus.INTERNAL_SERVER_ERROR)
        }
        ctx.response().putHeader("Content-Type", "text/xml; charset=utf-8")
        ctx.response().statusCode = HttpResponseStatus.MULTI_STATUS.code()
        ctx.end(render.buildXML())
    }

    suspend fun handlePropPatch(ctx: RoutingContext) = Defer.run {
        val reqPath = UPaths.slashClean(ctx.pathParam("*"))
        val (release, status) = confirmLocks(ctx, reqPath, "")
        if (status != 0) {
            return@run ctx.terminate(status)
        }
        defer { release() }

        val fs = Middlewares.vertx.fileSystem()
        val storage = Contexts.getStorage(ctx)
        if (!fs.exists(UPaths.realPath(storage, reqPath)).await()) {
            return@run ctx.terminate(HttpResponseStatus.METHOD_NOT_ALLOWED)
        }
        val (patchList, err) = DAVHelper.readPropPatch(ctx.body().asString() ?: "")
        if (err != 0) {
            return@run ctx.terminate(err)
        }

        val render = MultiStatusRender()
        val (pStats, patchCallError) = patch(patchList)
        if (patchCallError != 0) {
            return@run ctx.terminate(HttpResponseStatus.METHOD_NOT_ALLOWED)
        }
        render.addResp(makePropStatResponse(reqPath, pStats))
        ctx.response().putHeader("Content-Type", "text/xml; charset=utf-8")
        ctx.response().statusCode = HttpResponseStatus.MULTI_STATUS.code()
        ctx.end(render.buildXML())
    }

    private fun allProp(fi: FileInfo, include: List<XName>): MutableList<PropStat> {
        val pNames = propNames()
        val nameMap = HashMap<XName, Boolean>()
        for (pName in pNames) {
            nameMap[pName] = true
        }
        for (pName in include) {
            if (pName !in nameMap) {
                pNames.add(pName)
            }
        }

        return props(fi, pNames)
    }

    private fun props(fi: FileInfo, pNames: List<XName>): MutableList<PropStat> {
        val pStatOk = PropStat(status = HttpResponseStatus.OK)
        val pStatNotFount = PropStat(status = HttpResponseStatus.NOT_FOUND)
        for (pName in pNames) {
            var process = false
            if (pName in Props.liveProps) {
                val (dir, render) = Props.liveProps[pName]!!
                if (dir || !fi.isDir) {
                    pStatOk.props.add(Property(pName, fi, render))
                    process = true
                }
            }
            if (!process && pName !in Props.liveProps) {
                pStatNotFount.props.add(Property(pName, fi, { _, _ -> }))
            }
        }
        return makePropStats(pStatOk, pStatNotFount)
    }

    private fun makePropStats(x: PropStat, y: PropStat): MutableList<PropStat> {
        val pStats = ArrayList<PropStat>(2)
        if (x.props.isNotEmpty()) {
            pStats.add(x)
        }
        if (y.props.isNotEmpty()) {
            pStats.add(y)
        }
        if (pStats.isEmpty()) {
            pStats.add(PropStat(status = HttpResponseStatus.OK))
        }
        return pStats
    }

    private fun propNames(): MutableList<XName> {
        val copy = ArrayList<XName>(Props.livePropNames.size)
        copy.addAll(Props.livePropNames)
        return copy
    }

    private fun makePropStatResponse(href: String, pStats: List<PropStat>): MultiResponse {
        return MultiResponse(href, pStats)
    }

    private fun patch(patchList: List<PropPatch>): Pair<List<PropStat>, Int> {
        var conflict = false
        loop@ for (patch in patchList) {
            for (prop in patch.props) {
                if (prop.name in Props.liveProps) {
                    conflict = true
                    break@loop
                }
            }
        }
        if (conflict) {
            val pStatForbidden = PropStat(status = HttpResponseStatus.FORBIDDEN)
            val pStatFailedDep = PropStat(status = HttpResponseStatus.FAILED_DEPENDENCY)
            for (patch in patchList) {
                for (prop in patch.props) {
                    if (prop.name in Props.liveProps) {
                        pStatForbidden.props.add(Property(prop.name, FileInfo.NONE, { _, _ -> }))
                    } else {
                        pStatFailedDep.props.add(Property(prop.name, FileInfo.NONE, { _, _ -> }))
                    }
                }
            }
            return makePropStats(pStatForbidden, pStatFailedDep) to 0
        }

        val pStatOk = PropStat(status = HttpResponseStatus.OK)
        for (patch in patchList) {
            for (prop in patch.props) {
                pStatOk.props.add(Property(prop.name, FileInfo.NONE, { _, _ -> }))
            }
        }
        return listOf(pStatOk) to 0
    }

    private suspend fun DeferRunner<Unit>.confirmReqPathAndTakeLock(
        ctx: RoutingContext
    ) {
        TODO()
    }

    private fun confirmLocks(ctx: RoutingContext, src: String, dst: String): Pair<() -> Unit, Int> {
        val ifHeader = ctx.request().getHeader("If") ?: ""
        var srcToken = ""
        var dstToken = ""
        if (ifHeader == "") {
            if (src != "") {
                val call = lock(ctx, src)
                if (call.second != 0) {
                    return {} to call.second
                }
                srcToken = call.first
            }
            if (dst != "") {
                val call = lock(ctx, dst)
                if (call.second != 0) {
                    if (srcToken != "") {
                        Contexts.get(ctx).lockSystem.unlock(srcToken)
                    }
                    return {} to call.second
                }
                dstToken = call.first
            }
        }

        return {
            val lockSystem = Contexts.get(ctx).lockSystem
            if (srcToken != "") {
                lockSystem.unlock(srcToken)
            }
            if (dstToken != "") {
                lockSystem.unlock(dstToken)
            }
        } to 0
    }

    private fun lock(ctx: RoutingContext, root: String): Pair<String, Int> {
        val ls = Contexts.get(ctx).lockSystem
        val (token, error) = ls.create(LockDetails(root, infiniteTimeoutSeconds, zeroDepth = true))
        if (error != Errors.None) {
            if (error == Errors.Locked) {
                return "" to HttpResponseStatus.LOCKED.code()
            }
            return "" to HttpResponseStatus.INTERNAL_SERVER_ERROR.code()
        }
        return token to 0
    }

    private const val infiniteDepth = -1
    private const val invalidDepth = -2

    private fun parseDepth(s: String): Int {
        for (ch in s) {
            when (ch) {
                '0' -> return 0
                '1' -> return 1
                'y' -> return infiniteDepth
            }
        }
        return invalidDepth
    }

    private const val defTimeoutSeconds = 5L
    private const val maxTimeoutSeconds = 232 - 1L
    private const val infiniteTimeoutSeconds = -1L

    // 单位秒
    private fun parseTimeout(text: String): Pair<Boolean, Long> {
        if (text.isBlank()) {
            return true to defTimeoutSeconds
        }
        var timeoutStr = text
        val i = timeoutStr.lastIndexOf(',')
        if (i >= 0) {
            timeoutStr = timeoutStr.substring(0, i)
        }
        timeoutStr = timeoutStr.trim()
        if (timeoutStr == "Infinite") {
            return true to infiniteTimeoutSeconds
        }
        val prefix = "Second-"
        if (!timeoutStr.startsWith(prefix)) {
            return false to 0
        }
        val timeoutSuffix = timeoutStr.substring(prefix.length + 1)
        if (timeoutSuffix == "" || timeoutSuffix[0] < '0' || timeoutSuffix[0] > '9') {
            return false to 0
        }
        val intResult = runCatching { timeoutSuffix.toLong() }
        if (intResult.isFailure) {
            return false to 0
        }
        val timeout = intResult.getOrElse { defTimeoutSeconds }
        if (timeout < 0 || timeout > maxTimeoutSeconds) {
            return true to maxTimeoutSeconds
        }
        return true to timeout
    }

    private fun isSameHost(url: URL, request: HttpServerRequest): Boolean {
        // todo 比较 url.host 和 request.host, 内网环境和公网环境可能不同
        return true
    }

    private fun RoutingContext.terminate(status: HttpResponseStatus) {
        terminate(status.code())
    }

    private fun RoutingContext.terminate(status: Int) {
        response().statusCode = status
        end()
    }

}
