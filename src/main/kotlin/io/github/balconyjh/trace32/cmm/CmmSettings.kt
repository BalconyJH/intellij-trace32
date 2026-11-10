package io.github.balconyjh.trace32.cmm

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

/** Project paths also serve native completion/navigation when the optional LSP is disabled. */
@Service(Service.Level.PROJECT)
@State(name = "Trace32CmmSettings", storages = [Storage("trace32-cmm.xml")])
class CmmSettings : PersistentStateComponent<CmmSettings.Data> {
    data class Data(
        var lspEnabled: Boolean = false,
        var serverExecutable: String = "",
        var systemDirectory: String = "",
        var temporaryDirectory: String = ""
    )

    private var data = Data()

    var lspEnabled: Boolean
        get() = data.lspEnabled
        set(value) { data.lspEnabled = value }
    var serverExecutable: String
        get() = data.serverExecutable
        set(value) { data.serverExecutable = value }
    var systemDirectory: String
        get() = data.systemDirectory
        set(value) { data.systemDirectory = value }
    var temporaryDirectory: String
        get() = data.temporaryDirectory
        set(value) { data.temporaryDirectory = value }

    override fun getState(): Data = data
    override fun loadState(state: Data) { data = state.copy() }

    fun interface Listener {
        fun settingsChanged()
    }

    companion object {
        @JvmField
        val TOPIC: Topic<Listener> = Topic.create("Lauterbach TRACE32® settings changed", Listener::class.java)

        @JvmStatic
        fun getInstance(project: Project): CmmSettings = project.getService(CmmSettings::class.java)
    }
}
