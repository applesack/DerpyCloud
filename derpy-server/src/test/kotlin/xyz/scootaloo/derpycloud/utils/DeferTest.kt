package xyz.scootaloo.derpycloud.utils

import org.junit.jupiter.api.Test

/**
 * @author AppleSack
 * @since 2023/02/12
 */
class DeferTest {

    @Test
    fun testTryCatchNoThrow() {
        try {
            println(1)
        } catch (e: Throwable) {
            println(2)
        } finally {
            println(3)
        }
    }

    @Test
    fun testTryCatchThrow() {
        try {
            throw RuntimeException()
        } catch (e: Throwable) {
            throw e
        } finally {
            println(3)
        }
    }

}
