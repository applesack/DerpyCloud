package xyz.scootaloo.server.service.webdav

import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.file.openOptionsOf
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.awaitBlocking
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import xyz.scootaloo.server.context.Contexts
import xyz.scootaloo.server.middleware.Middlewares
import xyz.scootaloo.server.service.file.FileInfo
import xyz.scootaloo.server.service.file.UFiles
import xyz.scootaloo.server.service.file.UPaths
import java.io.File

/**
 * @author AppleSack
 * @since  2023/02/01
 */
object WebDAV {

    const val prefix = "/dav"

    private val log = LoggerFactory.getLogger("webdav")

    private const val defOptionsAllow = "OPTIONS, LOCK, PUT, MKCOL"
    private const val fileOptionsAllow = "OPTIONS, LOCK, GET, HEAD, POST, DELETE, PROPPATCH," +
            " COPY, MOVE, UNLOCK, PROPFIND, PUT"
    private const val dirOptionsAllow = "OPTIONS, LOCK, DELETE, PROPPATCH, COPY, MOVE, UNLOCK, PROPFIND"

    suspend fun options(ctx: RoutingContext): HttpResponseStatus {
        val reqPath = ctx.pathParam("*")
        val storage = Contexts.getStorage(ctx)
        val (exists, fi) = UFiles.isPathExists(storage, reqPath)
        var allow = defOptionsAllow
        if (exists) {
            allow = if (fi.isDir) {
                dirOptionsAllow
            } else {
                fileOptionsAllow
            }
        }
        val respHeader = ctx.response().headers()
        respHeader.add("Allow", allow)
        respHeader.add("DAV", "1, 2")
        respHeader.add("MS-Author-Via", "DAV")
        ctx.end()
        return HttpResponseStatus.OK
    }

    suspend fun lock(ctx: RoutingContext): HttpResponseStatus {
        val timeoutHeader = ctx.request().getHeader("Timeout")
        val (valid, duration) = parseTimeout(timeoutHeader ?: "")
        if (!valid) {
            log.warn("错误的参数lock.timeout: {}", timeoutHeader)
            return HttpResponseStatus.BAD_REQUEST
        }
        val (code, lockInfo) = DAVHelper.readLockInfo(ctx.body().asString() ?: "")
        if (code != 0) {
            return HttpResponseStatus.valueOf(code)
        }

        var token = ""
        var lockDetails: LockDetails = LockDetails.NONE
        var created = false
        val us = Contexts.getOrCreate(ctx)
        val ls = us.lock
        val response = ctx.response()
        if (lockInfo == LockInfo.NONE) {
            // 空的锁结构代表使用当前token刷新一个锁
            val (success, ifHeader) = parseIfHeader(ctx.body().asString() ?: "")
            if (!success) {
                return HttpResponseStatus.BAD_REQUEST
            }
            if (ifHeader.lists.size == 1 && ifHeader.lists[0].conditions.size == 1) {
                token = ifHeader.lists[0].conditions[0].token
            }
            val (ld, err) = ls.refresh(token, duration)
            if (err != Errors.None) {
                if (err == Errors.NoSuchLock) {
                    return HttpResponseStatus.PRECONDITION_FAILED
                }
                return HttpResponseStatus.INTERNAL_SERVER_ERROR
            }
        } else {
            // 创建锁
            val depth = parseDepth(ctx.request().getHeader("Depth") ?: "")
            if (depth != 0 && depth != infiniteDepth) {
                return HttpResponseStatus.BAD_REQUEST
            }
            val reqPath = ctx.pathParam("*")
            lockDetails = LockDetails(reqPath, duration, lockInfo.owner, depth == 0)
            val r = ls.create(lockDetails)
            if (r.second != Errors.None) {
                if (r.second == Errors.Locked) {
                    return HttpResponseStatus.LOCKED
                }
                return HttpResponseStatus.INTERNAL_SERVER_ERROR
            }
            token = r.first
            val safe = runCatching {
                val fs = Middlewares.vertx.fileSystem()
                val file = fs.open(UPaths.realPath(us.storageSpace, reqPath), openOptionsOf(create = true)).await()
                file.close().await()
            }
            if (safe.isFailure) {
                ls.unlock(token)
                return HttpResponseStatus.BAD_REQUEST
            }
            created = true
            response.putHeader("Lock-Token", "<$token>")
        }

        response.putHeader("Content-Type", "application/xml; charset=utf-8")
        if (created) {
            response.statusCode = HttpResponseStatus.CREATED.code()
        }

        response.end(DAVHelper.writeLockInfo(token, lockDetails))
        return HttpResponseStatus.OK
    }

    fun get(ctx: RoutingContext) {
        val storage = Contexts.getStorage(ctx)
        storage.staticResources.handle(ctx)
    }

    fun put(ctx: RoutingContext) {
        Middlewares.coroutine.launch {
            val fs = Middlewares.vertx.fileSystem()
            val homeDir = "derpy"
            if (!fs.exists(homeDir).await()) {
                fs.mkdirs(homeDir).await()
            }
            val destPath = homeDir + File.separator + ctx.pathParam("*")
            val destFile = fs.open(destPath, openOptionsOf()).await()
            ctx.request().pipeTo(destFile).await()
            ctx.end("ok")
        }
    }

    suspend fun propfind(ctx: RoutingContext): HttpResponseStatus {
        val reqPath = ctx.pathParam("*")
        val storage = Contexts.getStorage(ctx)
        val (exists, fi) = UFiles.isPathExists(storage, reqPath)
        if (!exists) {
            return HttpResponseStatus.NOT_FOUND
        }
        var depth = infiniteDepth
        val depthHeader = ctx.request().getHeader("Depth")
        if (depthHeader == null || depthHeader.isEmpty()) {
            depth = parseDepth(depthHeader ?: "1")
        }
        val (success, pf) = awaitBlocking { DAVHelper.readPropfind(ctx.body().asString() ?: "") }
        if (!success) {
            return HttpResponseStatus.BAD_REQUEST
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

        val fs = Contexts.vertx.fileSystem()
        val walkErr = UFiles.walkFS(storage, fs, fi, depth, reqPath, walkFun)
        if (walkErr != Errors.None) {
            return HttpResponseStatus.INTERNAL_SERVER_ERROR
        }
        ctx.response().putHeader("Content-Type", "text/xml; charset=utf-8")
        ctx.response().statusCode = HttpResponseStatus.MULTI_STATUS.code()
        ctx.end(render.buildXML())
        return HttpResponseStatus.OK
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

    private const val infiniteDepth = -1
    private const val invalidDepth = -2

    private fun parseDepth(s: String): Int {
        return when (s) {
            "0" -> 0
            "1" -> infiniteDepth
            else -> invalidDepth
        }
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
        if (i>=0) {
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
            return false to 0
        }
        return true to timeout
    }

}
