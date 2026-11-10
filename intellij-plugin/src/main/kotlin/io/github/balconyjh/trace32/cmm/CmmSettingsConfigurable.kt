package io.github.balconyjh.trace32.cmm

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class CmmSettingsConfigurable(private val project: Project) : Configurable {
    private var panel: JPanel? = null
    private var enabled: JCheckBox? = null
    private var executable: TextFieldWithBrowseButton? = null
    private var system: TextFieldWithBrowseButton? = null
    private var temporary: TextFieldWithBrowseButton? = null

    override fun getDisplayName(): String = "Lauterbach TRACE32®"

    override fun createComponent(): JComponent {
        panel?.let { return it }
        val fields = JPanel(GridBagLayout())
        val toggle = JCheckBox("Enable t32ls language server for cross-file navigation and references")
        val server = pathField(false)
        val systemPath = pathField(true)
        val temporaryPath = pathField(true)
        enabled = toggle
        executable = server
        system = systemPath
        temporary = temporaryPath
        fun row(index: Int, label: String, field: JComponent) {
            fields.add(JLabel(label), GridBagConstraints().apply {
                gridx = 0; gridy = index; anchor = GridBagConstraints.WEST; insets = Insets(5, 0, 5, 10)
            })
            fields.add(field, GridBagConstraints().apply {
                gridx = 1; gridy = index; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
                insets = Insets(5, 0, 5, 0)
            })
        }
        fields.add(toggle, GridBagConstraints().apply {
            gridx = 0; gridy = 0; gridwidth = 2; anchor = GridBagConstraints.WEST
            insets = Insets(0, 0, 10, 0)
        })
        row(1, "t32ls executable:", server)
        row(2, "Lauterbach TRACE32® system directory (~~):", systemPath)
        row(3, "Lauterbach TRACE32® temporary directory (~~~):", temporaryPath)
        fields.add(JLabel("<html>The server runs locally. Native completion and editing work without it.<br>" +
            "Install t32ls with tools/t32ls-install.ps1; select the resulting executable here.<br>" +
            "Directory settings also apply to native script navigation and path completion.</html>"), GridBagConstraints().apply {
            gridx = 0; gridy = 4; gridwidth = 2; anchor = GridBagConstraints.WEST
            insets = Insets(12, 0, 0, 0)
        })
        toggle.addActionListener { server.isEnabled = toggle.isSelected }
        return JPanel(BorderLayout()).also { result ->
            result.add(fields, BorderLayout.NORTH)
            panel = result
            reset()
        }
    }

    private fun pathField(directory: Boolean): TextFieldWithBrowseButton = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(project, if (directory) FileChooserDescriptorFactory.singleDir() else FileChooserDescriptorFactory.singleFile())
    }

    override fun isModified(): Boolean {
        if (panel == null) return false
        val settings = CmmSettings.getInstance(project)
        return enabled!!.isSelected != settings.lspEnabled || executable!!.text.trim() != settings.serverExecutable ||
            system!!.text.trim() != settings.systemDirectory || temporary!!.text.trim() != settings.temporaryDirectory
    }

    override fun apply() {
        if (panel == null) return
        val serverPath = executable!!.text.trim()
        val systemPath = system!!.text.trim()
        val temporaryPath = temporary!!.text.trim()
        if (enabled!!.isSelected && !validPath(serverPath, false))
            throw ConfigurationException("Select an existing t32ls executable file.")
        if (systemPath.isNotEmpty() && !validPath(systemPath, true))
            throw ConfigurationException("The Lauterbach TRACE32® system directory does not exist.")
        if (temporaryPath.isNotEmpty() && !validPath(temporaryPath, true))
            throw ConfigurationException("The Lauterbach TRACE32® temporary directory does not exist.")
        if (!isModified()) return
        CmmSettings.getInstance(project).apply {
            lspEnabled = enabled!!.isSelected
            serverExecutable = serverPath
            systemDirectory = systemPath
            temporaryDirectory = temporaryPath
        }
        // A listener registered only in cmm-lsp.xml keeps the base plugin independent of the LSP module.
        project.messageBus.syncPublisher(CmmSettings.TOPIC).settingsChanged()
    }

    private fun validPath(value: String, directory: Boolean): Boolean = try {
        value.isNotBlank() && if (directory) Files.isDirectory(Path.of(value)) else Files.isRegularFile(Path.of(value))
    } catch (_: InvalidPathException) { false }

    override fun reset() {
        if (panel == null) return
        val settings = CmmSettings.getInstance(project)
        enabled!!.isSelected = settings.lspEnabled
        executable!!.text = settings.serverExecutable
        executable!!.isEnabled = settings.lspEnabled
        system!!.text = settings.systemDirectory
        temporary!!.text = settings.temporaryDirectory
    }

    override fun disposeUIResources() {
        executable?.let { Disposer.dispose(it) }
        system?.let { Disposer.dispose(it) }
        temporary?.let { Disposer.dispose(it) }
        panel = null
        enabled = null
        executable = null
        system = null
        temporary = null
    }
}
