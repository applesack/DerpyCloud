package xyz.scootaloo.server.middleware

import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import xyz.scootaloo.server.context.Contexts

/**
 * @author AppleSack
 * @since  2023/02/02
 */
object UserContextHandler : Handler<RoutingContext> {

    override fun handle(event: RoutingContext) {
        Contexts.getOrCreate(event)
        event.next()
    }

}
