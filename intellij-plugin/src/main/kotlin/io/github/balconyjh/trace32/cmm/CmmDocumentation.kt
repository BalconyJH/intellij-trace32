package io.github.balconyjh.trace32.cmm

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import io.github.balconyjh.trace32.cmm.core.CmmCatalog

/** Small authored signatures; descriptions come from our command catalog, not redistributed manuals. */
object CmmDocumentation {
    private val signatures = mapOf(
        "DO" to "DO <script> [<arguments>...]", "RUN" to "RUN <script> [<arguments>...]",
        "GOTO" to "GOTO <label>", "GOSUB" to "GOSUB <label> [<arguments>...]", "JUMPTO" to "JUMPTO <label>",
        "IF" to "IF <condition>", "WHILE" to "WHILE <condition>", "REPEAT" to "RePeaT [<count>]",
        "PRIVATE" to "PRIVATE <macro>...", "LOCAL" to "LOCAL <macro>...", "GLOBAL" to "GLOBAL <macro>...",
        "PARAMETERS" to "PARAMETERS <macro>...", "ENTRY" to "ENTRY <macro>...", "RETURNVALUES" to "RETURNVALUES <macro>...",
        "SUBROUTINE" to "SUBROUTINE <name>", "RETURN" to "RETURN [<values>...]", "ENDDO" to "ENDDO [<values>...]",
        "PRINT" to "PRINT [<format>] <values>...", "WAIT" to "WAIT <condition-or-duration> [<timeout>]",
        "ON" to "ON <event> <action>", "GLOBALON" to "GLOBALON <event> <action>",
        "SYSTEM.CPU" to "SYStem.CPU <cpu>", "SYSTEM.MODE" to "SYStem.Mode <mode>",
        "SYSTEM.JTAGCLOCK" to "SYStem.JtagClock <frequency>",
        "DATA.LOAD.ELF" to "Data.Load.Elf <file> [<address>] [/options]",
        "DATA.LOAD.BINARY" to "Data.Load.Binary <file> <address> [/options]",
        "DATA.SET" to "Data.Set <address-or-range> [<format>] <value>",
        "DATA.DUMP" to "Data.Dump <address-or-range>", "BREAK.SET" to "Break.Set <address-or-range> [/options]",
        "OS.PPD" to "OS.PPD()", "OS.PRESENTPRACTICEDIRECTORY" to "OS.PresentPracticeDirectory()",
        "OS.FILE" to "OS.FILE(<filename>)", "OS.ENV" to "OS.ENV(<name>)", "TRUE" to "TRUE()", "FALSE" to "FALSE()",
        "STRing.UPpeR".uppercase() to "STRing.UPpeR(<string>)", "STRING.LOWER" to "STRing.LOWER(<string>)",
        "STRING.LEN" to "STRing.LEN(<string>)", "STRING.LEFT" to "STRing.LEFT(<string>, <count>)",
        "STRING.RIGHT" to "STRing.RIGHT(<string>, <count>)", "STRING.MID" to "STRing.MID(<string>, <start>, <count>)"
    )
    private fun entry(name: String) = CmmCatalog.find(name)
    @JvmStatic fun canonical(name: String): String = CmmCatalog.canonicalName(name)
    @JvmStatic fun signature(name: String): String? = signatures[canonical(name).uppercase()]
    @JvmStatic fun options(name: String): List<String> = when (canonical(name).uppercase()) {
        "DATA.LOAD.ELF" -> listOf("/NoCODE", "/NoRegister", "/NoClear", "/CODEZERO")
        "BREAK.SET" -> listOf("/Program", "/Read", "/Write", "/Onchip")
        else -> emptyList()
    }
    @JvmStatic fun html(name: String): String? {
        val item = entry(name) ?: return null
        val syntax = signature(name) ?: (item.name() + if (item.function()) "(...)" else "")
        return "<div class='definition'><pre>${escape(syntax)}</pre></div><div class='content'>${escape(item.detail())}</div>" +
            "<div class='content'><a href='https://www.lauterbach.com/pdf/practice_ref.pdf'>PRACTICE reference</a> · " +
            "<a href='https://www.lauterbach.com/pdf/ide_ref.pdf'>Lauterbach TRACE32® command reference</a></div>"
    }
    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

class CmmDocumentationProvider : AbstractDocumentationProvider() {
    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val token = originalElement ?: element ?: return null
        if (token.containingFile !is CmmFile) return null
        return CmmDocumentation.html(token.text.trim()) ?: CmmDocumentation.html(token.parent?.text?.trim() ?: "")
    }
    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? = generateDoc(element, originalElement)
}
