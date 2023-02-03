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
import xyz.scootaloo.server.service.file.UPaths
import java.io.File
import java.util.Collections

/**
 * @author AppleSack
 * @since  2023/02/01
 */
object WebDAV {

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
            return HttpResponseStatus.NOT_FOUND;
        }
        var depth = infiniteDepth
        val depthHeader = ctx.request().getHeader("Depth")
        if (depthHeader == null || depthHeader.isEmpty()) {
            depth = parseDepth(depthHeader)
        }
        val (success, pf) = Xml.readPropfind(ctx.body().asString())
        if (!success) {
            return HttpResponseStatus.BAD_REQUEST
        }

        val walkFun = fun(filename: String, info: FileInfo): Error {
            var pStats: MutableList<PropStat> = ArrayList()
            if (pf.propName) {
                val pStat = PropStat()
                for (name in propNames()) {
                    pStat.props.add(Property(name))
                }
                pStats.add(pStat)
            } else if (pf.allProp) {
               pStats = allProp(info, pf.props)
            } else {
                pStats = props(info, pf.props)
            }
            var href = UPaths.encodeUri(info.path)
            if (href != "/" && info.isDir) {
               href += "/"
            }
        }

        TODO()
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
                    val innerXML = render.render(fi)
                    pStatOk.props.add(Property(pName, innerXML))
                    process = true
                }
            }
            if (!process) {
                pStatNotFount.props.add(Property(pName))
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
        val copy = ArrayList<XName>()
        Collections.copy(copy, Props.livePropNames)
        return copy
    }

    private fun makePropStatResponse(href: String, pStats: List<PropStat>): MultiResponse {
        TODO()
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
