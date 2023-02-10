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
    var duration: Long = 0, // 单位秒
    val owner: String = "",
    val zeroDepth: Boolean = false
) {
    companion object {
        val NONE = LockDetails("", 0, "", false)
    }
}

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
    var token: String = "",
    var refCount: Int = 0,
    var expiry: Long = 0,
    var held: Boolean = false
) {
    companion object {
        val NONE = MemLSNode(LockDetails.NONE, "", 0, 0L, false)
    }
}

private class MemLockSystem(
    private val nameMap: MutableMap<String, MemLSNode>,
    private val tokenMap: MutableMap<String, MemLSNode>,
    private var gen: Long,
    private val expiryQueue: TreeMap<Long, MemLSNode>
) : LockSystem {

    override fun confirm(
        conditions: List<Condition>, name0: String, name1: String
    ): Pair<() -> Unit, Errors> {
        val currentTimeMillis = System.currentTimeMillis()
        collectExpiryLocks(currentTimeMillis)

        var n0: MemLSNode = MemLSNode.NONE
        var n1: MemLSNode = MemLSNode.NONE
        if (name0.isNotEmpty()) {
            n0 = lookup(name0, conditions)
            if (n0 == MemLSNode.NONE) {
                return {} to Errors.ConfirmationFailed
            }
        }
        if (name1.isNotEmpty()) {
            n1 = lookup(name1, conditions)
            if (n1 == MemLSNode.NONE) {
                return {} to Errors.ConfirmationFailed
            }
        }
        if (n1 == n0) {
            n1 = MemLSNode.NONE
        }
        if (n0 != MemLSNode.NONE) {
            hold(n0)
        }
        if (n1 != MemLSNode.NONE) {
            hold(n1)
        }
        return {
            if (n0 != MemLSNode.NONE) {
                unHold(n0)
            }
            if (n1 != MemLSNode.NONE) {
                unHold(n1)
            }
        } to Errors.None
    }

    override fun create(details: LockDetails): Pair<String, Errors> {
        val currentTimeMillis = System.currentTimeMillis()
        collectExpiryLocks(currentTimeMillis)
        if (!canCreate(details.root, details.zeroDepth)) {
            return "" to Errors.Locked
        }
        val node = createLock(details.root).apply {
            token = nextToken()
            tokenMap[token] = this
        }
        if (node.details.duration >= 0) {
            node.expiry = newExpireKey(currentTimeMillis, node.details.duration)
            expiryQueue[node.expiry] = node
        }
        return node.token to Errors.None
    }

    override fun refresh(token: String, duration: Long): Pair<LockDetails, Errors> {
        val currentTimeMillis = System.currentTimeMillis()
        collectExpiryLocks(currentTimeMillis)
        val node = tokenMap[token] ?: return LockDetails.NONE to Errors.NoSuchLock
        if (node.expiry in expiryQueue) {
            expiryQueue.remove(node.expiry)
        }
        node.details.duration = duration
        if (node.details.duration >= 0) {
            node.expiry = newExpireKey(currentTimeMillis, duration)
            expiryQueue[node.expiry] = node
        }
        return node.details to Errors.None
    }

    override fun unlock(token: String): Errors {
        val currentTimeMillis = System.currentTimeMillis()
        collectExpiryLocks(currentTimeMillis)
        val node = tokenMap[token] ?: return Errors.NoSuchLock
        if (node.held) {
            return Errors.Locked
        }
        remove(node)
        return Errors.None
    }

    private fun hold(node: MemLSNode) {
        if (node.held) {
            throw RuntimeException("webdav: memLS inconsistent held state")
        }
        node.held = true
        if (node.details.duration >= 0 && node.expiry in  expiryQueue) {
            expiryQueue.remove(node.expiry)
        }
    }

    private fun unHold(node: MemLSNode) {
        if (!node.held) {
            throw RuntimeException("webdav: memLS inconsistent held state")
        }
        node.held = false
        if (node.details.duration >= 0) {
            expiryQueue[node.expiry] = node
        }
    }

    private fun lookup(name: String, conditions: List<Condition>): MemLSNode {
        for (cond in conditions) {
            val node = tokenMap[cond.token] ?: continue
            if (node.held) {
                continue
            }
            if (name == node.details.root) {
                return node
            }
            if (node.details.zeroDepth) {
                continue
            }
            if (node.details.root == "/" || name.startsWith(node.details.root + "/")) {
                return node
            }
        }
        return MemLSNode.NONE
    }

    private fun createLock(name: String): MemLSNode {
        var ret = MemLSNode.NONE
        walkToRoot(name) ret@{ name0, first ->
            var node = nameMap[name0]
            if (node == null) {
                node = MemLSNode(LockDetails(name0))
            }
            nameMap[name0] = node
            node.refCount++
            if (first) {
                ret = node
            }
            return@ret true
        }
        return ret
    }

    private fun remove(node: MemLSNode) {
        tokenMap.remove(node.token)
        node.token = ""
        walkToRoot(node.details.root) ret@{ name0, _ ->
            val x = nameMap[name0] ?: return@ret true
            x.refCount--
            if (x.refCount <= 0) {
                nameMap.remove(name0)
            }
            return@ret true
        }
        if (node.expiry >= 0) {
            expiryQueue.remove(node.expiry)
        }
    }

    private fun canCreate(name: String, zeroDepth: Boolean): Boolean {
        return walkToRoot(name) ret@{ name0, first ->
            val node = this.nameMap[name0] ?: return@ret true
            if (first) {
                if (node.token != "") {
                    return@ret false
                }
                if (zeroDepth) {
                    return@ret false
                }
            } else if (node.token != "" && !node.details.zeroDepth) {
                return@ret false
            }
            true
        }
    }

    private fun walkToRoot(name: String, wkFun: (String, Boolean) -> Boolean): Boolean {
        var name0 = name
        var first = true
        while (true) {
            if (!wkFun(name0, first)) {
                return false
            }
            if (name0 == "/") {
                break
            }
            name0 = name0.substring(0, name0.lastIndexOf('/'))
            if (name0 == "") {
                name0 = "/"
            }
            first = false
        }
        return true
    }

    private fun collectExpiryLocks(currentTimeMillis: Long) {
        val currentVirtualTime = currentTime(currentTimeMillis)
        val expiryKeys = LinkedList<Long>()
        for ((time, _) in expiryQueue) {
            if (time > currentVirtualTime) {
                break
            }
            expiryKeys.add(time)
        }
        for (key in expiryKeys) {
            expiryQueue.remove(key)
        }
    }

    private fun nextToken(): String {
        gen++
        return gen.toString()
    }

    private fun newExpireKey(currentTimeMillis: Long, duration: Long): Long {
        var target = currentTime(currentTimeMillis, duration * 1000)
        while (true) {
            target++
            if (target !in expiryQueue) {
                return target
            }
        }
    }

    private fun currentTime(currentTimeMillis: Long, increment: Long? = null): Long {
        increment ?: return currentTimeMillis * 100
        return (System.currentTimeMillis() + increment) * 100
    }
}


