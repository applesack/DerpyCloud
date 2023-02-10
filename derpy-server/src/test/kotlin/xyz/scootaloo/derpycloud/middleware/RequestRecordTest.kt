package xyz.scootaloo.derpycloud.middleware

import io.vertx.ext.web.Router
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import xyz.scootaloo.derpycloud.utils.HttpServerLauncher

/**
 * @author AppleSack
 * @since  2023/02/01
 */
class RequestRecordTest {

    @Test
    fun testRecord(): Unit = runBlocking {
        HttpServerLauncher.launch { _, router ->
            router.route().handler(ResponseRecordHandler)
            router.route().subRouter(record(this))
        }.await()
        delay(1000 * 60 * 30)
    }

    private fun record(coroutine: CoroutineScope): Router {
        val router = Router.router(HttpServerLauncher.vertx)
        router.get("/*").handler {
            it.response().sendFile("build.gradle.kts")
        }

        router.put("/*").handler {
            coroutine.launch {
                delay(100)
                it.end("ok")
            }
        }
        return router
    }

}
