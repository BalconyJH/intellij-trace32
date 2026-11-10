package io.github.balconyjh.trace32.cmm.lsp

import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClientManager
import io.github.balconyjh.trace32.cmm.CmmSettings

/** Loaded through the optional LSP descriptor, never through the base settings service. */
class CmmLspSettingsListener(private val project: Project) : CmmSettings.Listener {
    override fun settingsChanged() {
        if (project.isDisposed) return
        val manager = LspClientManager.getInstance(project)
        if (CmmSettings.getInstance(project).lspEnabled) {
            manager.stopAndRestartClientsIfNeeded(CmmLspIntegrationProvider::class.java)
        } else {
            manager.stopClients(CmmLspIntegrationProvider::class.java)
        }
    }
}
