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

        router.route(HttpMethod.OPTIONS, "/*").handler {
            it.coroutineSafeCall { WebDAV.handleOptions(it) }
        }

        router.route(HttpMethod.PROPFIND, "/*").handler {
            it.coroutineSafeCall { WebDAV.handlePropFind(it) }
        }

        router.route(HttpMethod.PROPPATCH, "/*").handler {
            it.response().statusCode = HttpResponseStatus.METHOD_NOT_ALLOWED.code()
            it.end()
        }

        router.route(HttpMethod.MKCOL, "/*").handler {
            it.coroutineSafeCall { WebDAV.handleMkCol(it) }
        }

        router.route(HttpMethod.PUT, "/*").handler {
            it.coroutineSafeCall { WebDAV.handlePut(it) }
        }

        router.route(HttpMethod.COPY, "/*").handler {
            it.coroutineSafeCall { WebDAV.handleCopyMove(it) }
        }

        router.route(HttpMethod.MOVE, "/*").handler {
            it.coroutineSafeCall { WebDAV.handleCopyMove(it) }
        }

        router.route(HttpMethod.DELETE, "/*").handler {
            it.coroutineSafeCall { WebDAV.handleDelete(it) }
        }

        router.route(HttpMethod.LOCK, "/*").handler {
            it.coroutineSafeCall { WebDAV.handleLock(it) }
        }

        router.route(HttpMethod.UNLOCK, "/*").handler {
            it.coroutineSafeCall { WebDAV.handleUnlock(it) }
        }

        router.route(HttpMethod.GET, "/*").handler {
            WebDAV.handleGet(it)
        }

        return router
    }

    private fun RoutingContext.coroutineSafeCall(
        block: suspend CoroutineScope.() -> Unit
    ) {
        val ctx = this
        Middlewares.coroutine.launch {
            val safe = runCatching { block() }
            if (safe.isFailure) {
                log.error("webdav error", safe.exceptionOrNull())
                if (!ctx.response().ended()) {
                    ctx.response().statusCode = HttpResponseStatus.INTERNAL_SERVER_ERROR.code()
                    ctx.end()
                }
            }
        }
    }

}
