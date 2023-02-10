package xyz.scootaloo.server.service.user

/**
 * @author AppleSack
 * @since  2023/02/10
 */
object UserService {

    private val username2plaintext = HashMap<String, String>()

    init {
        username2plaintext["test"] = "123456"
    }

    fun getPlaintextPassByUsername(username: String): String? {
        return username2plaintext[username]
    }

    fun getUserId(name: String): Long {
        return 1
    }

    fun getDefaultUserId(): Long {
        return 0
    }

}
