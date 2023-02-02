package xyz.scootaloo.server.context

import java.nio.file.Paths
import kotlin.io.path.absolutePathString

/**
 * @author AppleSack
 * @since  2023/02/01
 */
class StorageSpace(
    val prefix: String
) {
    val realPrefixString by lazy { Paths.get(AppConfig.realPathString, prefix).absolutePathString() }
}
