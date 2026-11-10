package io.github.balconyjh.trace32.cmm

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import io.github.balconyjh.trace32.cmm.core.CmmScanner.Kind

class CmmFoldingBuilder : FoldingBuilderEx(), DumbAware {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val file = root as? CmmFile ?: return FoldingDescriptor.EMPTY_ARRAY
        return file.model().folds().map { fold ->
            FoldingDescriptor(file.findElementAt(fold.start())?.node ?: file.node, TextRange(fold.start(), fold.end()))
        }.toTypedArray()
    }
    override fun getPlaceholderText(node: ASTNode) =
        if (node.elementType == CmmTypes.of(Kind.COMMENT) || node.text.trimStart().startsWith("//") || node.text.trimStart().startsWith(";")) "…" else "( … )"
    override fun isCollapsedByDefault(node: ASTNode) = false
}
