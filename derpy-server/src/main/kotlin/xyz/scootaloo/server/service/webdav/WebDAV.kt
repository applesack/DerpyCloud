package xyz.scootaloo.server.service.webdav

import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.file.openOptionsOf
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.launch
import xyz.scootaloo.server.middleware.Middlewares
import java.io.File

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

}
