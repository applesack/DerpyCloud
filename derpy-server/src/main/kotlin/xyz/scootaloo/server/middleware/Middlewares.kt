package xyz.scootaloo.server.middleware

import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.CoroutineScope

/**
 * @author AppleSack
 * @since 2023/01/31
 */
object Middlewares {

    private const val mark = "middleware"

    lateinit var coroutine: CoroutineScope

    lateinit var vertx: Vertx

    fun setup(coroutine: CoroutineScope, vertx: Vertx, root: Router) {
        this.vertx = vertx
        this.coroutine = coroutine

        root.route().handler(ResponseRecordHandler)
        root.route().handler(CorsHandler)
        root.route().handler(DigestAuthHandler)
        root.route().handler(JwtAuthHandler)
        root.route().handler(UserContextHandler)
    }

    fun mark(ctx: RoutingContext, name: String) {
        val exists = ctx.get<String>(mark) ?: ""
        ctx.put(mark, "$exists;$name")
    }

}
