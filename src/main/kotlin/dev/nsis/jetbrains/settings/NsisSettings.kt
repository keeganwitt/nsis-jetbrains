package dev.nsis.jetbrains.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.GridLayout
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JComponent
import javax.swing.JPanel

@Service(Service.Level.PROJECT)
@State(name = "NsisSettings", storages = [Storage("nsis.xml")])
class NsisSettings : PersistentStateComponent<NsisSettings.Data>, ModificationTracker {
    data class Data(var includeDirectories: MutableList<String> = mutableListOf(), var sdkDirectory: String = "")

    @Volatile private var data = Data()
    private val changes = AtomicLong()

    override fun getState() = data.copy(includeDirectories = data.includeDirectories.toMutableList())
    override fun loadState(state: Data) {
        data = state.copy(includeDirectories = state.includeDirectories.toMutableList())
        changes.incrementAndGet()
    }
    override fun getModificationCount() = changes.get()

    fun update(includeDirectories: List<String>, sdkDirectory: String) = loadState(Data(
        includeDirectories.map { it.trim() }.filter { it.isNotEmpty() }.toMutableList(), sdkDirectory.trim()
    ))

    companion object {
        fun get(project: Project): NsisSettings = project.service()
    }
}

class NsisConfigurable(private val project: Project) : Configurable {
    private var directories: JBTextArea? = null
    private var sdk: JBTextField? = null

    override fun getDisplayName() = "NSIS"
    override fun createComponent(): JComponent {
        val roots = JBTextArea(8, 60)
        val sdkInput = JBTextField()
        directories = roots
        sdk = sdkInput
        return JPanel(BorderLayout(0, 12)).apply {
            add(JPanel(GridLayout(0, 1, 0, 6)).apply {
                add(JBLabel("Optional NSIS SDK directory (its Include folder is searched last):"))
                add(sdkInput)
                add(JBLabel("Include directories, in search order (one per line; relative to project):"))
            }, BorderLayout.NORTH)
            add(JBScrollPane(roots), BorderLayout.CENTER)
            add(JBLabel("Static navigation only. Scripts and preprocessor commands are never executed."), BorderLayout.SOUTH)
            reset()
        }
    }
    override fun isModified(): Boolean {
        val state = NsisSettings.get(project).state
        return enteredDirectories() != state.includeDirectories || sdk?.text.orEmpty().trim() != state.sdkDirectory
    }
    override fun apply() { NsisSettings.get(project).update(enteredDirectories(), sdk?.text.orEmpty()) }
    override fun reset() {
        val state = NsisSettings.get(project).state
        directories?.text = state.includeDirectories.joinToString("\n")
        sdk?.text = state.sdkDirectory
    }
    override fun disposeUIResources() { directories = null; sdk = null }
    private fun enteredDirectories() = directories?.text.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
}
