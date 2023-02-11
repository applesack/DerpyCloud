package xyz.scootaloo.derpycloud.middleware

import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext

/**
 * @author AppleSack
 * @since 2023/02/11
 */
object CallInterceptHandler : Handler<RoutingContext> {
    override fun handle(event: RoutingContext) {
        event.next()
    }
}
