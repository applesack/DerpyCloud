package xyz.scootaloo.derpycloud.service.webdav

import io.netty.handler.codec.http.HttpResponseStatus
import org.dom4j.DocumentHelper
import org.dom4j.Element

/**
 * @author AppleSack
 * @since  2023/02/02
 */
object DAVHelper {

    // todo 将boolean值交换为第二个值
    fun readPropFind(text: String): Pair<Boolean, PropFind> {
        val pf = PropFind()
        if (text.isBlank()) {
            pf.allProp = true
            return true to pf
        }

        val doc = runCatching { DocumentHelper.parseText(text) }
        if (doc.isFailure) {
            return false to pf
        }

        val root = doc.getOrThrow().rootElement
        if (root.name != "propfind") {
            return false to pf
        }
        for (ele in root.elementIterator()) {
            if (ele !is Element) {
                continue
            }

            when (ele.name) {
                "prop" -> for (prop in ele.elementIterator()) {
                    if (prop !is Element) {
                        continue
                    }
                    pf.props.add(XName(prop.namespacePrefix, prop.name))
                }

                "propname" -> pf.propName = true
                "allprop" -> pf.allProp = true
                "include" -> {
                    for (prop in ele.elementIterator()) {
                        if (prop !is Element) {
                            continue
                        }
                        pf.include.add(XName(prop.namespacePrefix, prop.name))
                    }
                }
            }
        }

        if (!pf.allProp && pf.include.isNotEmpty()) {
            return false to pf
        }
        if (pf.allProp && (pf.props.isNotEmpty() || pf.propName)) {
            return false to pf
        }
        if (pf.props.isNotEmpty() && pf.propName) {
            return false to pf
        }
        if (!pf.propName && !pf.allProp && pf.props.isEmpty()) {
            return false to pf
        }

        return true to pf
    }

    fun readPropPatch(text: String): Pair<List<PropPatch>, Int> {
        val safeParse = kotlin.runCatching { DocumentHelper.parseText(text) }
        if (safeParse.isFailure) {
            return emptyList<PropPatch>() to HttpResponseStatus.BAD_REQUEST.code()
        }

        val setPatch = PropPatch(false)
        val remPatch = PropPatch(true)
        val root = safeParse.getOrThrow().rootElement
        for (updateLabel in root.elementIterator()) {
            if (updateLabel !is Element) {
                continue
            }
            var set = updateLabel.name == "set"
            val propLabel = updateLabel.element("prop") ?: continue

        }

        TODO()
    }

    // todo 将 int 值交换为第二个值
    fun readLockInfo(text: String): Pair<Int, LockInfo> {
        if (text.isBlank()) {
            // 刷新锁的请求
            return 0 to LockInfo.NONE
        }
        val doc = runCatching { DocumentHelper.parseText(text) }
        if (doc.isFailure) {
            return HttpResponseStatus.BAD_REQUEST.code() to LockInfo.NONE
        }
        var shared = false
        var exclusive = false
        var lockType = ""
        var owner = ""

        val root = doc.getOrThrow().rootElement
        if (root.name != "lockinfo") {
            return HttpResponseStatus.BAD_REQUEST.code() to LockInfo.NONE
        }

        val lockScopeLabel = root.element("lockscope")
        if (lockScopeLabel != null) {
            if (lockScopeLabel.element("exclusive") != null) {
                exclusive = true
            }
            if (lockScopeLabel.element("shared") != null) {
                shared = true
            }
        }

        val lockTypeLabel = root.element("locktype")
        if (lockTypeLabel != null) {
            if (lockTypeLabel.element("write") != null) {
                lockType = "write"
            }
        }

        val ownerLabel = root.element("owner")
        if (ownerLabel != null) {
            val href = ownerLabel.element("href")
            if (href != null) {
                owner = href.text
            }
        }

        if (!exclusive || shared || lockType != "write") {
            return HttpResponseStatus.NOT_IMPLEMENTED.code() to LockInfo.NONE
        }

        return 0 to LockInfo(isShared = false, isWrite = true, owner = owner)
    }

    fun writeLockInfo(token: String, details: LockDetails): String {
        var depth = "infinity"
        if (details.zeroDepth) {
            depth = "0"
        }
        // todo root标签写入应用的命名空间信息 ns0
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<D:prop xmlns:D=\"DAV:\"><D:lockdiscovery><D:activelock>\n" +
                "<D:locktype><D:write/></D:locktype>\n" +
                "<D:lockscope><D:exclusive/></D:lockscope>\n" +
                "<D:depth>${depth}</D:depth>\n" +
                "<D:owner><D:href>${details.owner}</D:href></D:owner>\n" +
                "<D:timeout>Second-${details.duration}</D:timeout>\n" +
                "<D:locktoken><D:href>${token}</D:href></D:locktoken>\n" +
                "<D:lockroot><D:href>${details.root}</D:href></D:lockroot>\n" +
                "</D:activelock></D:lockdiscovery></D:prop>"
    }

}
