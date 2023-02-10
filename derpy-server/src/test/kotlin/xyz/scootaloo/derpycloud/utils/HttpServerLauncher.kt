package xyz.scootaloo.derpycloud.utils

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.ext.web.Router
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory

/**
 * @author AppleSack
 * @since  2023/02/01
 */
object HttpServerLauncher : CoroutineVerticle() {

    private val log = LoggerFactory.getLogger("http.verticle")

    private lateinit var callback: CoroutineScope.(HttpServer, Router) -> Unit

    fun launch(callback: CoroutineScope.(HttpServer, Router) -> Unit): Future<String> {
        this.callback = callback
        val vertx = Vertx.vertx()
        return vertx.deployVerticle(this)
    }

    override suspend fun start() {
        val server = vertx.createHttpServer()
        val router = Router.router(vertx)
        callback(server, router)
        server.requestHandler(router)
        server.exceptionHandler {
            it.printStackTrace()
        }
        server.listen(8080).await()
        log.info("server ok " + server.actualPort())
    }

}
