package io.github.balconyjh.trace32.cmm

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import io.github.balconyjh.trace32.cmm.core.CmmModel
import io.github.balconyjh.trace32.cmm.core.CmmScanner

class CmmAnnotator : Annotator {
    data class Issue(val start: Int, val end: Int, val message: String)
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element as? CmmFile ?: return
        for (issue in inspect(file.text)) holder.newAnnotation(HighlightSeverity.WARNING, issue.message)
            .range(TextRange(issue.start, issue.end)).create()
    }
    companion object {
        @JvmStatic fun inspect(text: String): List<Issue> {
            val issues = ArrayList<Issue>()
            val tokens = CmmScanner.scan(text)
            val opens = ArrayDeque<CmmScanner.Token>()
            for (token in tokens) {
                if (token.kind() == CmmScanner.Kind.LPAREN) opens.addLast(token)
                if (token.kind() == CmmScanner.Kind.RPAREN) {
                    if (opens.isEmpty()) issues += Issue(token.start(), token.end(), "Unmatched closing parenthesis")
                    else opens.removeLast()
                }
                if (token.kind() == CmmScanner.Kind.STRING) {
                    val value = text.substring(token.start(), token.end())
                    val quote = value.firstOrNull()
                    if (quote == '\"' || quote == '\'' || quote == '`') {
                        var i = 1
                        var closed = false
                        while (i < value.length) {
                            if (value[i] == quote) {
                                if (i + 1 < value.length && value[i + 1] == quote) i++ else { closed = true; break }
                            }
                            i++
                        }
                        if (!closed) issues += Issue(token.start(), token.end(), "Unterminated quoted text")
                    }
                }
            }
            for (token in opens) issues += Issue(token.start(), token.end(), "Unmatched opening parenthesis")
            val model = CmmModel.parse(text)
            val labels = HashSet<String>()
            for (symbol in model.symbols()) if (!symbol.macro() && !labels.add(symbol.name()))
                issues += Issue(symbol.start(), symbol.end(), "Duplicate label or subroutine '${symbol.name()}'")
            // GOTO has a local target; JUMPTO and dynamic macro targets can resolve on a runtime stack.
            for (ref in model.references()) {
                if (ref.macro() || model.resolve(ref) != null) continue
                val start = text.lastIndexOf('\n', ref.start() - 1) + 1
                val next = text.getOrNull(ref.end())
                if (next != null && !next.isWhitespace() && next != ';' && !text.startsWith("//", ref.end())) continue
                if (Regex("(?i).*\\bGOTO\\s+$").matches(text.substring(start, ref.start())))
                    issues += Issue(ref.start(), ref.end(), "No local label '${ref.name()}'")
            }
            return issues
        }
    }
}
