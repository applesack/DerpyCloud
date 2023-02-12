package xyz.scootaloo.derpycloud.service.utils

/**
 * @author AppleSack
 * @since 2023/02/11
 */

object Defer {

    suspend fun <R> run(block: suspend DeferRunner<R>.() -> R): R {
        return DeferRunner<R>().run(block)
    }

}

class DeferRunner<R> {

    private val callbacks = ArrayList<suspend () -> Unit>(4)

    fun defer(callback: suspend () -> Unit) {
        callbacks.add(callback)
    }

    suspend fun run(block: suspend DeferRunner<R>.() -> R): R {
        return try {
            block()
        } catch (e: Throwable) {
            throw e
        } finally {
            callbacks.forEach { it() }
        }
    }

}
