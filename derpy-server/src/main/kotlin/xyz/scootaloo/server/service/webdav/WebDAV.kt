package xyz.scootaloo.server.service.webdav

import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.file.openOptionsOf
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.launch
import xyz.scootaloo.server.context.Contexts
import xyz.scootaloo.server.middleware.Middlewares
import xyz.scootaloo.server.service.file.FileInfo
import xyz.scootaloo.server.service.file.UFiles
import xyz.scootaloo.server.service.lock.Errors
import java.io.File

/**
 * @author AppleSack
 * @since  2023/02/01
 */
object WebDAV {

    const val prefix = "/dav"

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
        val (success, pf) = XmlParser.readPropfind(ctx.body().asString() ?: "")
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

}
