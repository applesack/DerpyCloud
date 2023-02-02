package xyz.scootaloo.server.xml

import org.junit.jupiter.api.Test
import xyz.scootaloo.server.service.webdav.Xml

/**
 * @author AppleSack
 * @since  2023/02/02
 */
class XmlTest {

    @Test
    fun testXmlProp() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?> 
            <D:propfind xmlns:D="DAV:"> 
                <D:prop xmlns:R="http://ns.example.com/boxschema/"> 
                    <R:bigbox/> 
                    <R:author/> 
                    <R:DingALing/> 
                    <R:Random/> 
                </D:prop> 
            </D:propfind>
        """.trimIndent()
        println(Xml.readPropfind(xml))
    }

    @Test
    fun testXmlPropName() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?> 
            <propfind xmlns="DAV:"> 
                <propname/>
            </propfind>
        """.trimIndent()
        println(Xml.readPropfind(xml))
    }

    @Test
    fun testXmlAllProp() {
        val xml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <D:propfind xmlns:D="DAV:">
                <D:allprop/>
            </D:propfind>
        """.trimIndent()
        println(Xml.readPropfind(xml))
    }

    @Test
    fun testXmlAllPropAndInclude() {
        val xml = """
            <?xml version="1.0" encoding="utf-8" ?> 
            <D:propfind xmlns:D="DAV:"> 
                <D:allprop/>
                <D:include> 
                    <D:supported-live-property-set/> 
                    <D:supported-report-set/> 
                </D:include> 
            </D:propfind> 
        """.trimIndent()
        println(Xml.readPropfind(xml))
    }

}
