package xyz.scootaloo.server.service.webdav

import io.vertx.core.http.impl.MimeMapping
import io.vertx.ext.web.impl.Utils
import xyz.scootaloo.server.service.file.FileInfo
import xyz.scootaloo.server.service.file.UPaths
import xyz.scootaloo.server.service.lock.LockSystem
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * @author AppleSack
 * @since  2023/02/01
 */
data class PropFind(
    var allProp: Boolean = false,
    var propName: Boolean = false,
    val props: MutableList<XName> = ArrayList(),
    val include: MutableList<XName> = ArrayList()
)

internal class PropPatch(
    val remove: Boolean
)

data class XName(
    val space: String,
    val local: String
)

fun interface PropRender {
    fun render(fi: FileInfo): String
}

val liveProps: Map<XName, Pair<Boolean, PropRender>> by lazy {
    val map = HashMap<XName, Pair<Boolean, PropRender>>()
    map[XName("DAV:", "resourcetype")] = true to PropRender { findResourceType(it) }
    map[XName("DAV:", "displayname")] = true to PropRender { findDisplayName(it) }
    map[XName("DAV:", "getcontentlength")] = false to PropRender { it.size.toString() }
    map[XName("DAV:", "getlastmodified")] = true to PropRender { findLastModified(it) }
    map[XName("DAV:", "creationdate")] = false to PropRender { findCreationDate(it) }
    map[XName("DAV:", "getcontenttype")] = false to PropRender { findContentType(it) }
    map[XName("DAV:", "getetag")] = false to PropRender { findETag(it) }
    map[XName("DAV:", "lockdiscovery")]
    // todo
    map
}

private fun findEmpty(fi: FileInfo): String {
    return ""
}

private fun findResourceType(fi: FileInfo): String {
    if (fi.isDir) {
        return """<D:collection xmlns:D="DAV:"/>"""
    }
    return ""
}

private fun findDisplayName(fi: FileInfo): String {
    // todo escapeXML
    return UPaths.encodeUri(fi.path)
}

private fun findLastModified(fi: FileInfo): String {
    return Utils.formatRFC1123DateTime(fi.modTime)
}

private val rfc3339format by lazy { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ") }

private fun findCreationDate(fi: FileInfo): String {
    return rfc3339format.format(Date(fi.creationDate))
}

private fun findContentType(fi: FileInfo): String {
    return MimeMapping.getMimeTypeForFilename(fi.name) ?: MimeMapping.getMimeTypeForFilename("bin")
}

private fun findETag(fi: FileInfo): String {
    return String.format("\"%x%x\"", fi.modTime, fi.size)
}

private fun findSupportedLock(): String {
    return "" +
            "<D:lockentry xmlns:D=\"DAV:\">" +
            "<D:lockscope><D:exclusive/></D:lockscope>" +
            "<D:locktype><D:write/></D:locktype>" +
            "</D:lockentry>"
}
