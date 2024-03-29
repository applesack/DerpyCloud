package xyz.scootaloo.derpycloud.middleware

import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.Handler
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import xyz.scootaloo.derpycloud.context.Contexts
import xyz.scootaloo.derpycloud.service.user.UserService
import xyz.scootaloo.derpycloud.service.webdav.WebDAV
import java.lang.System.currentTimeMillis
import java.security.MessageDigest
import java.util.*

/**
 * @author AppleSack
 * @since 2023/01/31
 */
object DigestAuthHandler : Handler<RoutingContext> {

    private val log = LoggerFactory.getLogger("digest")
    private const val NAME = "digest"

    override fun handle(event: RoutingContext) {
        if (!event.request().uri().startsWith(WebDAV.prefix)) {
            return event.next()
        }
        if (event.request().method() == HttpMethod.OPTIONS) {
            return event.next()
        }
        handleAuth(event)
    }

    private fun handleAuth(ctx: RoutingContext) {
        val headers = ctx.request().headers()
        val authorization = headers[Term.H_AUTHORIZATION] ?: return sendChallenge(ctx)
        val authHeader = Utils.parseAuthHeader(ctx, authorization) ?: return sendChallenge(ctx)
        authentication(ctx, authHeader)
    }

    private fun authentication(ctx: RoutingContext, header: AuthorizationHeader) {
        val (valid, expired) = Utils.validateNonce(header.nonce)
        if (!valid) {
            return sendChallenge(ctx)
        }
        if (expired) {
            return sendChallenge(ctx, true)
        }
        val password = UserService.getPlaintextPassByUsername(header.username) ?: ""
        if (password.isEmpty()) {
            return sendChallenge(ctx)
        }
        val computedResponse = Utils.computedResponse(header, password)
        if (computedResponse == header.response) {
            authorization(ctx, header, password)
        } else {
            sendChallenge(ctx)
        }
    }

    // 授权
    private fun authorization(ctx: RoutingContext, header: AuthorizationHeader, password: String) {
        val headers = ctx.response().headers()
        headers[Term.H_AUTHENTICATION_INFO] = Utils.authorizationInfo(header, password)
        // 仅通过账号密码来确定登陆用户, 不记录用户的权限信息
        // 如果有, 则将获取用户权限的逻辑写在这里
        ctx.put(Contexts.USER_NAME, header.username)
        ctx.put(Contexts.USER_ID, UserService.getUserId(header.username).toString())
        Middlewares.mark(ctx, NAME)
        ctx.next()
    }

    private fun sendChallenge(ctx: RoutingContext, stale: Boolean = false) {
        val response = ctx.response()
        response.putHeader(Term.H_AUTHENTICATE, Utils.buildChallenge(stale))
        response.statusCode = HttpResponseStatus.UNAUTHORIZED.code()
        response.end()
    }

    private class AuthorizationHeader(
        val username: String,
        val realm: String,
        val method: String,
        val uri: String,
        val nonce: String,
        val nonceCounter: String,
        val clientNonce: String,
        val qop: String,
        val response: String
    )

    private object Term {
        const val H_AUTHENTICATE = "WWW-Authenticate" // 质询
        const val H_AUTHORIZATION = "Authorization"   // 响应
        const val H_AUTHENTICATION_INFO = "Authentication-Info"
        const val DIGEST_PREFIX = "Digest"
        const val C_USER = "user"
        const val C_USERNAME = "username"
        const val C_QOP = "qop"
        const val C_RSP_AUTH = "rspauth"
        const val C_CLIENT_NONCE = "cnonce"
        const val C_RESPONSE = "response"
        const val C_NONCE_COUNTER = "nc"
        const val C_NONCE = "nonce"
        const val C_URI = "uri"
        const val C_REALM = "realm"
        const val C_STALE = "stale"
        const val DEF_QOP = "auth"
        const val MAX_NONCE_AGE_SECONDS = 20
    }

    private object Utils {

        const val defRealm = "fly me to the moon"

        fun validateNonce(nonce: String): Pair<Boolean, Boolean> = try {
            val plainNonce = Encoder.base64decode(nonce).trim('\"')
            val timestamp = plainNonce.substring(0, plainNonce.indexOf(' '))
            if (nonce == newNonce(timestamp)) {
                if (currentTimeMillis() - timestamp.toLong() > (Term.MAX_NONCE_AGE_SECONDS * 1000)) {
                    true to true
                } else {
                    true to false
                }
            } else {
                false to false
            }
        } catch (e: Throwable) {
            false to false
        }

        fun buildChallenge(stale: Boolean = false): String {
            val parts = LinkedList<Triple<String, String, Boolean>>()
            parts.add(Triple(Term.C_REALM, defRealm, true))
            parts.add(Triple(Term.C_QOP, Term.DEF_QOP, true))
            parts.add(Triple(Term.C_NONCE, newNonce(), true))
            if (stale) {
                parts.add(Triple(Term.C_STALE, "true", false))
            }
            return "${Term.DIGEST_PREFIX} ${format(parts)}"
        }

        fun authorizationInfo(header: AuthorizationHeader, password: String): String {
            return format(
                listOf(
                    Triple(Term.C_QOP, Term.DEF_QOP, true),
                    Triple(Term.C_RSP_AUTH, rspAuth(header, password), true),
                    Triple(Term.C_CLIENT_NONCE, header.clientNonce, true),
                    Triple(Term.C_NONCE_COUNTER, header.nonceCounter, false)
                )
            )
        }

        fun computedResponse(header: AuthorizationHeader, password: String): String {
            val a1hash = header.run { md5("$username:$realm:$password") }
            val a2hash = header.run { md5("$method:$uri") }
            return header.run {
                md5("$a1hash:$nonce:$nonceCounter:$clientNonce:$qop:$a2hash")
            }
        }

        fun parseAuthHeader(
            ctx: RoutingContext, authentication: String
        ): AuthorizationHeader? {
            if (authentication.startsWith(Term.DIGEST_PREFIX)) {
                val rest = authentication.substring(Term.DIGEST_PREFIX.length + 1)
                    .replace("\"", "")
                val method = methodOf(ctx.request())
                val result = parseAuthHeaderCore(rest, method)
                if (result.isFailure) {
                    log.warn("摘要加密服务警告: 不能解析的权限信息, 格式可能存在错误 -> $authentication")
                    return null
                }
                return result.getOrThrow()
            }
            return null
        }

        private fun rspAuth(header: AuthorizationHeader, password: String): String {
            val a1Hash = header.run { md5("$username:$realm:$password") }
            val a2Hash = header.run { md5(":$uri") }
            return header.run {
                md5("$a1Hash:$nonce:$nonceCounter:$clientNonce:$qop:$a2Hash")
            }
        }

        private fun parseAuthHeaderCore(
            authorization: String, method: String
        ): Result<AuthorizationHeader> {
            val params = HashMap<String, String>()
            for (item in authorization.split(',')) {
                val idx = item.indexOf('=')
                if (idx == -1)
                    continue
                val key = item.substring(0, idx).trim()
                val value = item.substring(idx + 1)
                params[key] = value
            }
            return runCatching {
                AuthorizationHeader(
                    username = params[Term.C_USER]
                        ?: params[Term.C_USERNAME]!!,
                    realm = params[Term.C_REALM]!!,
                    method = method,
                    nonce = params[Term.C_NONCE]!!,
                    uri = params[Term.C_URI]!!,
                    nonceCounter = params[Term.C_NONCE_COUNTER]!!,
                    clientNonce = params[Term.C_CLIENT_NONCE]!!,
                    response = params[Term.C_RESPONSE]!!,
                    qop = params[Term.C_QOP]!!
                )
            }
        }

        private const val privateKey = "fly-me-to-the-moon"

        private fun newNonce(timestamp: String = currentTimeMillis().toString()): String {
            val secret = md5("$timestamp:${privateKey}")
            return Encoder.base64encode("\"$timestamp $secret\"")
        }

        private fun format(meta: List<Triple<String, String, Boolean>>): String {
            return meta.joinToString(",") {
                if (it.third) {
                    "${it.first}=\"${it.second}\""
                } else {
                    "${it.first}=${it.second}"
                }
            }
        }

        private fun md5(content: String): String {
            return Encoder.md5(content)
        }

        private fun methodOf(request: HttpServerRequest): String {
            return request.method().toString()
        }
    }

    private object Encoder {
        private val md5 = MessageDigest.getInstance("MD5")
        private const val HEX = "0123456789abcdef"

        private val base64encoder = Base64.getEncoder()
        private val base64decoder = Base64.getDecoder()

        fun md5(content: String): String {
            val bytes = md5.digest(content.toByteArray())
            return bytes2hex(bytes)
        }

        fun base64encode(content: String): String {
            return base64encoder.encodeToString(content.encodeToByteArray())
        }

        fun base64decode(encoded: String): String {
            return String(base64decoder.decode(encoded))
        }

        private fun bytes2hex(bytes: ByteArray): String {
            val builder = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                builder.append(HEX[b.toInt() shr 4 and 0x0f])
                builder.append(HEX[b.toInt() and 0x0f])
            }
            return builder.toString()
        }
    }
}
