package com.litter.android

import com.litter.android.ui.desktop.DesktopMessageKind
import com.litter.android.ui.desktop.parseDesktopMessages
import org.junit.Assert.assertEquals
import org.junit.Test

// Projection policy is tested against the real shared Rust implementation.
// These tests cover the native JSON adapter and stable identity contract only.
class DesktopMessageParsingTest {
    @Test fun decodesStableIdentityAndPreservesProjectedOrder() {
        val messages = parseDesktopMessages("""[
            {"id":"a1:p1","role":"assistant","text":"先检查项目","kind":"text","status":null,"details":[]},
            {"id":"a1:t1","role":"assistant","text":"读取文件","kind":"tool","status":"running","details":["读取文件"]},
            {"id":"u1:p1","role":"user","text":"继续","kind":"text","status":null,"details":[]}
        ]""")
        assertEquals(listOf("a1:p1", "a1:t1", "u1:p1"), messages.map { it.id })
        assertEquals(DesktopMessageKind.TOOL, messages[1].kind)
        assertEquals("running", messages[1].status)
        assertEquals(null, messages[0].status)
    }

    @Test fun preservesGroupedFailureDetailsWithoutReGrouping() {
        val messages = parseDesktopMessages("""[
            {"id":"a1:t1","role":"assistant","text":"2 项操作","kind":"tool","status":"error","details":["read","bash：permission denied"]},
            {"id":"a2:t1","role":"assistant","text":"list","kind":"tool","status":"running","details":["list"]}
        ]""")
        assertEquals(2, messages.size)
        assertEquals("error", messages[0].status)
        assertEquals(listOf("read", "bash：permission denied"), messages[0].details)
    }

    @Test fun preservesAttachmentPreviewAndUnavailableReason() {
        val messages = parseDesktopMessages("""[
            {"id":"u1:image","role":"user","text":"photo.jpg","kind":"file","status":null,"details":[],
             "attachment":{"filename":"photo.jpg","mime":"image/jpeg","size":3,"data_base64":"YWJj","unavailable_reason":null}},
            {"id":"u1:file","role":"user","text":"report.pdf","kind":"file","status":null,"details":[],
             "attachment":{"filename":"report.pdf","mime":"application/pdf","size":null,"data_base64":null,"unavailable_reason":"附件内容未随消息同步"}}
        ]""")
        assertEquals("image/jpeg", messages[0].attachment?.mime)
        assertEquals(3L, messages[0].attachment?.size)
        assertEquals("YWJj", messages[0].attachment?.dataBase64)
        assertEquals(null, messages[0].attachment?.unavailableReason)
        assertEquals(null, messages[1].attachment?.size)
        assertEquals(null, messages[1].attachment?.dataBase64)
        assertEquals("附件内容未随消息同步", messages[1].attachment?.unavailableReason)
    }


    @Test fun keepsArtifactPathWithoutPretendingItIsInlineContent() {
        val messages = parseDesktopMessages("""[
            {"id":"a1:art:file","role":"assistant","text":"路线.html","kind":"file","status":null,"details":[],
             "attachment":{"filename":"路线.html","mime":"text/html","size":99,"path":"D:\\test\\路线.html","data_base64":null,"unavailable_reason":null}}
        ]""")
        assertEquals("D:\\test\\路线.html", messages.single().attachment?.path)
        assertEquals(null, messages.single().attachment?.dataBase64)
        assertEquals(true, messages.single().attachment?.isHtml)
    }

    @Test(expected = org.json.JSONException::class)
    fun rejectsProjectionWithoutStableIdentity() {
        parseDesktopMessages("""[{"role":"user","text":"真实消息","kind":"text","status":null,"details":[]}]""")
    }
}
