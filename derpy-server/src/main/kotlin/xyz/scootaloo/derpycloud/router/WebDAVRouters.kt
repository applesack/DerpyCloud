package xyz.scootaloo.derpycloud.router

import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import xyz.scootaloo.derpycloud.middleware.Middlewares
import xyz.scootaloo.derpycloud.service.webdav.WebDAV

/**
 * @author AppleSack
 * @since  2023/02/01
 */
object WebDAVRouters {

    private val log by lazy { LoggerFactory.getLogger("webdav") }

    fun mount(superior: Router) {
        superior.route(WebDAV.prefix + "*").subRouter(davRouter())
    }

    private fun davRouter(): Router {
        val router = Router.router(Middlewares.vertx)

        router.route("/*").failureHandler {
            if (!it.response().ended()) {
                it.response().statusCode = it.statusCode()
                it.end(HttpResponseStatus.valueOf(it.statusCode()).reasonPhrase())
            }
            val fail = it.failure() ?: return@failureHandler
            log.error("webdav error", fail)
        }

        router.route(HttpMethod.OPTIONS, "/*").handler {
            it.coroutineSafeCall { WebDAV.options(it) }
        }

        router.route(HttpMethod.PROPFIND, "/*").handler {
            it.coroutineSafeCall { WebDAV.propfind(it) }
        }

        router.route(HttpMethod.PROPPATCH, "/*").handler {

        }

        router.route(HttpMethod.PUT, "/*").handler {
            WebDAV.put(it)
        }

        router.route(HttpMethod.COPY, "/*").handler {

        }

        router.route(HttpMethod.MOVE, "/*").handler {

        }

        router.route(HttpMethod.DELETE, "/*").handler {

        }

        router.route(HttpMethod.LOCK, "/*").handler {
            it.coroutineSafeCall { WebDAV.lock(it) }
        }

        router.route(HttpMethod.UNLOCK, "/*").handler {
            it.coroutineSafeCall { WebDAV.unlock(it) }
        }

        router.route(HttpMethod.GET, "/*").handler {
            WebDAV.get(it)
        }

        return router
    }

    private val serverInternalError = HttpResponseStatus.INTERNAL_SERVER_ERROR

    private fun RoutingContext.coroutineSafeCall(
        block: suspend CoroutineScope.() -> HttpResponseStatus
    ) {
        val ctx = this
        Middlewares.coroutine.launch {
            val result = runCatching { block() }
            if (result.isFailure) {
                return@launch ctx.fail(serverInternalError.code())
            }

            val rCode = result.getOrThrow().code()
            if (rCode >= 300) {
                return@launch ctx.fail(rCode)
            }
            if (!ctx.response().ended()) {
                ctx.end()
            }
        }
    }

}