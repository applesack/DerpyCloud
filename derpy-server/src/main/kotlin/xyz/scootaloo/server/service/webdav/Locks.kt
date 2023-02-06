package xyz.scootaloo.server.service.webdav

import java.util.*
import kotlin.collections.HashMap

/**
 * @author AppleSack
 * @since  2023/02/01
 */

enum class Errors(val msg: String) {
    None("webdav: ok"),
    SkipDir("webdav: skip dir"),
    ConfirmationFailed("webdav: confirmation failed"),
    Forbidden("webdav: forbidden"),
    Locked("webdav: locked"),
    NoSuchLock("webdav: no such lock")
}

class LockDetails(
    val root: String,
    val ttl: Long,
    val ownerXML: String,
    val zeroDepth: Boolean
)

class LockInfo(
    val isShared: Boolean,
    val isWrite: Boolean,
    val owner: String
) {
    companion object {
        val NONE = LockInfo(isShared = false, isWrite = false, owner = "")
    }
}

object Locks {
    fun create(): LockSystem {
        return MemLockSystem(
            HashMap(), HashMap(), 1, TreeMap()
        )
    }
}

private class MemLSNode(
    val details: LockDetails,
    val token: String,
    var refCount: Int,
    val duration: Long,
    var held: Boolean
)

private class MemLockSystem(
    private val nameMap: Map<String, MemLSNode>,
    private val tokenMap: Map<String, MemLSNode>,
    private var gen: Long,
    private val expirationQueue: TreeMap<Long, MemLSNode>
) : LockSystem {

    override fun confirm(
        conditions: List<Condition>, name0: String, name1: String
    ): Pair<() -> Unit, Errors> {
        TODO()
    }

    override fun create(details: LockDetails): Pair<String, Errors> {
        TODO("Not yet implemented")
    }

    override fun refresh(token: String, ttl: Long): Pair<LockDetails, Errors> {
        TODO("Not yet implemented")
    }

    override fun unlock(token: String): Errors {
        TODO("Not yet implemented")
    }

}


