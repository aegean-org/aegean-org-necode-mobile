package com.litter.android.ui.conversation

import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectableConversationTextTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var textView: TextView
    private var linkClicked = false

    private fun showText() {
        compose.setContent {
            AndroidView(
                modifier = Modifier.size(300.dp, 180.dp).testTag("selectable-markdown"),
                factory = { context ->
                    TextView(context).apply {
                        textView = this
                        configureSelectableMarkdownTextView(
                            textView = this, textColor = 0xFF000000.toInt(),
                            linkColor = 0xFF0000FF.toInt(), textSize = 18f,
                            enableTerminalSelectionAction = false,
                        )
                        text = SpannableString("link and selectable words").apply {
                            setSpan(object : ClickableSpan() {
                                override fun onClick(widget: View) { linkClicked = true }
                            }, 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                },
            )
        }
        compose.waitForIdle()
    }

    private fun position(offset: Int): Offset = compose.runOnIdle {
        val line = textView.layout.getLineForOffset(offset)
        Offset(
            textView.totalPaddingLeft + textView.layout.getPrimaryHorizontal(offset),
            textView.totalPaddingTop + (textView.layout.getLineTop(line) + textView.layout.getLineBottom(line)) / 2f,
        )
    }

    @Test
    fun selectableMarkdown_followsLinkOnTap() {
        showText()
        val point = position(2)
        compose.onNodeWithTag("selectable-markdown").performTouchInput { click(point) }
        compose.runOnIdle {
            assertTrue(textView.isTextSelectable)
            assertTrue(textView.linksClickable)
            assertTrue("Tapping the clickable span must invoke its link", linkClicked)
        }
    }

    @Test
    fun selectableMarkdown_longPressSelectsAndCopiesText() {
        showText()
        val point = position(14)
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int) {
            val event = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), action,
                point.x, point.y, 0,
            ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try { compose.runOnIdle { textView.dispatchTouchEvent(event) } } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN)
        // Native TextView long-press detection uses real uptime, not Compose's event clock.
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 200)
        send(MotionEvent.ACTION_UP)
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("Long press must select native text", textView.selectionStart >= 0)
            assertTrue(textView.selectionEnd > textView.selectionStart)
            val selected = textView.text.subSequence(textView.selectionStart, textView.selectionEnd).toString()
            assertTrue(textView.onTextContextMenuItem(android.R.id.copy))
            val clipboard = textView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(selected, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            assertTrue("Selecting text must not open a link", !linkClicked)
        }
    }
}
