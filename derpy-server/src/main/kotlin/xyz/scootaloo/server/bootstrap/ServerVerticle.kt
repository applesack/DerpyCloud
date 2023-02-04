package xyz.scootaloo.server.bootstrap

import io.vertx.ext.web.Router
import io.vertx.kotlin.coroutines.CoroutineVerticle
import org.slf4j.LoggerFactory
import xyz.scootaloo.server.context.Contexts
import xyz.scootaloo.server.middleware.Middlewares
import xyz.scootaloo.server.router.Routers

/**
 * @author AppleSack
 * @since 2023/01/30
 */
object ServerVerticle : CoroutineVerticle() {

    private val log = LoggerFactory.getLogger(ServerVerticle::class.java)

    override suspend fun start() {
        Contexts.setup(vertx)

        val server = vertx.createHttpServer()
        val rootRouter = Router.router(vertx)
        Middlewares.setup(this, vertx, rootRouter)
        Routers.setup(rootRouter)
        server.exceptionHandler {
            log.error("路由错误", it)
        }
        server.requestHandler(rootRouter)
        server.listen(8080)
        log.info("服务启动")
    }

}
