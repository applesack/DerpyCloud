package xyz.scootaloo.server.router

import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Router
import xyz.scootaloo.server.middleware.Middlewares
import xyz.scootaloo.server.service.webdav.WebDAV

/**
 * @author AppleSack
 * @since  2023/02/01
 */
object WebDAVRouters {

    fun mount(superior: Router) {
        superior.route(WebDAV.prefix + "*").subRouter(davRouter())
    }

    private fun davRouter(): Router {
        val router = Router.router(Middlewares.vertx)

        router.route(HttpMethod.OPTIONS, "/*").handler {

        }

        router.route(HttpMethod.GET, "/*").handler {

        }

        router.route(HttpMethod.PROPFIND, "/*").handler {

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

        }

        router.route(HttpMethod.UNLOCK, "/*").handler {

        }

        return router
    }

}
