package dev.nsis.jetbrains.editor

import com.intellij.lexer.LayeredLexer
import com.intellij.lexer.LexerBase
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Colors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import dev.nsis.jetbrains.NsisLexer
import dev.nsis.jetbrains.NsisTokens
import dev.nsis.jetbrains.model.NsisCatalog
import dev.nsis.jetbrains.model.NsisInterpolations
import dev.nsis.jetbrains.model.SymbolKind
import dev.nsis.jetbrains.psi.NsisTypes
import java.util.Locale

class NsisHighlightLexer : LayeredLexer(object : NsisLexer() {
    override fun getTokenType(): IElementType? {
        val type = super.getTokenType()
        if (type == NsisTypes.WORD) {
            val text = tokenText
            if (text.endsWith(':') && !text.contains("::")) return NsisTokens.LABEL
            if (text.lowercase(Locale.ROOT) in NsisCatalog.commandNames) return NsisTokens.COMMAND
        }
        return type
    }
}) {
    init { registerSelfStoppingLayer(NsisStringLexer(), arrayOf(NsisTypes.STRING, NsisTypes.UNTERMINATED_STRING), emptyArray()) }
}

class NsisStringLexer : LexerBase() {
    private data class Piece(val start: Int, val end: Int, val type: IElementType)
    private var buffer: CharSequence = ""
    private var end = 0
    private var index = 0
    private var pieces = emptyList<Piece>()

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        end = endOffset
        index = 0
        val text = buffer.subSequence(startOffset, endOffset).toString()
        val special = NsisInterpolations.scan(text).map {
            val define = it.kind == SymbolKind.DEFINE
            Piece(startOffset + it.range.startOffset - if (define) 2 else 1,
                startOffset + it.range.endOffset + if (define) 1 else 0,
                if (define) NsisTypes.DEFINE_REF else NsisTypes.VARIABLE)
        } + Regex("\\$\\$|\\$\\\\.").findAll(text).map {
            Piece(startOffset + it.range.first, startOffset + it.range.last + 1, NsisTokens.ESCAPE)
        }.toList()
        pieces = buildList {
            var offset = startOffset
            for (piece in special.sortedBy { it.start }) {
                if (piece.start < offset) continue
                if (piece.start > offset) add(Piece(offset, piece.start, NsisTokens.STRING_TEXT))
                add(piece)
                offset = piece.end
            }
            if (offset < endOffset) add(Piece(offset, endOffset, NsisTokens.STRING_TEXT))
        }
    }
    override fun getState() = 0
    override fun getTokenType() = pieces.getOrNull(index)?.type
    override fun getTokenStart() = pieces.getOrNull(index)?.start ?: end
    override fun getTokenEnd() = pieces.getOrNull(index)?.end ?: end
    override fun advance() { index++ }
    override fun getBufferSequence() = buffer
    override fun getBufferEnd() = end
}

class NsisSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer() = NsisHighlightLexer()
    override fun getTokenHighlights(type: IElementType): Array<TextAttributesKey> = pack(when (type) {
        NsisTokens.COMMAND -> COMMAND
        NsisTypes.DIRECTIVE -> DIRECTIVE
        NsisTypes.VARIABLE -> VARIABLE
        NsisTypes.DEFINE_REF, NsisTypes.LANG_REF -> DEFINE
        NsisTokens.LABEL -> LABEL
        NsisTypes.STRING, NsisTypes.UNTERMINATED_STRING, NsisTokens.STRING_TEXT -> STRING
        NsisTokens.ESCAPE -> ESCAPE
        NsisTokens.LINE_COMMENT -> COMMENT
        NsisTokens.BLOCK_COMMENT, NsisTokens.UNTERMINATED_COMMENT -> BLOCK_COMMENT
        else -> null
    })

    companion object {
        val COMMAND = TextAttributesKey.createTextAttributesKey("NSIS.COMMAND", Colors.KEYWORD)
        val DIRECTIVE = TextAttributesKey.createTextAttributesKey("NSIS.DIRECTIVE", Colors.METADATA)
        val VARIABLE = TextAttributesKey.createTextAttributesKey("NSIS.VARIABLE", Colors.INSTANCE_FIELD)
        val DEFINE = TextAttributesKey.createTextAttributesKey("NSIS.DEFINE", Colors.CONSTANT)
        val LABEL = TextAttributesKey.createTextAttributesKey("NSIS.LABEL", Colors.LABEL)
        val STRING = TextAttributesKey.createTextAttributesKey("NSIS.STRING", Colors.STRING)
        val ESCAPE = TextAttributesKey.createTextAttributesKey("NSIS.ESCAPE", Colors.VALID_STRING_ESCAPE)
        val COMMENT = TextAttributesKey.createTextAttributesKey("NSIS.COMMENT", Colors.LINE_COMMENT)
        val BLOCK_COMMENT = TextAttributesKey.createTextAttributesKey("NSIS.BLOCK_COMMENT", Colors.BLOCK_COMMENT)
    }
}

class NsisSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) = NsisSyntaxHighlighter()
}
