package xyz.scootaloo.server.middleware

import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import kotlinx.coroutines.CoroutineScope

/**
 * @author AppleSack
 * @since 2023/01/31
 */
object Middlewares {

    lateinit var coroutine: CoroutineScope

    lateinit var vertx: Vertx

    fun setup(coroutine: CoroutineScope, vertx: Vertx, root: Router) {
        this.vertx = vertx
        this.coroutine = coroutine
    }

}
