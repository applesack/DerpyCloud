package xyz.scootaloo.server.webdav

import org.junit.jupiter.api.Test
import java.util.PriorityQueue

/**
 * @author AppleSack
 * @since  2023/02/10
 */
class LockRecyclerTest {

    @Test
    fun test() {
        val que = PriorityQueue<Long>()
        que.add(10)
        que.add(8)
        que.add(7)
        que.add(8)
        que.add(7)
        que.add(15)
        for (i in 0 until 1) {
            que.remove()
        }
        println(que)
    }

    @Test
    fun testTime() {
        val cur = System.currentTimeMillis()
        println(cur)
    }

}
