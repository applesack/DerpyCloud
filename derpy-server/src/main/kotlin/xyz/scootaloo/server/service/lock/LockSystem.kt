package xyz.scootaloo.server.service.lock

/**
 * @author AppleSack
 * @since  2023/02/01
 */
interface LockSystem {

    fun confirm(names: List<String>, conditions: List<Locks.Condition>): Pair<LockRelease, Locks.Errors>

    fun create(details: Locks.LockDetails): Pair<String, Locks.Errors>

    fun refresh(token: String, ttl: Long): Pair<Locks.LockDetails, Locks.Errors>

    fun unlock(token: String): Locks.Errors

    interface LockRelease {

        fun release()

    }

}
