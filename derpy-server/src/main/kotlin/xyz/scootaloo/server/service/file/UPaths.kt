package xyz.scootaloo.server.service.file

import xyz.scootaloo.server.context.AppConfig
import xyz.scootaloo.server.context.StorageSpace
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

    fun realPath(storage: StorageSpace, path: String): String {
        return Paths.get(storage.realPrefixString, path).absolutePathString()
    }

    fun encodeUri(path: String): String {
        return URLEncoder.encode(path, "UTF-8")
            .replace("%2F", "/")
            .replace("+", "%20")
    }

    fun join(vararg names: String): String {
        var size = 0
        for (name in names) {
            size += name.length
        }
        if (size == 0) {
            return ""
        }
        val buff = StringBuilder(size + names.size - 1)
        for (name in names) {
            if (buff.isNotEmpty() || name != "") {
                buff.append("/")
            }
            buff.append(name)
        }
        return clean(buff.toString())
    }

}
