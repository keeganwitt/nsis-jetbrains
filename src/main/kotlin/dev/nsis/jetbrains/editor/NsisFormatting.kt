package dev.nsis.jetbrains.editor

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.formatting.Wrap
import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable
import com.intellij.psi.formatter.DocumentBasedFormattingModel
import dev.nsis.jetbrains.NsisLanguage
import dev.nsis.jetbrains.NsisLexer
import dev.nsis.jetbrains.NsisTokens
import dev.nsis.jetbrains.psi.NsisTypes
import java.util.Locale

class NsisCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
    override fun getLanguage() = NsisLanguage
    override fun getCodeSample(settingsType: SettingsType) = "Section Install\n  SetOutPath \"${'$'}INSTDIR\"\nSectionEnd\n"
    override fun getIndentOptionsEditor() = IndentOptionsEditor(this)
    override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
        if (settingsType == SettingsType.INDENT_SETTINGS) consumer.showStandardOptions("INDENT_SIZE", "TAB_SIZE", "USE_TAB_CHARACTER")
    }
    override fun createConfigurable(settings: CodeStyleSettings, modelSettings: CodeStyleSettings) =
        object : CodeStyleAbstractConfigurable(settings, modelSettings, "NSIS") {
            override fun createPanel(settings: CodeStyleSettings) = object : TabbedLanguageCodeStylePanel(NsisLanguage, settings, modelSettings) {
                override fun initTabs(settings: CodeStyleSettings) { addIndentOptionsTab(settings) }
            }
        }
}

class NsisFormattingModelBuilder : FormattingModelBuilder {
    override fun createModel(context: FormattingContext) = object : DocumentBasedFormattingModel(
        NsisFormatRoot(context.containingFile.text, context.codeStyleSettings.getIndentOptionsByFile(context.containingFile).INDENT_SIZE),
        context.codeStyleSettings,
        context.containingFile
    ) {
        override fun shiftIndentInsideRange(node: ASTNode?, range: TextRange, indent: Int) = range
    }
}

private open class NsisFormatBlock(
    private val range: TextRange,
    private val indentation: Int = 0,
    private val children: List<Block> = emptyList(),
    private val followingIndent: Int = indentation
) : Block {
    override fun getTextRange() = range
    override fun getSubBlocks() = children
    override fun getWrap(): Wrap? = null
    override fun getAlignment(): Alignment? = null
    override fun getIndent(): Indent = Indent.getSpaceIndent(indentation)
    override fun getSpacing(child1: Block?, child2: Block): Spacing? = null
    override fun getChildAttributes(newChildIndex: Int) = ChildAttributes(Indent.getSpaceIndent(followingIndent), null)
    override fun isIncomplete() = false
    override fun isLeaf() = children.isEmpty()
}

private class NsisFormatRoot(private val source: String, private val indentSize: Int) : NsisFormatBlock(TextRange(0, source.length)) {
    private val lines = buildLines()
    override fun getSubBlocks(): List<Block> = lines
    override fun isLeaf() = lines.isEmpty()
    override fun getSpacing(child1: Block?, child2: Block): Spacing? =
        if (child1 == null) null else Spacing.createSpacing(0, 0, 1, true, source.length)
    override fun getChildAttributes(newChildIndex: Int): ChildAttributes =
        lines.getOrNull(newChildIndex - 1)?.getChildAttributes(0) ?: ChildAttributes(Indent.getNoneIndent(), null)

    private fun buildLines(): List<Block> {
        val lexer = NsisLexer()
        lexer.start(source)
        val stack = mutableListOf<String>()
        val result = mutableListOf<Block>()
        val tokens = mutableListOf<TextRange>()
        var protected = false
        var firstCommand: String? = null
        fun flush() {
            if (tokens.isEmpty()) return
            val command = firstCommand.orEmpty()
            if (command in cases || command in setOf("${'$'}{endselect}", "${'$'}{endswitch}")) {
                if (stack.lastOrNull() == "<case>") stack.removeAt(stack.lastIndex)
            }
            val ending = when {
                command in setOf("${'$'}{loopwhile}", "${'$'}{loopuntil}") -> "${'$'}{loop}"
                command in alternatives -> alternatives.getValue(command)
                command in openings.values -> command
                else -> null
            }
            if (ending != null) {
                val index = stack.indexOfLast { it == ending }
                if (index >= 0) while (stack.size > index) stack.removeAt(stack.lastIndex)
            }
            val level = stack.size
            if (command in alternatives) stack.add(alternatives.getValue(command))
            else openings[command]?.let { stack.add(it) }
            if (command in cases) stack.add("<case>")
            val range = TextRange(tokens.first().startOffset, tokens.last().endOffset)
            val children = if (protected) emptyList() else tokens.map { NsisFormatBlock(it) }
            result.add(object : NsisFormatBlock(range, level * indentSize, children, stack.size * indentSize) {
                override fun getSpacing(child1: Block?, child2: Block): Spacing? {
                    if (child1 == null) return null
                    val gap = source.substring(child1.textRange.endOffset, child2.textRange.startOffset)
                    val spaces = if (gap.isEmpty()) 0 else 1
                    return Spacing.createSpacing(spaces, spaces, 0, true, 0)
                }
            })
            tokens.clear()
            protected = false
            firstCommand = null
        }
        while (lexer.tokenType != null) {
            ProgressManager.checkCanceled()
            val type = lexer.tokenType
            val range = TextRange(lexer.tokenStart, lexer.tokenEnd)
            val text = range.substring(source)
            when {
                type == NsisTypes.NEWLINE -> flush()
                type != TokenType.WHITE_SPACE || text.any { !it.isWhitespace() } -> {
                    tokens.add(range)
                    if (firstCommand == null && type !in comments && type != TokenType.WHITE_SPACE) firstCommand = text.lowercase(Locale.ROOT)
                    if ('\n' in text || '\r' in text || type == NsisTokens.BLOCK_COMMENT || type == NsisTokens.UNTERMINATED_COMMENT) protected = true
                }
            }
            lexer.advance()
        }
        flush()
        return result
    }

    companion object {
        private val comments = setOf(NsisTokens.LINE_COMMENT, NsisTokens.BLOCK_COMMENT, NsisTokens.UNTERMINATED_COMMENT)
        private val openings = mapOf(
            "function" to "functionend", "section" to "sectionend", "sectiongroup" to "sectiongroupend",
            "pageex" to "pageexend", "!macro" to "!macroend", "!if" to "!endif", "!ifdef" to "!endif",
            "!ifndef" to "!endif", "!ifmacrodef" to "!endif", "!ifmacrondef" to "!endif",
            "${'$'}{if}" to "${'$'}{endif}", "${'$'}{ifnot}" to "${'$'}{endif}", "${'$'}{unless}" to "${'$'}{endif}",
            "${'$'}{select}" to "${'$'}{endselect}", "${'$'}{switch}" to "${'$'}{endswitch}",
            "${'$'}{while}" to "${'$'}{endwhile}", "${'$'}{for}" to "${'$'}{next}", "${'$'}{foreach}" to "${'$'}{next}",
            "${'$'}{do}" to "${'$'}{loop}", "${'$'}{dowhile}" to "${'$'}{loop}", "${'$'}{dountil}" to "${'$'}{loop}"
        )
        private val alternatives = mapOf(
            "!else" to "!endif", "${'$'}{else}" to "${'$'}{endif}", "${'$'}{elseif}" to "${'$'}{endif}",
            "${'$'}{elseifnot}" to "${'$'}{endif}", "${'$'}{andif}" to "${'$'}{endif}", "${'$'}{andifnot}" to "${'$'}{endif}",
            "${'$'}{orif}" to "${'$'}{endif}", "${'$'}{orifnot}" to "${'$'}{endif}"
        )
        private val cases = setOf("${'$'}{case}", "${'$'}{caseelse}", "${'$'}{default}")
    }
}
