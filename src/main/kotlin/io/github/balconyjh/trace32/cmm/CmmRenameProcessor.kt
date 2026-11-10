package io.github.balconyjh.trace32.cmm

import com.intellij.psi.PsiElement
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import com.intellij.util.containers.MultiMap
import io.github.balconyjh.trace32.cmm.core.CmmModel

/** Refuse collisions before the platform touches any usages. Runtime symbols remain outside this model. */
class CmmRenameProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement) = (element as? CmmParserDefinition.Atom)?.symbol() != null
    override fun isInplaceRenameSupported() = false

    override fun findExistingNameConflicts(element: PsiElement, newName: String, conflicts: MultiMap<PsiElement, String>) {
        conflict(element, newName)?.let { conflicts.putValue(element, it) }
    }

    override fun renameElement(element: PsiElement, newName: String, usages: Array<out UsageInfo>, listener: RefactoringElementListener?) {
        conflict(element, newName)?.let { throw IncorrectOperationException(it) }
        val atom = element as? CmmParserDefinition.Atom ?: return
        val symbol = atom.symbol() ?: return
        val changes = linkedMapOf<PsiElement, MutableList<Edit>>()
        for (usage in usages) {
            val reference = usage.reference ?: continue
            if (reference.element.containingFile != atom.containingFile || !reference.isReferenceTo(atom)) continue
            val range = reference.rangeInElement
            changes.getOrPut(reference.element) { mutableListOf() } += Edit(range.startOffset, range.endOffset,
                CmmReferences.renamed(range.substring(reference.element.text), newName, symbol.macro()))
        }
        val declarationEnd = symbol.end() - symbol.start()
        changes.getOrPut(atom) { mutableListOf() } += Edit(0, declarationEnd,
            CmmReferences.renamed(atom.text.substring(0, declarationEnd), newName, symbol.macro()))
        // Replacing a string atom invalidates its other usage pointers. Capture every selected
        // edit first and replace each atom once, then notify the platform with the new declaration.
        // The complete preflight above also avoids checking an inconsistent, half-renamed model.
        val replacements = changes.map { (node, edits) ->
            val text = StringBuilder(node.text)
            for (edit in edits.distinctBy { it.start to it.end }.sortedByDescending { it.start })
                text.replace(edit.start, edit.end, edit.replacement)
            node to text.toString()
        }.sortedByDescending { it.first.textOffset }
        var renamedDeclaration: PsiElement = atom
        for ((node, replacement) in replacements) {
            val updated = CmmReferences.replaceText(node, 0, node.textLength, replacement)
            if (node == atom) renamedDeclaration = updated
        }
        listener?.elementRenamed(renamedDeclaration)
    }

    private fun conflict(element: PsiElement, newName: String): String? {
        val atom = element as? CmmParserDefinition.Atom ?: return null
        val symbol = atom.symbol() ?: return null
        return conflict(atom.containingFile.text, symbol.start(), newName)
    }

    companion object {
        private data class Edit(val start: Int, val end: Int, val replacement: String)

        /**
         * Simulate the full rename and compare bindings, rather than banning every equal name in
         * the file. Names in disjoint sibling blocks therefore remain legal. A direct setName call
         * checks declarations only, because the platform may already have renamed its references.
         */
        @JvmStatic @JvmOverloads
        fun conflict(text: String, targetOffset: Int, newName: String, checkReferences: Boolean = true): String? {
            val before = CmmModel.parse(text)
            val target = before.symbols().firstOrNull { it.start() == targetOffset } ?: return null
            val canonical = CmmModel.macroName(CmmReferences.renamed(target.name(), newName, target.macro()))
            if (canonical == target.name()) return null
            if (!target.macro() && before.symbols().any { !it.macro() && it.start() != targetOffset && it.name() == canonical })
                return "A label or subroutine named '$canonical' already exists in this script."

            val edits = mutableListOf(Edit(target.start(), target.end(),
                CmmReferences.renamed(text.substring(target.start(), target.end()), newName, target.macro())))
            if (checkReferences) for (reference in before.references()) {
                if (before.resolve(reference)?.start() == targetOffset)
                    edits += Edit(reference.start(), reference.end(),
                        CmmReferences.renamed(text.substring(reference.start(), reference.end()), newName, reference.macro()))
            }
            edits.sortBy { it.start }
            val updated = StringBuilder(text)
            for (edit in edits.asReversed()) updated.replace(edit.start, edit.end, edit.replacement)
            val after = CmmModel.parse(updated.toString())
            fun mapped(offset: Int) = offset + edits.filter { it.end <= offset }.sumOf { it.replacement.length - (it.end - it.start) }

            for (symbol in before.symbols()) {
                val expectedName = if (symbol.start() == targetOffset) canonical else symbol.name()
                if (after.symbols().none { it.start() == mapped(symbol.start()) && it.name() == expectedName && it.macro() == symbol.macro() })
                    return "Renaming to '$canonical' would merge declarations in the same scope."
            }
            if (checkReferences) for (reference in before.references()) {
                val original = before.resolve(reference)
                val renamedReference = original?.start() == targetOffset
                val name = if (renamedReference) canonical else reference.name()
                val start = mapped(reference.start())
                val resolved = after.resolve(CmmModel.Reference(start, start + name.length, name, reference.macro()))
                if (resolved?.start() != original?.let { mapped(it.start()) })
                    return "Renaming to '$canonical' would change the binding of '${reference.name()}' in this script."
            }
            return null
        }
    }
}
