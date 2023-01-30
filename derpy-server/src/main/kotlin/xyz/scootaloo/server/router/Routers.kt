package xyz.scootaloo.server.router

import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import kotlinx.coroutines.CoroutineScope

/**
 * @author AppleSack
 * @since 2023/01/30
 */
object Routers {

    fun doRoute(vertx: Vertx, coroutine: CoroutineScope): Router {
        return Router.router(vertx)
    }

}
