package dev.nsis.jetbrains

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.icons.AllIcons
import com.intellij.psi.tree.IElementType
import javax.swing.Icon

object NsisLanguage : Language("NSIS")

object NsisFileType : LanguageFileType(NsisLanguage) {
    override fun getName() = "NSIS"
    override fun getDescription() = "NSIS installer script"
    override fun getDefaultExtension() = "nsi"
    override fun getIcon(): Icon = AllIcons.FileTypes.Config
}

class NsisElementType(name: String) : IElementType(name, NsisLanguage)
class NsisTokenType(name: String) : IElementType(name, NsisLanguage)

object NsisTokens {
    @JvmField val LINE_COMMENT = NsisTokenType("LINE_COMMENT")
    @JvmField val BLOCK_COMMENT = NsisTokenType("BLOCK_COMMENT")
    @JvmField val UNTERMINATED_COMMENT = NsisTokenType("UNTERMINATED_COMMENT")
    @JvmField val STRING_TEXT = NsisTokenType("STRING_TEXT")
    @JvmField val ESCAPE = NsisTokenType("ESCAPE")
    @JvmField val COMMAND = NsisTokenType("COMMAND")
    @JvmField val LABEL = NsisTokenType("LABEL")
}
