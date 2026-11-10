package io.github.balconyjh.trace32.cmm

import com.intellij.ide.hierarchy.*
import com.intellij.ide.util.treeView.AlphaComparator
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.util.CompositeAppearance
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import io.github.balconyjh.trace32.cmm.navigation.CmmNavigationData
import io.github.balconyjh.trace32.cmm.navigation.CmmNavigationIndex
import javax.swing.JTree

/** Script-level call hierarchy: DO/RUN edges only, without executing or guessing dynamic macros. */
class CmmCallHierarchyProvider : HierarchyProvider {
    override fun getTarget(context: DataContext): PsiElement? =
        (CommonDataKeys.PSI_FILE.getData(context) ?: CommonDataKeys.PSI_ELEMENT.getData(context)?.containingFile) as? CmmFile
    override fun createHierarchyBrowser(target: PsiElement): HierarchyBrowser = CmmCallHierarchyBrowser(target.containingFile as CmmFile)
    override fun browserActivated(browser: HierarchyBrowser) {
        (browser as? CmmCallHierarchyBrowser)?.changeView(CallHierarchyBrowserBase.getCallerType())
    }
}

class CmmCallHierarchyBrowser(file: CmmFile) : CallHierarchyBrowserBase(file.project, file) {
    override fun createTrees(trees: MutableMap<in String, in JTree>) {
        trees[getCallerType()] = createTree(false)
        trees[getCalleeType()] = createTree(false)
    }
    override fun isApplicableElement(element: PsiElement) = element.containingFile is CmmFile
    override fun getElementFromDescriptor(descriptor: HierarchyNodeDescriptor): PsiElement? = descriptor.psiElement
    override fun getOpenFileElementFromDescriptor(descriptor: HierarchyNodeDescriptor): PsiElement? {
        val node = descriptor as? CmmCallHierarchyNode ?: return descriptor.psiElement
        val file = node.psiElement as? CmmFile ?: return null
        return file.findElementAt(node.callOffset) ?: file
    }
    override fun createHierarchyTreeStructure(type: String, element: PsiElement): HierarchyTreeStructure? {
        val file = element.containingFile as? CmmFile ?: return null
        return CmmCallHierarchyTree(file, type == getCallerType(), currentScopeType)
    }
    override fun getComparator(): Comparator<NodeDescriptor<*>> = AlphaComparator.getInstance()
}

class CmmCallHierarchyNode(
    project: Project, parent: CmmCallHierarchyNode?, file: CmmFile,
    val callOffset: Int = 0, val recursive: Boolean = false
) : HierarchyNodeDescriptor(project, parent, file, parent == null) {
    override fun update(): Boolean {
        super.update()
        val file = psiElement as? CmmFile ?: return invalidElement()
        myName = file.name
        myHighlightedText = CompositeAppearance.single(file.name + if (recursive) " (recursive)" else "")
        myHighlightedText.ending.addComment(file.virtualFile?.parent?.presentableUrl ?: "")
        installIcon(file, myIsBase)
        return true
    }
}

class CmmCallHierarchyTree(file: CmmFile, private val callers: Boolean, private val scopeType: String) :
    HierarchyTreeStructure(file.project, CmmCallHierarchyNode(file.project, null, file)) {
    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> {
        val node = descriptor as? CmmCallHierarchyNode ?: return emptyArray()
        val file = node.psiElement as? CmmFile ?: return emptyArray()
        if (node.recursive || DumbService.isDumb(myProject)) return emptyArray()
        val ancestors = HashSet<VirtualFile>()
        var parent: NodeDescriptor<*>? = node
        while (parent != null) {
            (parent as? CmmCallHierarchyNode)?.psiElement?.containingFile?.virtualFile?.let(ancestors::add)
            parent = parent.parentDescriptor
        }
        val scope = getSearchScope(scopeType, file)
        val calls = if (callers) CmmCallHierarchyQueries.callers(file, scope as? GlobalSearchScope ?: GlobalSearchScope.projectScope(myProject))
                    else CmmCallHierarchyQueries.callees(file)
        return calls.mapNotNull { call ->
            ProgressManager.checkCanceled()
            val next = PsiManager.getInstance(myProject).findFile(call.file) as? CmmFile ?: return@mapNotNull null
            if (!com.intellij.psi.search.PsiSearchScopeUtil.isInScope(scope, next)) return@mapNotNull null
            CmmCallHierarchyNode(myProject, node, next, call.offset, call.file in ancestors)
        }.toTypedArray()
    }
}

object CmmCallHierarchyQueries {
    data class Call(val file: VirtualFile, val offset: Int)
    @JvmStatic fun callees(file: CmmFile): List<Call> {
        val source = file.virtualFile ?: return emptyList()
        val settings = CmmSettings.getInstance(file.project)
        return file.model().scriptPaths().mapNotNull { path ->
            ProgressManager.checkCanceled()
            resolve(source, path.path(), settings)?.let { Call(it, 0) }
        }.distinctBy { it.file }
    }
    @JvmStatic @JvmOverloads fun callers(file: CmmFile, scope: GlobalSearchScope = GlobalSearchScope.projectScope(file.project)): List<Call> {
        val target = file.virtualFile ?: return emptyList()
        if (DumbService.isDumb(file.project)) return emptyList()
        val settings = CmmSettings.getInstance(file.project)
        val result = LinkedHashMap<VirtualFile, Call>()
        FileBasedIndex.getInstance().processValues(CmmNavigationIndex.NAME, CmmNavigationData.CALLS_KEY, null,
            { source, entries ->
                for (entry in entries) {
                    ProgressManager.checkCanceled()
                    if (resolve(source, entry.value, settings) == target) {
                        result[source] = Call(source, entry.offset)
                        break
                    }
                }
                true
            }, scope)
        return result.values.toList()
    }
    private fun resolve(source: VirtualFile, value: String, settings: CmmSettings): VirtualFile? {
        val directory = source.parent?.path ?: return null
        val expanded = CmmNavigationData.expandedPath(directory, value, settings.systemDirectory, settings.temporaryDirectory) ?: return null
        val path = FileUtil.toCanonicalPath(expanded)
        val fs = VirtualFileManager.getInstance().getFileSystem("file")
        val target = fs.findFileByPath(path) ?: if (!path.endsWith(".cmm", true) && !path.endsWith(".cmmt", true)) fs.findFileByPath("$path.cmm") else null
        return target?.takeUnless { it.isDirectory }
    }
}
