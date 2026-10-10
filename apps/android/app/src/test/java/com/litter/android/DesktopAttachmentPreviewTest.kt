package com.litter.android

import com.litter.android.ui.desktop.DesktopAttachmentPreview
import com.litter.android.ui.desktop.desktopAttachmentPreviewText
import java.nio.charset.CharacterCodingException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopAttachmentPreviewTest {
    @Test fun displaysTypeAndSizeWithoutChangingFilename() {
        val file = DesktopAttachmentPreview("季度报告.pdf", "application/pdf", 1_500_000, null)
        assertEquals("季度报告.pdf", file.filename)
        assertEquals("PDF · 1.5 MB", file.description)
        assertFalse(file.isImage)
        assertFalse(file.isText)
        assertEquals("文件", DesktopAttachmentPreview("无扩展名", "application/octet-stream", null, null).description)
    }

    @Test fun supportsImagesAndUtf8TextIncludingBom() {
        assertTrue(DesktopAttachmentPreview("photo.jpg", "image/jpeg", 0, null).isImage)
        assertTrue(DesktopAttachmentPreview("config.json", "application/json", 0, null).isText)
        val preview = desktopAttachmentPreviewText("\uFEFF你好，附件\n第二行".toByteArray())
        assertEquals("你好，附件\n第二行", preview.text)
        assertFalse(preview.truncated)
    }

    @Test fun limitsLongTextWithoutSplittingUnicodeSurrogatePair() {
        val preview = desktopAttachmentPreviewText(("a".repeat(63_999) + "😀结尾").toByteArray())
        assertEquals("a".repeat(63_999), preview.text)
        assertTrue(preview.truncated)
    }

    @Test(expected = CharacterCodingException::class)
    fun rejectsInvalidUtf8InsteadOfShowingGarbledText() {
        desktopAttachmentPreviewText(byteArrayOf(0xc3.toByte(), 0x28))
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsBinaryContentDisguisedAsText() {
        desktopAttachmentPreviewText(byteArrayOf(65, 0, 66))
    }
}
