package xyz.scootaloo.derpycloud.service.file

import io.vertx.core.http.impl.HttpUtils
import io.vertx.core.net.impl.URIDecoder
import xyz.scootaloo.derpycloud.context.StorageSpace
import xyz.scootaloo.derpycloud.service.webdav.WebDAV
import java.net.URLEncoder
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern
import kotlin.io.path.absolutePathString

/**
 * @author AppleSack
 * @since 2023/02/02
 */
object UPaths {

    fun slashClean(name: String): String {
        var out = name
        if (out == "" || out[0] != '/') {
            out = "/$out"
        }
        return clean(out)
    }

    fun clean(path: String): String {
        return HttpUtils.removeDots(path)
    }

    fun relative(storage: StorageSpace, cur: Path): String {
        return normalize(storage.realPrefixPath.relativize(cur).toString())
    }

    fun normalize(path: String): String {
        val size = if (path.startsWith('/')) path.length else path.length + 1
        val builder = StringBuilder(size)
        if (!path.startsWith('/')) {
            builder.append('/')
        }
        for (ch in path) {
            if (ch == '\\') {
                builder.append('/')
            } else {
                builder.append(ch)
            }
        }
        if (builder.length > 1 && builder.last() == '/') {
            builder.setLength(builder.length - 1)
        }
        return builder.toString()
    }

    fun filenameOf(path: String): String {
        val spIndex = path.lastIndexOf('/')
        if (spIndex < 0) {
            return ""
        }
        return path.substring(spIndex + 1)
    }

    fun realPath(storage: StorageSpace, path: String): String {
        return Paths.get(storage.realPrefixString, path).absolutePathString()
    }

    fun href(path: String): String {
        val prefix = WebDAV.prefix
        return encodeUri(join(prefix, path))
    }

    fun encodeUri(path: String): String {
        return URLEncoder.encode(path, "UTF-8")
            .replace("%2F", "/")
            .replace("+", "%20")
    }

    fun decodeUri(path: String): String {
        return slashClean(URIDecoder.decodeURIComponent(path))
    }

    fun join(vararg names: String): String {
        var size = 0
        for (name in names) {
            size += name.length
        }
        val buff = StringBuilder(size + names.size - 1)
        for (name in names) {
            if (name.isEmpty()) {
                continue
            }
            if (!name.startsWith('/')) {
                buff.append('/')
            }
            if (name.isNotEmpty() && name.endsWith('/')) {
                buff.append(name.trimEnd('/'))
            } else {
                buff.append(name)
            }
        }
        val path = buff.toString()
        if (path.isEmpty()) {
            return "/"
        }
        return clean(path)
    }

    private val regx: Pattern by lazy {
        Pattern.compile("((\\w+)://([^/:]+)(:\\d*)?)")
    }

    // suffix, ok
    @Deprecated("unused")
    fun pureUrlSuffix(url: String): Pair<String, Boolean> {
        val matcher = regx.matcher(url)
        if (!matcher.find()) {
            return slashClean(URIDecoder.decodeURIComponent(url)) to true
        }

        val matched = runCatching { matcher.group(0) }
        if (matched.isFailure) {
            return "" to false
        }
        val urlPrefix = matched.getOrThrow()
        return slashClean(url.substring(urlPrefix.length)) to true
    }

}
