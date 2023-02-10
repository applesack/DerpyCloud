package xyz.scootaloo.derpycloud.bootstrap

import io.vertx.core.Vertx
import xyz.scootaloo.derpycloud.context.AppConfig

/**
 * @author AppleSack
 * @since 2023/01/30
 */
object Launcher {

    fun run(args: Array<String>) {
        val rsl = runCatching {
            AppConfig.init()
            DbScript.init()
        }

        if (rsl.isFailure) {
            return report(rsl.exceptionOrNull()!!)
        }

        val vertx = Vertx.vertx()
        vertx.deployVerticle(MainVerticle)
    }

    private fun report(err: Throwable) {
    }

}
