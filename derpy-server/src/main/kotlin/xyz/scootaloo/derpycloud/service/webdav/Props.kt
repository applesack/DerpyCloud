package xyz.scootaloo.derpycloud.service.webdav

import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.http.impl.MimeMapping
import io.vertx.ext.web.impl.Utils
import org.dom4j.Document
import org.dom4j.DocumentHelper
import org.dom4j.Element
import org.dom4j.Namespace
import org.dom4j.QName
import xyz.scootaloo.derpycloud.service.file.FileInfo
import xyz.scootaloo.derpycloud.service.file.UFiles
import xyz.scootaloo.derpycloud.service.file.UPaths
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

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

data class PropPatch(
    val remove: Boolean,
    val props: MutableList<Property> = ArrayList()
)

data class XName(
    val space: String,
    val local: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as XName

        if (local != other.local) return false

        return true
    }

    override fun hashCode(): Int {
        return local.hashCode()
    }
}

data class PropStat(
    val props: MutableList<Property> = ArrayList(),
    var status: HttpResponseStatus = HttpResponseStatus.OK,
    var xmlError: String = "",
    var responseDescription: String = ""
)

data class Property(
    val name: XName,
    val fi: FileInfo,
    val render: PropRender,
    val lang: String = ""
)

data class MultiResponse(
    val href: String,
    val propStats: List<PropStat>
)

fun interface PropRender {
    fun render(fi: FileInfo, root: Element)
}

object Props {

    // key: dir, value: render
    val liveProps: Map<XName, Pair<Boolean, PropRender>> by lazy {
        val map = HashMap<XName, Pair<Boolean, PropRender>>()
        map[XName("DAV:", "resourcetype")] = true to PropRender { f, r -> findResourceType(f, r) }
        map[XName("DAV:", "displayname")] = false to PropRender { f, r -> findDisplayName(f, r) }
        map[XName("DAV:", "getcontentlength")] = true to PropRender { f, r -> findContentLength(f, r) }
        map[XName("DAV:", "getlastmodified")] = true to PropRender { f, r -> findLastModified(f, r) }
        map[XName("DAV:", "creationdate")] = true to PropRender { f, r -> findCreationDate(f, r) }
        map[XName("DAV:", "getcontenttype")] = true to PropRender { f, r -> findContentType(f, r) }
        map[XName("DAV:", "getetag")] = false to PropRender { f, r -> findETag(f, r) }
//        map[XName("DAV:", "lockdiscovery")] = true to PropRender { _, r -> findSupportedLock(r) }
        map
    }

    val livePropNames: List<XName> by lazy {
        val value = ArrayList<XName>(liveProps.size)
        for ((xn, _) in liveProps) {
            value.add(xn)
        }
        value
    }

}

private val appUrnToken by lazy { "urn:uuid:${UUID.randomUUID().toString()}/" }
private const val APP_TKN_NS = "ns0"
private const val CONTENT_NS = "ns1"
private const val CONTENT_SCHEMA = "urn:schemas-microsoft-com:"

class MultiStatusRender(private val document: Document = DocumentHelper.createDocument()) {
    private val namespace = Namespace("D", "DAV:")
    private val root get() = document.rootElement

    init {
        document.addElement(qname("multistatus", namespace))
        root.addNamespace(APP_TKN_NS, appUrnToken)
        root.addNamespace(CONTENT_NS, CONTENT_SCHEMA)
    }

    fun addResp(resp: MultiResponse) {
        val respLabel = root.addElement(qname("response", namespace))
        val hrefLabel = respLabel.addElement(qname("href", namespace))
        hrefLabel.addText(UPaths.href(resp.href))
        for (propStat in resp.propStats) {
            val propStatLabel = respLabel.addElement(qname("propstat", namespace))
            val propLabel = propStatLabel.addElement(qname("prop", namespace))
            for (prop in propStat.props) {
                val localLabel = propLabel.addElement(qname(prop.name.local, namespace))
                prop.render.render(prop.fi, localLabel)
            }
            val statusLabel = propStatLabel.addElement(qname("status", namespace))
            val statusContent = String.format(
                "HTTP/1.1 %d %s", propStat.status.code(), propStat.status.reasonPhrase()
            )
            statusLabel.addText(statusContent)
            if (propStat.responseDescription.isNotEmpty()) {
                val respDescLabel = propStatLabel.addElement(qname("responsedescription", namespace))
                respDescLabel.addText(propStat.responseDescription)
            }
        }
    }

    fun buildXML(): String {
        document.xmlEncoding = "utf-8"
        return document.asXML()
    }

}

private fun qname(name: String, ns: Namespace): QName {
    return QName(name, ns)
}

private fun findResourceType(fi: FileInfo, root: Element) {
    if (fi.isDir) {
        val ns = root.namespace
        root.addElement(qname("collection", ns))
    }
}

private fun findContentLength(fi: FileInfo, root: Element) {
    root.addText(fi.size.toString())
}

private fun findDisplayName(fi: FileInfo, root: Element) {
    if (UPaths.slashClean(fi.name) == "/") {
        root.addText("")
        return
    }
    root.addText(UPaths.encodeUri(fi.name))
}

private fun findLastModified(fi: FileInfo, root: Element) {
    root.addAttribute("ns0:dt", "dateTime.rfc1123")
    root.addText(Utils.formatRFC1123DateTime(fi.modTime))
}

private val rfc3339Format by lazy { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'") }

private fun findCreationDate(fi: FileInfo, root: Element) {
    root.addAttribute("ns0:dt", "datetime.tz")
    root.addText(rfc3339Format.format(Date(fi.creationTime)))
}

private fun findContentType(fi: FileInfo, root: Element) {
    if (fi.isDir) {
        root.addText("httpd/unix-directory")
    } else {
        root.addText(
            MimeMapping.getMimeTypeForFilename(fi.name) ?: MimeMapping.getMimeTypeForExtension("bin")
        )
    }
}

private fun findETag(fi: FileInfo, root: Element) {
    root.addText(UFiles.findETag(fi))
}

private fun findSupportedLock(root: Element) {
    val ns = root.namespace
    val lockEntry = root.addElement(qname("lockentry", ns))
    val lockScope = lockEntry.addElement(qname("lockscope", ns))
    lockScope.addElement(qname("exclusive", ns))
    val lockType = lockEntry.addElement(qname("locktype", ns))
    lockType.addElement(qname("write", ns))
}
