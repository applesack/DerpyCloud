package xyz.scootaloo.server.context

import io.vertx.ext.web.handler.FileSystemAccess
import io.vertx.ext.web.handler.StaticHandler
import java.nio.file.Paths
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString

/**
 * @author AppleSack
 * @since  2023/02/01
 */
class StorageSpace(
    private val prefix: String
) {
    val realPrefixPath by lazy { Paths.get(AppConfig.realPathString, prefix).absolute() }
    val realPrefixString by lazy { realPrefixPath.absolutePathString() }
    val staticResources: StaticHandler by lazy { StaticHandler.create(FileSystemAccess.ROOT, realPrefixString)
        .setCachingEnabled(true)
        .setEnableFSTuning(true)
        .setFilesReadOnly(true)
        .setCachingEnabled(true)
        .setMaxCacheSize(512)
        .setEnableRangeSupport(true)
    }
}
