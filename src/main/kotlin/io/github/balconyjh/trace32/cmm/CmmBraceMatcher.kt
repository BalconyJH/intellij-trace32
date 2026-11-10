package io.github.balconyjh.trace32.cmm

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import io.github.balconyjh.trace32.cmm.core.CmmScanner.Kind

class CmmBraceMatcher : PairedBraceMatcher {
    override fun getPairs() = arrayOf(BracePair(CmmTypes.of(Kind.LPAREN), CmmTypes.of(Kind.RPAREN), false))
    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?) = true
    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int) = openingBraceOffset
}
