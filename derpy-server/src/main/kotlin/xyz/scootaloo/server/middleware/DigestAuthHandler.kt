package xyz.scootaloo.server.middleware

import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext

/**
 * @author AppleSack
 * @since 2023/01/31
 */
object DigestAuthHandler : Handler<RoutingContext> {
    override fun handle(event: RoutingContext) {
    }
}
