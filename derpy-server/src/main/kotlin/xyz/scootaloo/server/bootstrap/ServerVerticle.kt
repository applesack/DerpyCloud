package xyz.scootaloo.server.bootstrap

import io.vertx.kotlin.coroutines.CoroutineVerticle
import org.slf4j.LoggerFactory
import xyz.scootaloo.server.router.Routers

/**
 * @author AppleSack
 * @since 2023/01/30
 */
object ServerVerticle : CoroutineVerticle() {

    private val log = LoggerFactory.getLogger(ServerVerticle::class.java)

    override suspend fun start() {
        val server = vertx.createHttpServer()
        server.requestHandler(Routers.doRoute(vertx, this))
        server.listen(8080)
        log.info("服务启动")
    }

}
