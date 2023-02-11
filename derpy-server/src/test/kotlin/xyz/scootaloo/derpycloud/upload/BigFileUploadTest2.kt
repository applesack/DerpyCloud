package xyz.scootaloo.derpycloud.upload

import io.vertx.core.Vertx
import io.vertx.core.file.AsyncFile
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.file.openOptionsOf
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.File

/**
 * @author AppleSack
 * @since  2023/02/01
 */
class BigFileUploadTest2 : CoroutineVerticle() {

    @Test
    fun run(): Unit = runBlocking {
        val vertx = Vertx.vertx()
        vertx.deployVerticle(this@BigFileUploadTest2).await()
        delay(1000 * 60 * 30)
    }

    override suspend fun start() {
        val server = vertx.createHttpServer()
        server.requestHandler(router())
        server.exceptionHandler {
            it.printStackTrace()
        }
        server.invalidRequestHandler {
            println("invalid")
        }
        server.listen(8080).await()
        println("server ok")
    }

    private fun router(): Router {
        return Router.router(vertx).apply {
            // 要执行上传的路径上不得添加 body handler, 否则导致预先内存分配, 进而导致内存溢出
//            route().handler(BodyHandler.create()
//                .setHandleFileUploads(true)
//                .setBodyLimit(-1))
            put("/*").handler {
                // 在异步操作之前, 暂停请求, 在处理处理请求体时, 恢复请求
                it.request().pause()
                launch {
                    handleFileUpload(it)
                }
            }
        }
    }

    private suspend fun handleFileUpload(ctx: RoutingContext) {
        val dest = desktopAsyncFile(ctx.pathParam("*"))
        ctx.request().resume()
        ctx.request().pipeTo(dest).await()
        ctx.end("ok")
    }

    private suspend fun desktopAsyncFile(filename: String): AsyncFile {
        val dir = buildString {
            append(System.getProperty("user.home"))
            append(File.separator)
            append("Desktop")
            append(File.separator)
            append("derpy")
        }
        val fs = vertx.fileSystem()
        if (!fs.exists(dir).await()) {
            fs.mkdirs(dir).await()
        }
        val dest = dir + File.separator + filename
        println("file path : $dest")
        return fs.open(dest, openOptionsOf()).await()
    }

}
