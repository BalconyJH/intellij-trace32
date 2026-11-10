package io.github.balconyjh.trace32.cmm

import com.intellij.codeInsight.editorActions.QuoteHandler
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.psi.PsiFile
import com.intellij.application.options.CodeStyle
import io.github.balconyjh.trace32.cmm.core.CmmScanner

class CmmQuoteHandler : QuoteHandler {
    override fun isOpeningQuote(iterator: HighlighterIterator, offset: Int) = quoteRole(iterator.document.charsSequence, offset) == 1
    override fun isClosingQuote(iterator: HighlighterIterator, offset: Int) = quoteRole(iterator.document.charsSequence, offset) == -1
    override fun isInsideLiteral(iterator: HighlighterIterator) = literal(iterator.document.charsSequence, iterator.start) != null
    override fun hasNonClosedLiteral(editor: Editor, iterator: HighlighterIterator, offset: Int): Boolean {
        val text = editor.document.charsSequence
        val token = literal(text, offset) ?: literal(text, (offset - 1).coerceAtLeast(0)) ?: return false
        return closingOffset(text, token) == null
    }
    companion object {
        private fun literal(text: CharSequence, offset: Int) = CmmScanner.scan(text).firstOrNull {
            it.kind() == CmmScanner.Kind.STRING && it.start() <= offset && offset < it.end()
        }
        private fun closingOffset(text: CharSequence, token: CmmScanner.Token): Int? {
            val quote = text[token.start()]
            var offset = token.start() + 1
            while (offset < token.end()) {
                if (text[offset] == quote) {
                    if (offset + 1 < token.end() && text[offset + 1] == quote) offset++ else return offset
                }
                offset++
            }
            return null
        }
        /** Independent of highlight-token splitting around embedded macros. */
        @JvmStatic fun quoteRole(text: CharSequence, offset: Int): Int {
            val token = literal(text, offset) ?: return 0
            return when (offset) { token.start() -> 1; closingOffset(text, token) -> -1; else -> 0 }
        }
    }
}

class CmmTemplateContextType : TemplateContextType("Lauterbach TRACE32® PRACTICE") {
    override fun isInContext(context: TemplateActionContext): Boolean {
        if (context.file !is CmmFile) return false
        val offset = context.startOffset
        val token = CmmScanner.scan(context.file.text).firstOrNull { it.start() <= offset && offset < it.end() }
        return token?.kind() !in setOf(CmmScanner.Kind.COMMENT, CmmScanner.Kind.STRING)
    }
}

class CmmColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName() = "Lauterbach TRACE32® PRACTICE"
    override fun getIcon() = CmmFileType.INSTANCE.icon
    override fun getHighlighter() = CmmSyntaxHighlighter()
    override fun getDemoText() = """
        // PRACTICE editor colors
        PRIVATE &directory &value
        &directory=OS.PPD()
        &value=0x1234
        SYStem.CPU CortexM
        IF &value!=0
        (
          PRINT "Value: &(value)"
          DO "&directory/prepare.cmm"
        )
        failed:
        ENDDO
    """.trimIndent()
    override fun getAdditionalHighlightingTagToDescriptorMap() = null
    override fun getAttributeDescriptors() = CmmSyntaxHighlighter.COLORS.entries.map {
        AttributesDescriptor(it.key.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase), it.value)
    }.toTypedArray()
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
}

class CmmEnterHandler : EnterHandlerDelegate {
    override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): EnterHandlerDelegate.Result {
        if (file !is CmmFile) return EnterHandlerDelegate.Result.Continue
        val document = editor.document
        val offset = editor.caretModel.offset
        val line = document.getLineNumber(offset)
        if (line == 0) return EnterHandlerDelegate.Result.Continue
        val previous = document.getText(com.intellij.openapi.util.TextRange(document.getLineStartOffset(line - 1), document.getLineEndOffset(line - 1)))
        val previousBody = previous.trim()
        val settings = CodeStyle.getIndentOptions(file)
        val base = previous.takeWhile { it == ' ' || it == '\t' }
        val unit = if (settings.USE_TAB_CHARACTER) "\t" else " ".repeat(settings.INDENT_SIZE.coerceAtLeast(1))
        var indent = base + if (previousBody == "(" || previousBody.startsWith("( //") || previousBody.startsWith("( ;")) unit else ""
        val start = document.getLineStartOffset(line)
        var end = start
        while (end < document.getLineEndOffset(line) && document.charsSequence[end] in " \t") end++
        val body = document.charsSequence.subSequence(end, document.getLineEndOffset(line)).toString()
        if (body.startsWith(")")) indent = indent.removeSuffix(unit)
        if (Regex("[A-Za-z_][A-Za-z0-9_]*:(?:\\s.*)?").matches(body) && end == start) indent = ""
        if (document.charsSequence.subSequence(start, end).toString() != indent) {
            document.replaceString(start, end, indent)
            editor.caretModel.moveToOffset(start + indent.length)
        }
        return EnterHandlerDelegate.Result.Continue
    }
}
