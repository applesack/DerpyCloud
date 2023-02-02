package xyz.scootaloo.server.service.webdav

import io.vertx.core.http.impl.MimeMapping
import io.vertx.ext.web.impl.Utils
import xyz.scootaloo.server.service.file.FileInfo
import xyz.scootaloo.server.service.file.UPaths
import xyz.scootaloo.server.service.lock.LockSystem

/**
 * @author AppleSack
 * @since  2023/02/01
 */
class PropPatch(
    val remove: Boolean
)

object Props {

    fun patch(ls: LockSystem) {

    }

    fun findResourceType(fi: FileInfo): String {
        if (fi.isDir) {
            return """<D:collection xmlns:D="DAV:"/>"""
        }
        return ""
    }

    fun findDisplayName(fi: FileInfo): String {
        // todo escapeXML
        return UPaths.encodeUri(fi.path)
    }

    private fun findLastModified(fi: FileInfo): String {
        return Utils.formatRFC1123DateTime(fi.modTime)
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

}
