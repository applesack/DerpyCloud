package xyz.scootaloo.derpycloud.context

import io.vertx.core.Vertx
import io.vertx.ext.web.RoutingContext
import xyz.scootaloo.derpycloud.service.user.UserService
import xyz.scootaloo.derpycloud.service.webdav.Locks

/**
 * @author AppleSack
 * @since  2023/02/01
 */
object Contexts {

    lateinit var vertx: Vertx

    private const val USER_SPACE = "user.space"

    const val USER_NAME = "user.name"

    const val USER_ID = "user.id"

    const val GUEST_USER = "guest"

    private val usMap = HashMap<String, UserSpace>()

    fun launchCrontab() {
    }

    fun setup(vertx: Vertx) {
        this.vertx = vertx
    }

    suspend fun waitGetOrCreate(ctx: RoutingContext): UserSpace {
        val userId = ctx.get<String>(USER_ID) ?: UserService.getDefaultUserId().toString()
        val us = getOrCreateUserSpace(userId)
        ctx.put(USER_SPACE, us)
        return us
    }

    fun get(ctx: RoutingContext): UserSpace {
        return getUserSpace(ctx)
    }

    fun getStorage(ctx: RoutingContext): StorageSpace {
        return getUserSpace(ctx).storageSpace
    }

    private fun getUserSpace(ctx: RoutingContext): UserSpace {
        return ctx.get(USER_SPACE) as UserSpace
    }

    private suspend fun getOrCreateUserSpace(userId: String): UserSpace {
        return usMap[userId] ?: createUserSpace(userId)
    }

    private suspend fun createUserSpace(userId: String): UserSpace {
        val store = StorageSpace(userId).initSpace(vertx.fileSystem())
        val ls = Locks.create()
        return UserSpace(store, ls).apply {
            usMap[userId] = this
        }
    }

}
