package xyz.scootaloo.server.middleware

import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory

/**
 * @author AppleSack
 * @since  2023/02/01
 */
object ResponseRecordHandler : Handler<RoutingContext> {

    private const val RESP_RECORD_MARK = "resp:record"

    private val log = LoggerFactory.getLogger("http.recorder")

    override fun handle(event: RoutingContext) {
        event.put(RESP_RECORD_MARK, System.currentTimeMillis())
        event.addEndHandler {
            val info = buildString {
                append(remoteIpAddress(event))
                append(" ")
                append(requestPath(event))
                append(" | ")
                append(duration(event))
                append(" | status: ")
                append(event.response().statusCode)
            }
            log.info(info)
        }
        event.next()
    }

    private fun remoteIpAddress(ctx: RoutingContext): String {
        return ctx.request().remoteAddress().hostAddress()
    }

    private fun requestPath(ctx: RoutingContext): String {
        return ctx.request().uri()
    }

    private fun duration(ctx: RoutingContext): String {
        val currentTime = System.currentTimeMillis()
        val duration = currentTime - ctx.get<Long>(RESP_RECORD_MARK)
        return "$duration" + "ms"
    }

}
