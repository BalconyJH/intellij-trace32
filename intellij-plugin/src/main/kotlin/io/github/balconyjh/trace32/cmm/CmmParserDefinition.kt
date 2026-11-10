package io.github.balconyjh.trace32.cmm

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.tree.TokenSet
import io.github.balconyjh.trace32.cmm.core.CmmScanner.Kind

class CmmParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?) = CmmLexer()
    override fun createParser(project: Project?) = PsiParser { root, builder ->
        val file = builder.mark()
        while (!builder.eof()) {
            val atom = builder.mark()
            builder.advanceLexer()
            atom.done(CmmTypes.ATOM)
        }
        file.done(root)
        builder.treeBuilt
    }
    override fun getFileNodeType() = CmmTypes.FILE
    override fun getCommentTokens() = TokenSet.create(CmmTypes.of(Kind.COMMENT))
    override fun getStringLiteralElements() = TokenSet.create(CmmTypes.of(Kind.STRING))
    override fun createElement(node: ASTNode) = Atom(node)
    override fun createFile(viewProvider: FileViewProvider) = CmmFile(viewProvider)

    class Atom(node: ASTNode) : ASTWrapperPsiElement(node), PsiNameIdentifierOwner {
        override fun getReferences() = CmmReferences.forElement(this)
        override fun getReference(): PsiReference? = references.singleOrNull()
        fun symbol() = (containingFile as? CmmFile)?.model()?.symbols()?.firstOrNull { it.start() == textRange.startOffset }
        override fun getName(): String? = symbol()?.name()
        override fun getNameIdentifier(): PsiElement? = if (symbol() == null) null else firstChild
        override fun setName(name: String): PsiElement {
            val symbol = symbol() ?: return this
            CmmRenameProcessor.conflict(containingFile.text, symbol.start(), name, false)?.let {
                throw com.intellij.util.IncorrectOperationException(it)
            }
            val replacement = CmmReferences.renamed(text.substring(0, symbol.end() - symbol.start()), name, symbol.macro())
            return CmmReferences.replaceText(this, 0, symbol.end() - symbol.start(), replacement)
        }
        override fun getUseScope() = LocalSearchScope(containingFile)
    }
}
