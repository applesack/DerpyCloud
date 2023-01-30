package xyz.scootaloo.server.bootstrap

import io.vertx.kotlin.coroutines.CoroutineVerticle

/**
 * @author AppleSack
 * @since 2023/01/30
 */
object MainVerticle : CoroutineVerticle() {

    override suspend fun start() {
        vertx.deployVerticle(ServerVerticle)
    }

}
