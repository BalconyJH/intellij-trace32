package io.github.balconyjh.trace32.cmm

import com.intellij.formatting.*
import com.intellij.openapi.util.TextRange
import io.github.balconyjh.trace32.cmm.core.CmmModel

/** Each physical line is indivisible: formatting changes leading whitespace only. */
class CmmFormattingModelBuilder : FormattingModelBuilder {
    override fun createModel(context: FormattingContext): FormattingModel {
        val file = context.containingFile
        val size = context.codeStyleSettings.getIndentOptions(CmmFileType.INSTANCE).INDENT_SIZE.coerceAtLeast(1)
        return FormattingModelProvider.createFormattingModelForPsiFile(file, Root(file.text, size), context.codeStyleSettings)
    }
    class Line(private val range: TextRange, val spaces: Int, val opensBlock: Boolean) : Block {
        override fun getTextRange() = range
        override fun getSubBlocks(): List<Block> = emptyList()
        override fun getWrap(): Wrap? = null
        override fun getIndent() = Indent.getSpaceIndent(spaces, true)
        override fun getAlignment(): Alignment? = null
        override fun getSpacing(child1: Block?, child2: Block) = Spacing.getReadOnlySpacing()
        override fun getChildAttributes(newChildIndex: Int) = ChildAttributes(Indent.getSpaceIndent(spaces), null)
        override fun isIncomplete() = false
        override fun isLeaf() = true
    }
    class Root(private val text: String, private val size: Int) : Block {
        private val lines = lines(text, size)
        override fun getTextRange() = TextRange(0, text.length)
        override fun getSubBlocks(): List<Block> = lines
        override fun getWrap(): Wrap? = null
        override fun getIndent() = Indent.getNoneIndent()
        override fun getAlignment(): Alignment? = null
        override fun getSpacing(child1: Block?, child2: Block): Spacing? = if (child1 == null) null else Spacing.createSpacing(0, 0, 1, true, Int.MAX_VALUE)
        override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
            val previous = lines.getOrNull(newChildIndex - 1)
            val spaces = previous?.let { it.spaces + if (it.opensBlock) size else 0 } ?: 0
            return ChildAttributes(Indent.getSpaceIndent(spaces, true), null)
        }
        override fun isIncomplete() = false
        override fun isLeaf() = lines.isEmpty()
    }
    companion object {
        @JvmStatic fun lines(text: String, indentSize: Int): List<Line> {
            val folds = CmmModel.parse(text).folds().filter { text.getOrNull(it.start()) == '(' }
            val result = ArrayList<Line>()
            var offset = 0
            var continued = false
            for (line in text.split('\n')) {
                val content = line.trimEnd('\r')
                val leading = content.takeWhile { it == ' ' || it == '\t' }.length
                val start = offset + leading
                if (leading < content.length) {
                    val body = content.substring(leading)
                    val label = leading == 0 && Regex("[A-Za-z_][A-Za-z0-9_]*:(?:\\s.*)?").matches(body)
                    val level = folds.count { it.start() < start && start < it.end() - 1 && !(body.startsWith(")") && start == it.end() - 1) }
                    val spaces = when {
                        label -> 0 // PRACTICE labels are column-sensitive.
                        continued -> content.take(leading).sumOf { if (it == '\t') indentSize else 1 }
                        else -> level * indentSize
                    }
                    result += Line(TextRange(start, offset + content.length), spaces, body == "(" || body.startsWith("( //") || body.startsWith("( ;"))
                    continued = body.trimEnd().endsWith('\\')
                }
                offset += line.length + 1
            }
            return result
        }
    }
}
