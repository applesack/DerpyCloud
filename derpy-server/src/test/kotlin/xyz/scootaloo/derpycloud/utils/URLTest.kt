package xyz.scootaloo.derpycloud.utils

import org.junit.jupiter.api.Test
import xyz.scootaloo.derpycloud.service.file.UPaths
import java.net.URL

/**
 * @author AppleSack
 * @since 2023/02/11
 */
class URLTest {

    @Test
    fun testUrl() {
        val url = URL("http://www.runoob.com/index.html?language=cn#j2se")
        println(url.host)
    }

    @Test
    fun test1() {
        val url = URL("")
        println(url.host)
    }

    @Test
    fun test2() {
        println(UPaths.pureUrlSuffix("http://www.runoob.com/index.html"))
        println(UPaths.pureUrlSuffix("http://www.runoob.com/index/abc"))
    }

}
