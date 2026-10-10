package com.litter.android.ui.conversation

import android.graphics.Picture
import android.text.Spanned
import android.text.SpannedString
import android.util.TypedValue
import android.text.method.LinkMovementMethod
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.litter.android.state.AppModel
import com.litter.android.ui.LitterTextStyle
import com.litter.android.ui.LitterTheme
import com.litter.android.ui.LitterThemeManager
import com.litter.android.ui.LocalTextScale
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableRowSpan
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
internal fun SelectableConversationText(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SelectionContainer(modifier = modifier) {
        content()
    }
}

@Composable
internal fun SelectableMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    bodySize: Float = LitterTextStyle.body,
    usePhysicalDpTextSize: Boolean = false,
    onTextViewReady: ((TextView) -> Unit)? = null,
    enableTerminalSelectionAction: Boolean = true,
    readingLayout: Boolean = false,
    streaming: Boolean = false,
) {
    val context = LocalContext.current
    val textScale = LocalTextScale.current
    val resolvedTextSize = bodySize * textScale
    val textColor = LitterTheme.textBody.toArgb()
    val useMono = LitterThemeManager.monoFontEnabled
    val codeTypeface = remember(context) {
        runCatching {
            androidx.core.content.res.ResourcesCompat.getFont(
                context,
                com.sigkitten.litter.android.R.font.berkeley_mono_regular,
            )
        }.getOrNull() ?: android.graphics.Typeface.MONOSPACE
    }
    val typeface = if (useMono) codeTypeface else android.graphics.Typeface.DEFAULT
    val markdownTextSizePx = remember(context, resolvedTextSize, usePhysicalDpTextSize) {
        resolvedTextSize.toTextSizePx(context, usePhysicalDpTextSize)
    }
    val markwon = rememberConversationMarkwon(
        context = context,
        codeTypeface = codeTypeface,
        readingLayout = readingLayout,
        markdownTextSizePx = markdownTextSizePx,
        textColor = textColor,
        inlineCodeBackground = LitterTheme.surfaceLight.toArgb(),
    )
    val shown = rememberStreamingMarkdown(text, streaming)
    val markdown = remember(shown) { normalizeMathMarkdown(shown) }

    AndroidView(
        factory = { ctx ->
            ConversationMarkdownTextView(ctx).apply {
                configureSelectableMarkdownTextView(
                    textView = this,
                    textColor = textColor,
                    linkColor = LitterTheme.accent.toArgb(),
                    textSize = resolvedTextSize,
                    typeface = typeface,
                    usePhysicalDpTextSize = usePhysicalDpTextSize,
                    enableTerminalSelectionAction = enableTerminalSelectionAction,
                    readingLayout = readingLayout,
                )
                onTextViewReady?.invoke(this)
            }
        },
        update = { tv ->
            configureSelectableMarkdownTextView(
                textView = tv,
                textColor = textColor,
                linkColor = LitterTheme.accent.toArgb(),
                textSize = resolvedTextSize,
                typeface = typeface,
                usePhysicalDpTextSize = usePhysicalDpTextSize,
                enableTerminalSelectionAction = enableTerminalSelectionAction,
                readingLayout = readingLayout,
            )
            tv.renderMarkdown(markwon, markdown)
        },
        modifier = modifier,
    )
}

private class ConversationMarkdownTextView(context: android.content.Context) : AppCompatTextView(context) {
    private var renderedSource: String? = null
    private var renderedWith: Markwon? = null

    fun renderMarkdown(renderer: Markwon, source: String) {
        // Control/parent updates must not reset identical spans, selection or layout.
        // Renderer identity also changes with font size/theme; those updates must still render.
        if (renderedWith === renderer && renderedSource == source) return
        val rendered = renderer.toMarkdown(source)
        prepareTableRows(rendered, width - compoundPaddingLeft - compoundPaddingRight)
        renderer.setParsedMarkdown(this, rendered)
        renderedWith = renderer
        renderedSource = source
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // A newly attached view has no width when setParsedMarkdown runs.
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            (text as? Spanned)?.let {
                prepareTableRows(it, MeasureSpec.getSize(widthMeasureSpec) - compoundPaddingLeft - compoundPaddingRight)
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun prepareTableRows(source: Spanned, contentWidth: Int) {
        if (contentWidth <= 0) return
        val rows = source.getSpans(0, source.length, TableRowSpan::class.java)
            .filter { it.getSize(paint, source, 0, 0, null) != contentWidth }
        if (rows.isEmpty()) return
        // Markwon lazily measures cells during draw, then posts setText to fix row heights.
        // Warm those layouts offscreen so the first visible layout already has exact metrics.
        val picture = Picture()
        val canvas = picture.beginRecording(contentWidth, 1)
        try {
            // Empty text makes Markwon use this canvas width, not the previous TextView layout.
            val measuringText = SpannedString("")
            rows.forEach { it.draw(canvas, measuringText, 0, 0, 0f, 0, 0, 0, paint) }
        } finally {
            picture.endRecording()
        }
    }
}

internal fun configureSelectableMarkdownTextView(
    textView: TextView,
    textColor: Int,
    linkColor: Int,
    textSize: Float,
    typeface: android.graphics.Typeface? = null,
    usePhysicalDpTextSize: Boolean = false,
    enableTerminalSelectionAction: Boolean = true,
    readingLayout: Boolean = false,
) {
    textView.setTextColor(textColor)
    textView.typeface = typeface
    textView.includeFontPadding = false
    textView.setLineSpacing(0f, if (readingLayout) 1.15f else 1f)
    if (usePhysicalDpTextSize) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize)
    } else {
        textView.textSize = textSize
    }
    textView.linksClickable = true
    textView.setLinkTextColor(linkColor)
    textView.setTextIsSelectable(true)
    // Enabling selection installs ArrowKeyMovementMethod; restore links afterwards.
    textView.movementMethod = LinkMovementMethod.getInstance()
    textView.customSelectionActionModeCallback =
        if (enableTerminalSelectionAction) RunInTerminalSelectionMenu(textView) else null
}

/**
 * Adds a "Run in Terminal" item to the text-selection ActionMode of the
 * markwon-rendered conversation text. Available only when the Rust store has
 * an active terminal session.
 */
private class RunInTerminalSelectionMenu(
    private val textView: TextView,
) : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        if (hasActiveTerminalSession()) {
            menu.add(Menu.NONE, MENU_ID_RUN_IN_TERMINAL, Menu.CATEGORY_SECONDARY, "Run in Terminal")
        }
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val existing = menu.findItem(MENU_ID_RUN_IN_TERMINAL)
        val hasSession = hasActiveTerminalSession()
        if (hasSession && existing == null) {
            menu.add(Menu.NONE, MENU_ID_RUN_IN_TERMINAL, Menu.CATEGORY_SECONDARY, "Run in Terminal")
            return true
        }
        if (!hasSession && existing != null) {
            menu.removeItem(MENU_ID_RUN_IN_TERMINAL)
            return true
        }
        return false
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId != MENU_ID_RUN_IN_TERMINAL) return false
        val start = textView.selectionStart.coerceAtLeast(0)
        val end = textView.selectionEnd.coerceAtMost(textView.text.length)
        if (start >= end) {
            mode.finish()
            return true
        }
        val selected = textView.text.subSequence(start, end).toString()
        val bytes = selected.toByteArray(Charsets.UTF_8)
        CoroutineScope(Dispatchers.Main.immediate).launch {
            runCatching {
                AppModel.shared.store.writeToActiveTerminal(bytes)
            }
        }
        mode.finish()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {}

    companion object {
        private const val MENU_ID_RUN_IN_TERMINAL = 0x6c697474 // 'litt'

        private fun hasActiveTerminalSession(): Boolean =
            AppModel.shared.store.activeTerminalId() != null
    }
}

@Composable
private fun rememberConversationMarkwon(
    context: android.content.Context,
    codeTypeface: android.graphics.Typeface,
    readingLayout: Boolean,
    markdownTextSizePx: Float,
    textColor: Int,
    inlineCodeBackground: Int,
): Markwon = remember(context, codeTypeface, readingLayout, markdownTextSizePx, textColor, inlineCodeBackground) {
    try {
        val prism4j = Prism4j(com.litter.android.ui.Prism4jGrammarLocator())
        val syntaxTheme = io.noties.markwon.syntax.Prism4jThemeDarkula.create()
        Markwon.builder(context)
            .usePlugin(SyntaxHighlightPlugin.create(prism4j, syntaxTheme))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.codeTypeface(codeTypeface).codeBlockTypeface(codeTypeface)
                    if (readingLayout) {
                        val density = context.resources.displayMetrics.density
                        builder.headingTextSizeMultipliers(floatArrayOf(1.5f, 1.3f, 1.15f, 1.05f, 1f, 1f))
                            .bulletWidth((4 * density).toInt())
                            .blockMargin((18 * density).toInt())
                    }
                    // SyntaxHighlightPlugin also themes inline code; override it after the plugin.
                    builder.codeTextColor(textColor)
                        .codeBackgroundColor(inlineCodeBackground)
                        .codeBlockTextColor(syntaxTheme.textColor())
                        .codeBlockBackgroundColor(syntaxTheme.background())
                }
            })
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(
                JLatexMathPlugin.create(markdownTextSizePx, markdownTextSizePx * 1.12f) { builder ->
                    builder.inlinesEnabled(true)
                    builder.blocksEnabled(true)
                    builder.theme().textColor(textColor)
                },
            )
            .build()
    } catch (_: Exception) {
        Markwon.create(context)
    }
}

private fun Float.toTextSizePx(
    context: android.content.Context,
    usePhysicalDpTextSize: Boolean,
): Float {
    val unit = if (usePhysicalDpTextSize) {
        TypedValue.COMPLEX_UNIT_DIP
    } else {
        TypedValue.COMPLEX_UNIT_SP
    }
    return TypedValue.applyDimension(unit, this, context.resources.displayMetrics)
}
