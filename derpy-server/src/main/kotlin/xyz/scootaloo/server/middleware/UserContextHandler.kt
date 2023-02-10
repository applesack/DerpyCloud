package xyz.scootaloo.server.middleware

import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.launch
import xyz.scootaloo.server.context.Contexts

/**
 * @author AppleSack
 * @since  2023/02/02
 */
object UserContextHandler : Handler<RoutingContext> {

    private const val NAME = "context"

    override fun handle(event: RoutingContext) {
        Middlewares.coroutine.launch {
            Contexts.waitGetOrCreate(event)
            Middlewares.mark(event, NAME)
            event.next()
        }
    }

}
