package xyz.scootaloo.derpycloud.xml

import org.junit.jupiter.api.Test
import xyz.scootaloo.derpycloud.service.webdav.DAVHelper

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
        println(DAVHelper.readPropFind(xml))
    }

    @Test
    fun testXmlPropName() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?> 
            <propfind xmlns="DAV:"> 
                <propname/>
            </propfind>
        """.trimIndent()
        println(DAVHelper.readPropFind(xml))
    }

    @Test
    fun testXmlAllProp() {
        val xml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <D:propfind xmlns:D="DAV:">
                <D:allprop/>
            </D:propfind>
        """.trimIndent()
        println(DAVHelper.readPropFind(xml))
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
        println(DAVHelper.readPropFind(xml))
    }

    @Test
    fun testPropPatch() {
        val xml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <D:propertyupdate xmlns:D="DAV:" xmlns:Z="urn:schemas-microsoft-com:">
                <D:set>
                    <D:prop>
                        <Z:Win32LastModifiedTime>Sun, 12 Feb 2023 06:49:56 GMT</Z:Win32LastModifiedTime>
                    </D:prop>
                </D:set>
            </D:propertyupdate>
        """.trimIndent()
        val (proppatch, code) = DAVHelper.readPropPatch(xml)
        println("$proppatch, $code")
    }

}
