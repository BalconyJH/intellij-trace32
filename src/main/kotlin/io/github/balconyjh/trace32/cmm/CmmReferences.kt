package io.github.balconyjh.trace32.cmm

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.util.IncorrectOperationException

object CmmReferences {
    @JvmStatic fun forElement(element: PsiElement): Array<PsiReference> {
        val file = element.containingFile as? CmmFile ?: return PsiReference.EMPTY_ARRAY
        val result = ArrayList<PsiReference>()
        for (path in file.model().scriptPaths()) {
            val range = intersection(element.textRange, path.start(), path.end()) ?: continue
            result += object : PsiReferenceBase<PsiElement>(element, range, true) {
                override fun resolve(): PsiElement? = resolvePath(file, path.path())
                override fun getVariants(): Array<Any> = emptyArray()
            }
        }
        for (ref in file.model().references()) {
            val range = intersection(element.textRange, ref.start(), ref.end()) ?: continue
            result += object : PsiReferenceBase<PsiElement>(element, range, true) {
                override fun resolve(): PsiElement? {
                    val symbol = file.model().resolve(ref) ?: return null
                    return file.findElementAt(symbol.start())?.parent
                }
                override fun getVariants(): Array<Any> = emptyArray()
                override fun handleElementRename(newElementName: String): PsiElement {
                    if (resolve() == null) return element
                    val old = range.substring(element.text)
                    return replaceText(element, range.startOffset, range.endOffset, renamed(old, newElementName, ref.macro()))
                }
            }
        }
        return result.toTypedArray()
    }
    private fun intersection(element: TextRange, start: Int, end: Int): TextRange? {
        val from = maxOf(element.startOffset, start)
        val to = minOf(element.endOffset, end)
        return if (from < to) TextRange(from - element.startOffset, to - element.startOffset) else null
    }
    @JvmStatic fun resolveVirtualPath(file: CmmFile, value: String): VirtualFile? {
        val path = expandedPath(file, value) ?: return null
        val fs = VirtualFileManager.getInstance().getFileSystem("file")
        return fs.findFileByPath(path) ?: if (!path.endsWith(".cmm", true) && !path.endsWith(".cmmt", true)) fs.findFileByPath("$path.cmm") else null
    }
    @JvmStatic fun resolvePath(file: CmmFile, path: String): PsiFile? =
        resolveVirtualPath(file, path)?.takeUnless { it.isDirectory }?.let { PsiManager.getInstance(file.project).findFile(it) }

    /** Expand only configured/static roots. Runtime macros remain unresolved. */
    @JvmStatic fun expandedPath(file: CmmFile, value: String): String? {
        if (value.contains('&')) return null
        val path = value.replace('\\', '/')
        val directory = file.originalFile.virtualFile?.parent?.path ?: return null
        return when {
            path == "~~~~" || path.startsWith("~~~~/") -> directory + path.removePrefix("~~~~")
            path == "~~~" || path.startsWith("~~~/") -> CmmSettings.getInstance(file.project).temporaryDirectory.takeIf { it.isNotBlank() }?.replace('\\', '/')?.trimEnd('/')?.plus(path.removePrefix("~~~"))
            path == "~~" || path.startsWith("~~/") -> CmmSettings.getInstance(file.project).systemDirectory.takeIf { it.isNotBlank() }?.replace('\\', '/')?.trimEnd('/')?.plus(path.removePrefix("~~"))
            path.startsWith('~') -> null
            path.startsWith('/') || Regex("^[A-Za-z]:/.*").matches(path) -> path
            else -> "$directory/$path"
        }
    }
    internal fun renamed(old: String, newName: String, macro: Boolean): String {
        val name = newName.removePrefix("&(").removeSuffix(")").removePrefix("&")
        if (!Regex("[A-Za-z_][A-Za-z0-9_]*").matches(name)) throw IncorrectOperationException("Expected a PRACTICE identifier")
        return if (!macro) name else if (old.startsWith("&(")) "&($name)" else "&$name"
    }
    internal fun replaceText(element: PsiElement, start: Int, end: Int, replacement: String): PsiElement {
        val newText = element.text.replaceRange(start, end, replacement)
        val copy = PsiFileFactory.getInstance(element.project).createFileFromText("rename.cmm", CmmFileType.INSTANCE, newText)
        val atom = copy.firstChild ?: throw IncorrectOperationException("Cannot parse replacement")
        return element.replace(atom)
    }
}
