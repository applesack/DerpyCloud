package xyz.scootaloo.server.bootstrap

import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import org.slf4j.LoggerFactory

/**
 * @author AppleSack
 * @since 2023/01/30
 */
object MainVerticle : CoroutineVerticle() {

    private val log = LoggerFactory.getLogger("main")

    override suspend fun start() {
        val rsl = runCatching {
            vertx.deployVerticle(ServerVerticle).await()
        }
        if (rsl.isFailure) {
            log.error("启动错误", rsl.exceptionOrNull())
        }
    }

}
