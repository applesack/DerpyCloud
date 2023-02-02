package xyz.scootaloo.server.service.file

import xyz.scootaloo.server.context.AppConfig
import java.net.URLEncoder
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

/**
 * @author AppleSack
 * @since 2023/02/02
 */
object UPaths {

    fun clean(path: String): String {
        // todo 归一化, 去除路径上的 ./和../
        return path
    }

    fun realPath(prefix: String, path: String): String {
        return Paths.get(prefix, path).absolutePathString()
    }

    fun encodeUri(path: String): String {
        return URLEncoder.encode(path, "UTF-8")
            .replace("%2F", "/")
            .replace("+", "%20")
    }

}
