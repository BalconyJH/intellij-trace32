package io.github.balconyjh.trace32.cmm

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.tree.TokenSet
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import io.github.balconyjh.trace32.cmm.core.CmmScanner.Kind

class CmmFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner() = DefaultWordsScanner(CmmLexer(),
        TokenSet.create(CmmTypes.of(Kind.MACRO), CmmTypes.of(Kind.LABEL), CmmTypes.of(Kind.IDENTIFIER)),
        TokenSet.create(CmmTypes.of(Kind.COMMENT)), TokenSet.create(CmmTypes.of(Kind.STRING)))
    override fun canFindUsagesFor(psiElement: PsiElement) = (psiElement as? CmmParserDefinition.Atom)?.symbol() != null
    override fun getHelpId(psiElement: PsiElement): String? = null
    override fun getType(element: PsiElement) = if ((element as? CmmParserDefinition.Atom)?.symbol()?.macro() == true) "PRACTICE macro" else "PRACTICE label"
    override fun getDescriptiveName(element: PsiElement) = (element as? CmmParserDefinition.Atom)?.name ?: element.text
    override fun getNodeText(element: PsiElement, useFullName: Boolean) = getDescriptiveName(element)
}

/** Exact, file-local references supplement the generic word index, including quoted macro uses. */
class CmmReferenceSearch : QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
    override fun execute(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>): Boolean =
        ReadAction.computeBlocking<Boolean, RuntimeException> { search(parameters, consumer) }
    private fun search(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>): Boolean {
        val target = parameters.elementToSearch as? CmmParserDefinition.Atom ?: return true
        val file = target.containingFile as? CmmFile ?: return true
        val symbol = target.symbol() ?: return true
        for (reference in file.model().references()) {
            val resolved = file.model().resolve(reference) ?: continue
            if (resolved.start() != symbol.start()) continue
            val leaf = file.findElementAt(reference.start()) ?: continue
            val element = leaf.parent
            for (psiReference in element.references) {
                if (psiReference.rangeInElement.startOffset + element.textOffset != reference.start()) continue
                if (!com.intellij.psi.search.PsiSearchScopeUtil.isInScope(parameters.effectiveSearchScope, element)) continue
                if (psiReference.isReferenceTo(target) && !consumer.process(psiReference)) return false
            }
        }
        return true
    }
}

class CmmNamesValidator : NamesValidator {
    override fun isKeyword(name: String, project: Project?) = false
    override fun isIdentifier(name: String, project: Project?) = Regex("(?:&?[A-Za-z_][A-Za-z0-9_]*|&\\([A-Za-z_][A-Za-z0-9_]*\\))").matches(name)
}
