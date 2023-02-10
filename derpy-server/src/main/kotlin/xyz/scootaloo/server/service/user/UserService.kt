package xyz.scootaloo.server.service.user

/**
 * @author AppleSack
 * @since  2023/02/10
 */

enum class UserLoginState {
    OK, MISTAKE, NOT_EXISTS
}

object UserService {

    private val username2plaintext = HashMap<String, String>()

    fun loginWithPlaintext(username: String, password: String): UserLoginState {
        val pass = username2plaintext[username] ?: return UserLoginState.NOT_EXISTS
        if (pass != password) {
            return UserLoginState.MISTAKE
        }
        return UserLoginState.OK
    }

}
