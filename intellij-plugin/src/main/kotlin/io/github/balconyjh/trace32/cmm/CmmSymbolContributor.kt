package io.github.balconyjh.trace32.cmm

import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import io.github.balconyjh.trace32.cmm.navigation.CmmNavigationData
import io.github.balconyjh.trace32.cmm.navigation.CmmNavigationIndex

/** Navigate Symbol reads the content index rather than loading every script's PSI. */
class CmmSymbolContributor : ChooseByNameContributorEx {
    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        val project = scope.project ?: return
        if (DumbService.isDumb(project)) return
        FileBasedIndex.getInstance().processAllKeys(CmmNavigationIndex.NAME, { key ->
            ProgressManager.checkCanceled()
            !key.startsWith(CmmNavigationData.SYMBOL_PREFIX) || processor.process(key.removePrefix(CmmNavigationData.SYMBOL_PREFIX))
        }, scope, filter)
    }

    override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
        val project = parameters.project
        if (DumbService.isDumb(project)) return
        FileBasedIndex.getInstance().processValues(CmmNavigationIndex.NAME, CmmNavigationData.SYMBOL_PREFIX + name, null,
            { file, entries ->
                for (entry in entries) {
                    ProgressManager.checkCanceled()
                    // Let the public consumer stop iteration; SDK cutoff notifications are internal.
                    if (!processor.process(Item(project, file, entry))) return@processValues false
                }
                true
            }, parameters.searchScope, parameters.idFilter)
    }

    class Item(private val project: Project, private val file: VirtualFile, private val entry: CmmNavigationData.Entry) : NavigationItem {
        override fun getName() = entry.value
        override fun getPresentation() = object : ItemPresentation {
            override fun getPresentableText() = entry.value
            override fun getLocationString() = file.presentableUrl + if (entry.macro) " · macro" else " · label / subroutine"
            override fun getIcon(unused: Boolean) = CmmFileType.INSTANCE.icon
        }
        override fun navigate(requestFocus: Boolean) { OpenFileDescriptor(project, file, entry.offset).navigate(requestFocus) }
        override fun canNavigate() = file.isValid && !project.isDisposed
        override fun canNavigateToSource() = canNavigate()
    }
}
