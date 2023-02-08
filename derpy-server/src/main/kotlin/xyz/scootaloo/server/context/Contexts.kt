package xyz.scootaloo.server.context

import io.vertx.core.Vertx
import io.vertx.ext.web.RoutingContext
import xyz.scootaloo.server.service.webdav.Locks

/**
 * @author AppleSack
 * @since  2023/02/01
 */
object Contexts {

    lateinit var vertx: Vertx

    private const val USER_SPACE = "user.space"

    const val USER_INFO = "user.info"

    const val GUEST_USER = "guest"

    private val usMap = HashMap<String, UserSpace>()

    fun launchCrontab() {
    }

    fun setup(vertx: Vertx) {
        this.vertx = vertx
    }

    fun getOrCreate(ctx: RoutingContext): UserSpace {
        val username = "a"
        val us = getOrCreateUserSpace(username)
        ctx.put(USER_SPACE, us)
        return us
    }

    private fun getUserSpace(ctx: RoutingContext): UserSpace {
        return ctx.get(USER_SPACE) as UserSpace
    }

    private fun getOrCreateUserSpace(user: String): UserSpace {
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
