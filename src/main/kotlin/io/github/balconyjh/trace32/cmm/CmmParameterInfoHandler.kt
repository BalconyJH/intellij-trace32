package io.github.balconyjh.trace32.cmm

import com.intellij.lang.parameterInfo.*
import com.intellij.psi.PsiElement
import io.github.balconyjh.trace32.cmm.core.CmmScanner

class CmmParameterInfoHandler : ParameterInfoHandler<PsiElement, String> {
    override fun findElementForParameterInfo(context: CreateParameterInfoContext): PsiElement? {
        val owner = owner(context) ?: return null
        context.itemsToShow = arrayOf(CmmDocumentation.signature(owner.text) ?: return null)
        return owner
    }
    override fun showParameterInfo(element: PsiElement, context: CreateParameterInfoContext) {
        context.showHint(element, element.textOffset, this)
    }
    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext) = owner(context)
    override fun updateParameterInfo(parameterOwner: PsiElement, context: UpdateParameterInfoContext) {
        if (context.parameterOwner != parameterOwner) { context.removeHint(); return }
        val after = context.file.text.substring((parameterOwner.textRange.endOffset).coerceAtMost(context.offset), context.offset)
        val signature = CmmDocumentation.signature(parameterOwner.text) ?: return
        val function = signature.contains('(')
        var depth = 0
        var index = 0
        for (token in CmmScanner.scan(after)) {
            if (token.kind() == CmmScanner.Kind.LPAREN) depth++
            else if (token.kind() == CmmScanner.Kind.RPAREN) depth--
            else if (function && depth == 1 && after.substring(token.start(), token.end()) == ",") index++
        }
        if (!function) index = Regex("\"(?:\"\"|[^\"])*\"|\\S+").findAll(after).count().let { if (after.lastOrNull()?.isWhitespace() == true) it else maxOf(0, it - 1) }
        context.setCurrentParameter(index)
    }
    override fun updateUI(parameter: String, context: ParameterInfoUIContext) {
        val slot = Regex("<[^>]+>").findAll(parameter).toList().getOrNull(context.currentParameterIndex)
        context.setupUIComponentPresentation(parameter, slot?.range?.first ?: -1, slot?.range?.last?.plus(1) ?: -1,
            false, false, false, context.defaultParameterColor)
    }
    private fun owner(context: ParameterInfoContext): PsiElement? {
        val file = context.file as? CmmFile ?: return null
        val text = file.text
        val offset = context.offset.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        val tokens = CmmScanner.scan(text.substring(lineStart, offset))
        if (tokens.lastOrNull()?.kind() == CmmScanner.Kind.COMMENT) return null
        val open = ArrayDeque<Int>()
        for (index in tokens.indices) when (tokens[index].kind()) {
            CmmScanner.Kind.LPAREN -> open.addLast(index)
            CmmScanner.Kind.RPAREN -> if (open.isNotEmpty()) open.removeLast()
            else -> Unit
        }
        for (index in open.reversed()) {
            val function = tokens.take(index).lastOrNull { it.kind() != CmmScanner.Kind.WHITESPACE } ?: continue
            val name = text.substring(lineStart + function.start(), lineStart + function.end())
            if (CmmDocumentation.signature(name) != null) return file.findElementAt(lineStart + function.start())?.parent
        }
        val command = tokens.firstOrNull { it.kind() !in setOf(CmmScanner.Kind.WHITESPACE, CmmScanner.Kind.COMMENT) } ?: return null
        val name = text.substring(lineStart + command.start(), lineStart + command.end())
        if (CmmDocumentation.signature(name) == null) return null
        return file.findElementAt(lineStart + command.start())?.parent
    }
}
