package io.github.balconyjh.trace32.cmm

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader

class CmmFileType private constructor() : LanguageFileType(CmmLanguage.INSTANCE) {
    override fun getName() = "Lauterbach TRACE32® PRACTICE"
    override fun getDescription() = "Lauterbach TRACE32® PRACTICE script"
    override fun getDefaultExtension() = "cmm"
    override fun getIcon() = IconLoader.getIcon("/icons/cmm.svg", CmmFileType::class.java)
    companion object { @JvmField val INSTANCE = CmmFileType() }
}
