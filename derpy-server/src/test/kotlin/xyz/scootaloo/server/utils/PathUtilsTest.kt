package xyz.scootaloo.server.utils

import org.junit.jupiter.api.Test
import xyz.scootaloo.server.service.file.UPaths

/**
 * @author AppleSack
 * @since 2023/02/11
 */
class PathUtilsTest {

    @Test
    fun test() {
        println(UPaths.clean("/abc/def/../../ace"))
        println(UPaths.clean("/../a"))
        println(UPaths.clean("/"))
        println(UPaths.clean(""))
    }

}
