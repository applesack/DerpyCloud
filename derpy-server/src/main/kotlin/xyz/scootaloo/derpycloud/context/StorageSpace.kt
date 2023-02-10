package xyz.scootaloo.derpycloud.context

import io.vertx.core.file.FileSystem
import io.vertx.ext.web.handler.FileSystemAccess
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.kotlin.coroutines.await
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
    private var hasInit = false
    val staticResources: StaticHandler by lazy {
        StaticHandler.create(FileSystemAccess.ROOT, realPrefixString)
//            .setCachingEnabled(true)
//            .setEnableFSTuning(true)
//            .setFilesReadOnly(true)
//            .setCachingEnabled(true)
//            .setMaxCacheSize(512)
            .setEnableRangeSupport(true)
    }

    suspend fun initSpace(fs: FileSystem): StorageSpace {
        if (hasInit) {
            return this
        }

        if (!fs.exists(realPrefixString).await()) {
            val safe = runCatching { fs.mkdirs(realPrefixString).await() }
            if (safe.isFailure) {
                throw safe.exceptionOrNull()!!
            }
        }
        hasInit = true
        return this
    }
}
