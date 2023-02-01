package xyz.scootaloo.server.router

import io.vertx.ext.web.Router
import xyz.scootaloo.server.router.controller.Controllers

/**
 * @author AppleSack
 * @since 2023/01/30
 */
object Routers {

    fun setup(root: Router): Router {
        WebDAVRouters.mount(root)
        Controllers.mount(root)
        return root
    }

}
