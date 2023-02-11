package xyz.scootaloo.derpycloud.middleware

import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory

/**
 * @author AppleSack
 * @since  2023/02/01
 */
object ResponseRecordHandler : Handler<RoutingContext> {

    private const val RESP_RECORD_MARK = "resp.record"

    private val log = LoggerFactory.getLogger("http.recorder")

    override fun handle(event: RoutingContext) {
        event.put(RESP_RECORD_MARK, System.currentTimeMillis())
        event.addEndHandler {
            logInfo(event)
        }
        event.next()
    }

    private fun logInfo(ctx: RoutingContext) {
        val reqRmtAdr = ctx.request().remoteAddress().hostAddress()
        val reqUri = ctx.request().uri()
        val reqMethod = ctx.request().method()
        val status = ctx.response().statusCode
        log.info("| %s | %6s | %15s | %-8s | \"%s\""
            .format(status, duration(ctx), reqRmtAdr, reqMethod, reqUri))
    }

    private fun duration(ctx: RoutingContext): String {
        val currentTime = System.currentTimeMillis()
        val duration = currentTime - ctx.get<Long>(RESP_RECORD_MARK)
        if (duration < 2000) {
            // 1745ms
            return "$duration" + "ms"
        }
        // 12.34s
        return "%.2fs".format(duration.toFloat() / 1000f)
    }

}
