package xyz.scootaloo.server.service.webdav

import org.dom4j.DocumentHelper
import org.dom4j.Element

/**
 * @author AppleSack
 * @since  2023/02/02
 */
object DAVParser {

    fun readPropfind(text: String): Pair<Boolean, PropFind> {
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

    fun readLockInfo(text: String): Pair<Int, LockInfo> {
        if (text.isBlank()) {
            // 刷新锁的请求
            return 0 to LockInfo.NONE
        }
        val doc = runCatching { DocumentHelper.parseText(text) }
        if (doc.isFailure) {
            return 400 to LockInfo.NONE
        }
        var shared = false
        var exclusive = false
        var lockType = ""
        var owner = ""

        val root = doc.getOrThrow().rootElement
        if (root.name != "lockinfo") {
            return 400 to LockInfo.NONE
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
            if (lockScopeLabel.element("write") != null) {
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
            return 501 to LockInfo.NONE
        }

        return 0 to LockInfo(isShared = false, isWrite = true, owner = owner)
    }

    private const val infiniteDepth = -1
    private const val invalidDepth = -2

    fun parseDepth(s: String): Int {
        return when (s) {
            "0" -> 0
            "1" -> infiniteDepth
            else -> invalidDepth
        }
    }

    private const val defTimeoutSeconds = 5
    private const val maxTimeoutSeconds = 232 - 1
    private const val infiniteTimeoutSeconds = -1

    fun parseTimeout(text: String): Pair<Boolean, Int> {
        if (text.isBlank()) {
            return true to defTimeoutSeconds
        }
        var timeoutStr = text
        val i = timeoutStr.lastIndexOf(',')
        if (i>=0) {
            timeoutStr = timeoutStr.substring(0, i)
        }
        timeoutStr = timeoutStr.trim()
        if (timeoutStr == "Infinite") {
            return true to infiniteTimeoutSeconds
        }
        val prefix = "Second-"
        if (!timeoutStr.startsWith(prefix)) {
            return false to 0
        }
        val timeoutSuffix = timeoutStr.substring(prefix.length + 1)
        if (timeoutSuffix == "" || timeoutSuffix[0] < '0' || timeoutSuffix[0] > '9') {
            return false to 0
        }
        val intResult = runCatching { timeoutSuffix.toInt() }
        if (intResult.isFailure) {
            return false to 0
        }
        val timeout = intResult.getOrElse { defTimeoutSeconds }
        if (timeout < 0 || timeout > maxTimeoutSeconds) {
            return false to 0
        }
        return true to timeout
    }

}
