package xyz.scootaloo.server.service.file

import java.net.URLEncoder

/**
 * @author AppleSack
 * @since 2023/02/02
 */
object UPaths {

    fun clean(path: String): String {
        // todo 归一化, 去除路径上的 ./和../
        return path
    }

    fun encodeUri(path: String): String {
        return URLEncoder.encode(path, "UTF-8")
            .replace("%2F", "/")
            .replace("+", "%20")
    }

}
