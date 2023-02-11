package xyz.scootaloo.derpycloud.utils

import org.junit.jupiter.api.Test

/**
 * @author AppleSack
 * @since 2023/02/11
 */
class ColorConsoleTest {

    @Test
    fun test() {
        val text = "${Char(31)}[31m helloworld" + "assa"
        println(text)
    }

}
