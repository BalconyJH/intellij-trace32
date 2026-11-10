package io.github.balconyjh.trace32.cmm

import com.intellij.lang.Language

class CmmLanguage private constructor() : Language("CMM") {
    override fun isCaseSensitive() = true
    companion object { @JvmField val INSTANCE = CmmLanguage() }
}
