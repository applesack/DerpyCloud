package xyz.scootaloo.server.context

import io.vertx.core.Vertx
import io.vertx.ext.web.RoutingContext
import xyz.scootaloo.server.service.lock.Locks

/**
 * @author AppleSack
 * @since  2023/02/01
 */
object Contexts {

    lateinit var vertx: Vertx

    const val USER_SPACE = "user.space"

    const val USER_INFO = "user.info"

    const val GUEST_USER = "guest"

    private val usMap = HashMap<String, UserSpace>()

    fun launchCrontab() {
    }

    fun getUserSpace(ctx: RoutingContext): UserSpace {
        return ctx.get(USER_SPACE) as UserSpace
    }

    fun getUserSpace(user: String): UserSpace {
        return usMap[user] ?: createUserSpace(user)
    }

    fun getStorage(ctx: RoutingContext): StorageSpace {
        return getUserSpace(ctx).storageSpace
    }

    private fun createUserSpace(user: String): UserSpace {
        val store = StorageSpace("test")
        val ls = Locks.create()
        return UserSpace(store, ls).apply {
            usMap[user] = this
        }
    }

}
