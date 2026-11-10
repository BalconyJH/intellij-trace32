package io.github.balconyjh.trace32.cmm

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import io.github.balconyjh.trace32.cmm.core.CmmScanner.Kind

object CmmTypes {
    @JvmField val FILE = IFileElementType(CmmLanguage.INSTANCE)
    @JvmField val ATOM = IElementType("ATOM", CmmLanguage.INSTANCE)
    private val types = Kind.values().associateWith {
        when (it) {
            Kind.WHITESPACE -> TokenType.WHITE_SPACE
            Kind.BAD_CHARACTER -> TokenType.BAD_CHARACTER
            else -> IElementType(it.name, CmmLanguage.INSTANCE)
        }
    }
    @JvmStatic fun of(kind: Kind): IElementType = types.getValue(kind)
}
