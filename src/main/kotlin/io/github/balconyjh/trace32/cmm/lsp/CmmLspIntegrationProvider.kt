package io.github.balconyjh.trace32.cmm.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspFoldingRangeDisabled
import io.github.balconyjh.trace32.cmm.CmmSettings

class CmmLspIntegrationProvider : LspIntegrationProvider {
    override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
        val settings = CmmSettings.getInstance(project)
        if (settings.lspEnabled && settings.serverExecutable.isNotBlank() && supports(file)) {
            clientStarter.ensureClientStarted(CmmLspClientDescriptor(project))
        }
    }

    companion object {
        @JvmStatic
        fun supports(file: VirtualFile): Boolean = !file.isDirectory &&
            (file.extension.equals("cmm", true) || file.extension.equals("cmmt", true))
    }
}

class CmmLspClientDescriptor(project: Project) : ProjectWideLspClientDescriptor(project, "Lauterbach TRACE32® PRACTICE (t32ls)") {
    override fun isSupportedFile(file: VirtualFile): Boolean = CmmLspIntegrationProvider.supports(file)
    override fun getLanguageId(file: VirtualFile): String = "practice"

    override fun createCommandLine(): GeneralCommandLine {
        val settings = CmmSettings.getInstance(project)
        val command = GeneralCommandLine(settings.serverExecutable)
            .withParameters("--clientProcessId=${ProcessHandle.current().pid()}", "--trace=off")
        project.basePath?.let { command.withWorkDirectory(it) }
        if (settings.systemDirectory.isNotBlank()) command.addParameter("--t32SystemDir=${settings.systemDirectory}")
        if (settings.temporaryDirectory.isNotBlank()) command.addParameter("--t32TempDir=${settings.temporaryDirectory}")
        return command
    }

    override val lspCustomization: LspCustomization = object : LspCustomization() {
        // Native folding includes our error-tolerant block and comment rules; avoid duplicate regions.
        override val foldingRangeCustomizer = LspFoldingRangeDisabled
    }
}
