package io.github.balconyjh.trace32.cmm

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import io.github.balconyjh.trace32.cmm.core.CmmModel

class CmmFile(provider: FileViewProvider) : PsiFileBase(provider, CmmLanguage.INSTANCE) {
    override fun getFileType() = CmmFileType.INSTANCE
    fun model(): CmmModel = CachedValuesManager.getCachedValue(this) {
        CachedValueProvider.Result.create(CmmModel.parse(text), this)
    }
    override fun toString() = "PRACTICE script: $name"
}
