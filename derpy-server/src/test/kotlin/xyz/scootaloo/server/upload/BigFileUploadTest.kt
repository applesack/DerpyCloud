package xyz.scootaloo.server.upload

import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.File

/**
 * @author AppleSack
 * @since  2023/01/31
 */
class BigFileUploadTest {

    @Test
    fun testUpload(): Unit = runBlocking {
        val vertx = Vertx.vertx()
        val server = vertx.createHttpServer()
        server.requestHandler(router(vertx))
        server.exceptionHandler {
            it.printStackTrace()
        }
        server.listen(8080).onComplete { done ->
            println(done)
        }
        delay(1000 * 60 * 30)
    }

    private fun router(vertx: Vertx): Router {
        return Router.router(vertx).apply {
            put("/*").handler(::saveFile)
        }
    }

    private fun saveFile(ctx: RoutingContext) {
        ctx.request().isExpectMultipart = true
        ctx.request().uploadHandler {
            println()
        }
//        ctx.request().body().compose { buff ->
//            val desktopDir = desktopDir("derpy")
//            val fs = ctx.vertx().fileSystem()
//            fs.exists(desktopDir).compose { exists ->
//                if (exists) {
//                    Future.succeededFuture<Unit>()
//                } else {
//                    fs.mkdir(desktopDir)
//                }
//            }.compose {
//                val newFileDest = desktopDir + File.pathSeparator + ctx.pathParam("*")
//                fs.writeFile(newFileDest, buff)
//            }.onComplete {
//                println(it)
//            }
//        }
    }

    private fun desktopDir(dir: String): String {
        return System.getProperty("user.home") + File.pathSeparator + "Desktop" + File.pathSeparator + dir
    }

}
