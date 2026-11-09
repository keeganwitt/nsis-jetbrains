package dev.nsis.jetbrains.editor

import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import dev.nsis.jetbrains.NsisFileType

class NsisColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName() = "NSIS"
    override fun getIcon() = NsisFileType.icon
    override fun getHighlighter() = NsisSyntaxHighlighter()
    override fun getDemoText() = """
        ; Installer script
        !include "MUI2.nsh"
        !define APP "Example"
        Name "${'$'}{APP}"
        OutFile "setup.exe"
        Section "Install"
          SetOutPath "${'$'}INSTDIR"
          DetailPrint "Installing ${'$'}{APP}${'$'}\n"
        done:
        SectionEnd
    """.trimIndent()
    override fun getAdditionalHighlightingTagToDescriptorMap() = null
    override fun getColorDescriptors() = ColorDescriptor.EMPTY_ARRAY
    override fun getAttributeDescriptors() = arrayOf(
        AttributesDescriptor("Commands", NsisSyntaxHighlighter.COMMAND),
        AttributesDescriptor("Preprocessor directives", NsisSyntaxHighlighter.DIRECTIVE),
        AttributesDescriptor("Variables", NsisSyntaxHighlighter.VARIABLE),
        AttributesDescriptor("Defines and language strings", NsisSyntaxHighlighter.DEFINE),
        AttributesDescriptor("Labels", NsisSyntaxHighlighter.LABEL),
        AttributesDescriptor("Strings", NsisSyntaxHighlighter.STRING),
        AttributesDescriptor("Escapes", NsisSyntaxHighlighter.ESCAPE),
        AttributesDescriptor("Line comments", NsisSyntaxHighlighter.COMMENT),
        AttributesDescriptor("Block comments", NsisSyntaxHighlighter.BLOCK_COMMENT)
    )
}
