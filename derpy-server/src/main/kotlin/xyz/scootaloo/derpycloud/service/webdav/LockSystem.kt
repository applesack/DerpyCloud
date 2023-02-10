package xyz.scootaloo.derpycloud.service.webdav

import xyz.scootaloo.derpycloud.service.file.UPaths
import java.util.*
import kotlin.collections.HashMap

/**
 * @author AppleSack
 * @since  2023/02/01
 */

interface LockSystem {

    /**
     * **确认调用者是否可以用给定[conditions]条件声明[name0]指定的所有的锁，并且持有这些锁**;
     * 将对所有指定的资源的标记为独占访问. 最多可以指定两个资源. 如果指定的资源名称为空将被忽略;
     *
     * - 返回一个元组, 第一个元素是一个回调, 第二个元素是操作状态; 如果操作状态为[Errors.None],
     * 那么调用第一个元素将释放之前持有的锁; 如果操作状态为其他状态, 那么返回的回调无效, 并不会持有锁;
     * 在WebDAV的意义上, 调用释放不会解锁资源, 但是一旦确认声明操作成功, 在该锁被释放之前无法再次确认;
     *
     * - 如果[confirm]调用返回[Errors.ConfirmationFailed], 则处理程序将继续尝试使用其他的锁;
     * 如果抛出异常, 那么响应应该写入"500 Internal Server Error"状态码
     *
     * @param conditions 条件, http请求中的if-header
     * @param name0      资源名1
     * @param name1      资源名2
     */
    fun confirm(conditions: List<Condition>, name0: String, name1: String = ""): Pair<() -> Unit, Errors>

    /**
     * **使用提供的信息(深度, 持续时间, 所有者, 资源名)去创建一个锁**,
     * 其中锁的深度只有两个取值, 0 或者 infinite;
     *
     * - 如果该调用返回了[Errors.Locked], 响应应该写入"423 Locked"状态码,
     * 如果该调用抛出异常, 那么响应应该写入"500 Internal Server Error"状态码
     *
     * - 返回的token可以标识这个调用创建的锁, 这个锁的路径应该是一个绝对路径(路径格式在**RFC3986**中定义), 且不能包含空格
     *
     * 参考 http://www.webdav.org/specs/rfc4918.html#rfc.section.9.10.6
     *
     * @param details 创建锁所需要的信息
     */
    fun create(details: LockDetails): Pair<String, Errors>

    /**
     * **使用给定的token刷新锁**
     *
     * - 如果[refresh]调用返回了[Errors.Locked], 响应应该写入"423 Locked"状态码;
     * 如果调用返回了[Errors.NoSuchLock], 响应应该写入"412 Precondition Failed"状态码;
     * 如果该调用抛出异常, 那么响应应该写入"500 Internal Server Error"状态码
     *
     * 参考 http://www.webdav.org/specs/rfc4918.html#rfc.section.9.10.6
     */
    fun refresh(token: String, duration: Long): Pair<LockDetails, Errors>

    /**
     * **使用指定的token解锁**
     *
     * - 如果[unlock]调用返回[Errors.Forbidden], 响应应该写入"403 Forbidden"状态码;
     * 如果调用返回[Errors.Locked], 响应应该写入"423 Locked"状态码;
     * 如果调用返回[Errors.ConfirmationFailed], 响应应该写入"409 Conflict"状态码;
     * 如果该调用抛出异常, 那么响应应该写入"500 Internal Server Error"状态码
     *
     * 参考 http://www.webdav.org/specs/rfc4918.html#rfc.section.9.11.1
     */
    fun unlock(token: String): Errors

}

enum class Errors(val msg: String) {
    None("webdav: ok"),
    SkipDir("webdav: skip dir"),
    ConfirmationFailed("webdav: confirmation failed"),
    Forbidden("webdav: forbidden"),
    Locked("webdav: locked"),
    NoSuchLock("webdav: no such lock")
}

class LockDetails(
    var root: String,
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
            n0 = lookup(UPaths.slashClean(name0), conditions)
            if (n0 == MemLSNode.NONE) {
                return {} to Errors.ConfirmationFailed
            }
        }
        if (name1.isNotEmpty()) {
            n1 = lookup(UPaths.slashClean(name1), conditions)
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
        details.root = UPaths.slashClean(details.root)
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
        if (node.details.duration >= 0 && node.expiry in expiryQueue) {
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
