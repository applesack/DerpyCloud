package xyz.scootaloo.server.upload

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.File

/**
 * @author AppleSack
 * @since 2023/01/31
 */
class BigFileUploadTest {

    @Test
    fun testUpload(): Unit = runBlocking {
        val vertx = Vertx.vertx()
        val server = vertx.createHttpServer()
        server.requestHandler(router(vertx))
        server.invalidRequestHandler {
            it.end()
        }
        server.exceptionHandler {
            it.printStackTrace()
        }
        server.listen(8080).onSuccess {
            println("server ok")
        }
        delay(1000 * 60 * 30)
    }

    private fun router(vertx: Vertx): Router {
        return Router.router(vertx).apply {
            // 注册 body handler 后, request.handler 不可用
            put("/*").handler {
                handleFile(it)
            }
        }
    }

    private fun handleFile(ctx: RoutingContext) {
        val uploadDir = desktopUploadDir()
        val fs = ctx.vertx().fileSystem()
        fs.exists(uploadDir)
            .compose { exists ->
                if (exists) {
                    Future.succeededFuture()
                } else {
                    fs.mkdirs(uploadDir)
                }
            }.compose {
                val destFile = uploadDest(uploadDir, ctx.pathParam("*"))
                fs.createFile(destFile).transform { done ->
                    if (done.succeeded()) Future.succeededFuture(destFile)
                    else Future.failedFuture(done.cause())
                }
            }.onComplete { done ->
                if (done.failed()) {
                    ctx.response().statusCode = 500
                    ctx.end()
                    return@onComplete
                }

                handleLargeFile(ctx, done.result())
            }
    }

    private fun handleLargeFile(ctx: RoutingContext, destFile: String) {
        val fs = ctx.vertx().fileSystem()
        ctx.request().handler {
            println("receive chunk " + it.length())
            fs.writeFile(destFile, it)
        }
    }

    private fun desktopUploadDir(): String {
        return buildString {
            append(System.clearProperty("user.home"))
            append(File.separator)
            append("Desktop")
            append(File.separator)
            append("derpy")
        }
    }

    private fun uploadDest(prefix: String, filename: String): String {
        return buildString {
            append(prefix)
            append(File.pathSeparator)
            append(filename)
        }
    }

}
