package io.github.balconyjh.trace32.cmm

import com.intellij.lexer.LexerBase
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Defaults
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import io.github.balconyjh.trace32.cmm.core.CmmScanner
import io.github.balconyjh.trace32.cmm.core.CmmScanner.Kind

class CmmSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer() = CmmHighlightingLexer()
    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        COLORS.entries.firstOrNull { CmmTypes.of(it.key) == tokenType }?.let { pack(it.value) } ?: TextAttributesKey.EMPTY_ARRAY
    class Factory : SyntaxHighlighterFactory() {
        override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) = CmmSyntaxHighlighter()
    }
    companion object {
        @JvmField val COLORS: Map<Kind, TextAttributesKey> = linkedMapOf(
            Kind.COMMENT to Defaults.LINE_COMMENT, Kind.STRING to Defaults.STRING,
            Kind.MACRO to Defaults.LOCAL_VARIABLE, Kind.KEYWORD to Defaults.KEYWORD,
            Kind.COMMAND to Defaults.FUNCTION_CALL, Kind.FUNCTION to Defaults.STATIC_METHOD,
            Kind.NUMBER to Defaults.NUMBER, Kind.LABEL to Defaults.LABEL,
            Kind.OPERATOR to Defaults.OPERATION_SIGN, Kind.LPAREN to Defaults.PARENTHESES,
            Kind.RPAREN to Defaults.PARENTHESES, Kind.BAD_CHARACTER to HighlighterColors.BAD_CHARACTER
        ).mapValues { (kind, fallback) -> TextAttributesKey.createTextAttributesKey("CMM_$kind", fallback) }
    }
}

/** Highlight-only view: PSI still sees a complete quoted string. */
class CmmHighlightingLexer : LexerBase() {
    private data class Part(val kind: Kind, val start: Int, val end: Int, val state: Int)
    private var buffer: CharSequence = ""
    private var end = 0
    private var parts = emptyList<Part>()
    private var index = 0
    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        end = endOffset
        val result = ArrayList<Part>()
        // Reconstruct enclosing string context on restart; never split the parser's string tokens.
        for (token in CmmScanner.scan(buffer.subSequence(0, endOffset))) {
            if (token.end() <= startOffset) continue
            if (token.kind() != Kind.STRING) {
                result += Part(token.kind(), maxOf(token.start(), startOffset), token.end(), token.state())
                continue
            }
            val text = buffer.subSequence(token.start(), token.end()).toString()
            var cursor = token.start()
            val inside = token.state() or (1 shl 12) or (text.first().code shl 16)
            for (match in MACRO.findAll(text)) {
                val from = token.start() + match.range.first
                val to = token.start() + match.range.last + 1
                if (cursor < from && from > startOffset)
                    result += Part(Kind.STRING, maxOf(cursor, startOffset), from, if (cursor == token.start()) token.state() else inside)
                if (to > startOffset) result += Part(Kind.MACRO, maxOf(from, startOffset), to, inside)
                cursor = to
            }
            if (cursor < token.end() && token.end() > startOffset)
                result += Part(Kind.STRING, maxOf(cursor, startOffset), token.end(), if (cursor == token.start()) token.state() else inside)
        }
        parts = result
        index = 0
    }
    override fun getState() = parts.getOrNull(index)?.state ?: 0
    override fun getTokenType() = parts.getOrNull(index)?.let { CmmTypes.of(it.kind) }
    override fun getTokenStart() = parts.getOrNull(index)?.start ?: end
    override fun getTokenEnd() = parts.getOrNull(index)?.end ?: end
    override fun advance() { index++ }
    override fun getBufferSequence() = buffer
    override fun getBufferEnd() = end
    companion object { private val MACRO = Regex("&(?:[A-Za-z_][A-Za-z0-9_]*|\\([A-Za-z_][A-Za-z0-9_]*\\))") }
}
