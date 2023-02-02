package xyz.scootaloo.server.service.webdav

import org.dom4j.DocumentHelper
import org.dom4j.Element

/**
 * @author AppleSack
 * @since  2023/02/02
 */
object Xml {

    fun readPropfind(text: String): Pair<Boolean, PropFind> {
        val pf = PropFind()
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
                    pf.props.add(Name(prop.namespacePrefix, prop.name))
                }

                "propname" -> pf.propName = true
                "allprop" -> pf.allProp = true
                "include" -> {
                    for (prop in ele.elementIterator()) {
                        if (prop !is Element) {
                            continue
                        }
                        pf.include.add(Name(prop.namespacePrefix, prop.name))
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

}
