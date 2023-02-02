package xyz.scootaloo.server.service.lock

import java.util.*

/**
 * @author AppleSack
 * @since  2023/02/01
 */
object Locks {

    enum class Errors(val msg: String) {
        None("webdav: ok"),
        ConfirmationFailed("webdav: confirmation failed"),
        Forbidden("webdav: forbidden"),
        Locked("webdav: locked"),
        NoSuchLock("webdav: no such lock")
    }

    class Condition(
        val not: Boolean,
        val token: String,
        val etag: String
    )

    class LockDetails(
        val root: String,
        val ttl: Long,
        val ownerXML: String,
        val zeroDepth: Boolean
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

    private class MemLSNode(
        val details: LockDetails,
        val token: String,
        var refCount: Int,
        val duration: Long,
        var held: Boolean
    )

}
