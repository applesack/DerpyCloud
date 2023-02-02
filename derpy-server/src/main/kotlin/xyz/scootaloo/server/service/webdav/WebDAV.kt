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
import java.io.File
import javax.naming.Name

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

        val walkFun = fun (filename: String, info: FileInfo): Error {
            if (pf.propName) {
                val pNames =
            }
        }

        TODO()
    }

    private fun propNames(fi: FileInfo): XName {
        val deedProps =
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
