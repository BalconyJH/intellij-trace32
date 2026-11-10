package io.github.balconyjh.trace32.cmm

import com.intellij.ide.structureView.*
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.psi.PsiFile
import io.github.balconyjh.trace32.cmm.core.CmmModel

class CmmStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        val file = psiFile as? CmmFile ?: return null
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel =
                StructureViewModelBase(file, editor, Node(file, null)).withSorters(Sorter.ALPHA_SORTER)
        }
    }
    class Node(private val file: CmmFile, private val symbol: CmmModel.Symbol?) : StructureViewTreeElement {
        override fun getValue(): Any = symbol?.let { file.findElementAt(it.start())?.parent } ?: file
        override fun getChildren(): Array<StructureViewTreeElement> = if (symbol != null) emptyArray() else
            file.model().symbols().distinctBy { it.start() }.map { Node(file, it) }.toTypedArray()
        override fun getPresentation() = object : ItemPresentation {
            override fun getPresentableText() = symbol?.name() ?: file.name
            override fun getLocationString() = symbol?.let { if (it.macro()) "macro" else "label / subroutine" }
            override fun getIcon(unused: Boolean) = CmmFileType.INSTANCE.icon
        }
        override fun navigate(requestFocus: Boolean) {
            val vf = file.virtualFile ?: return
            OpenFileDescriptor(file.project, vf, symbol?.start() ?: 0).navigate(requestFocus)
        }
        override fun canNavigate() = file.isValid && file.virtualFile != null
        override fun canNavigateToSource() = canNavigate()
    }
}
