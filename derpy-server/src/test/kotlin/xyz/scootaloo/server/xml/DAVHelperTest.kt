package xyz.scootaloo.server.xml

import org.junit.jupiter.api.Test
import xyz.scootaloo.server.service.webdav.DAVHelper

/**
 * @author AppleSack
 * @since  2023/02/02
 */
class DAVHelperTest {

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
        println(DAVHelper.readPropfind(xml))
    }

    @Test
    fun testXmlPropName() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?> 
            <propfind xmlns="DAV:"> 
                <propname/>
            </propfind>
        """.trimIndent()
        println(DAVHelper.readPropfind(xml))
    }

    @Test
    fun testXmlAllProp() {
        val xml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <D:propfind xmlns:D="DAV:">
                <D:allprop/>
            </D:propfind>
        """.trimIndent()
        println(DAVHelper.readPropfind(xml))
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
        println(DAVHelper.readPropfind(xml))
    }

}
