package com.litter.android

import android.text.Editable
import android.text.TextWatcher
import android.text.Spanned
import android.graphics.Paint
import android.widget.TextView
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.litter.android.ui.LitterAppTheme
import com.litter.android.ui.conversation.SelectableMarkdownText
import io.noties.markwon.ext.tables.TableRowSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesktopMarkdownUpdateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun streamedChunksArePacedWithoutReplacingTheViewAndStopFlushesTheTail() {
        val body = mutableStateOf("已显示正文")
        val streaming = mutableStateOf(true)
        val updates = java.util.concurrent.ConcurrentLinkedQueue<String>()
        lateinit var view: TextView
        compose.setContent {
            LitterAppTheme {
                SelectableMarkdownText(
                    body.value, streaming = streaming.value, readingLayout = true,
                    enableTerminalSelectionAction = false,
                    onTextViewReady = {
                        view = it
                        it.addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                updates.add(s.toString())
                            }
                            override fun afterTextChanged(s: Editable?) {}
                        })
                    },
                )
            }
        }
        compose.waitForIdle()
        val originalView = view
        compose.runOnIdle {
            updates.clear()
            body.value += "新增文字".repeat(20)
        }
        compose.waitUntil(5_000) { updates.size >= 2 }
        // A second source snapshot must not restart a pending reveal clock.
        compose.runOnIdle { body.value += "更多文字".repeat(20) }
        compose.waitUntil(5_000) { view.text.toString() == body.value }
        compose.runOnIdle {
            assertSame(originalView, view)
            assertTrue("A chunk should become several visible updates", updates.size > 3)
            assertTrue(updates.all { it.startsWith("已显示正文") && body.value.startsWith(it) })
            assertTrue(updates.toList().zipWithNext().all { (previous, next) -> next.startsWith(previous) })
            body.value += "停止时必须完整显示".repeat(40)
            streaming.value = false
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("Stop/completion must flush the full snapshot", body.value, view.text.toString())
            streaming.value = true
            body.value = "修正后的正文"
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("修正后的正文", view.text.toString()) }
    }

    @Test fun tableRowsHaveMeasuredHeightBeforeUpdatedTextReachesTheScreen() {
        val body = mutableStateOf("已有正文")
        val streaming = mutableStateOf(true)
        val rowSizes = java.util.concurrent.ConcurrentLinkedQueue<Pair<Int, Int>>()
        lateinit var view: TextView
        compose.setContent {
            LitterAppTheme {
                SelectableMarkdownText(
                    body.value, streaming = streaming.value, readingLayout = true, enableTerminalSelectionAction = false,
                    onTextViewReady = {
                        view = it
                        it.addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            override fun afterTextChanged(s: Editable?) {
                                val rendered = s as? Spanned ?: return
                                rendered.getSpans(0, rendered.length, TableRowSpan::class.java).forEach { row ->
                                    val metrics = Paint.FontMetricsInt()
                                    val width = row.getSize(it.paint, rendered, rendered.getSpanStart(row), rendered.getSpanEnd(row), metrics)
                                    rowSizes.add(width to -metrics.ascent)
                                }
                            }
                        })
                    },
                )
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(view.width > 0)
            body.value += "\n\n| 名称 | 描述 |\n| --- | --- |\n| 第一项 | 很长的描述" + "用于验证多行单元格".repeat(12) + " |\n"
        }
        compose.waitUntil(5_000) { rowSizes.size >= 20 }
        compose.runOnIdle {
            streaming.value = false
            body.value += "\n表格后继续输出正文"
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("The real Markdown renderer must produce table rows", rowSizes.isNotEmpty())
            assertTrue("No table row should first appear with zero width/height", rowSizes.all { it.first > 0 && it.second > 0 })
        }
    }

    @Test fun unchangedBodyDoesNotResetTextAndStreamingKeepsTheView() {
        val body = mutableStateOf("已经输出 **正文**")
        val terminalAction = mutableStateOf(false)
        val size = mutableStateOf(15f)
        lateinit var view: TextView
        var changes = 0
        compose.setContent {
            LitterAppTheme {
                SelectableMarkdownText(
                    body.value, bodySize = size.value, readingLayout = true,
                    enableTerminalSelectionAction = terminalAction.value,
                    onTextViewReady = {
                        view = it
                        it.addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            override fun afterTextChanged(s: Editable?) { changes++ }
                        })
                    },
                )
            }
        }
        compose.waitForIdle()
        val originalView = view
        val initialChanges = changes
        compose.runOnIdle { terminalAction.value = true }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("Changing controls must not reassign unchanged Markdown", initialChanges, changes)
            body.value += "，继续流式回答"
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(originalView, view)
            assertEquals("已经输出 正文，继续流式回答", view.text.toString())
            assertTrue(changes > initialChanges)
        }
        val streamedChanges = changes
        compose.runOnIdle { size.value = 18f }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("New renderer/font size must update spans", changes > streamedChanges)
            assertEquals("已经输出 正文，继续流式回答", view.text.toString())
        }
    }
}
