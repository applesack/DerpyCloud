package xyz.scootaloo.server.webdav

import org.junit.jupiter.api.Test
import xyz.scootaloo.server.service.webdav.parseIfHeader

/**
 * @author AppleSack
 * @since 2023/02/05
 */
class IfHeaderTest {

    @Test
    fun testTokenType() {
        val char1 = '('
        println(char1)
        println('('.code)
    }

    @Test
    fun test1() {
        val r = parseIfHeader(
            """(<urn:uuid:181d4fae-7d8c-11d0-a765-00a0c91e6bf2>["I am an ETag"])(["I am another ETag"])"""
        )
        println(r)
    }

    @Test
    fun test2() {
        val r = parseIfHeader("""
            (Not <urn:uuid:181d4fae-7d8c-11d0-a765-00a0c91e6bf2><urn:uuid:58f202ac-22cf-11d1-b12d-002035b29092>)
        """.trimIndent())
        println(r)
    }

    @Test
    fun test3() {
        val r = parseIfHeader("""
            </resource1>(<urn:uuid:181d4fae-7d8c-11d0-a765-00a0c91e6bf2>[W/"A weak ETag"]) (["strong ETag"])
        """.trimIndent())
        println(r)
    }

}
