package dev.nsis.jetbrains.editor

import com.intellij.psi.tree.IElementType
import com.intellij.psi.TokenType
import dev.nsis.jetbrains.NsisFile
import dev.nsis.jetbrains.NsisLexer
import dev.nsis.jetbrains.NsisTokens
import dev.nsis.jetbrains.model.NsisArgument
import dev.nsis.jetbrains.model.NsisCommandInfo
import dev.nsis.jetbrains.model.NsisCommandMetadata
import dev.nsis.jetbrains.model.NsisLine
import dev.nsis.jetbrains.model.NsisModel
import dev.nsis.jetbrains.psi.NsisTypes

data class NsisArgumentContext(
    val line: NsisLine,
    val operandIndex: Int,
    val argument: NsisArgument?,
    val prefix: String,
    val info: NsisCommandInfo?,
    val semanticIndex: Int,
    val switchIndex: Int?,
    val switchValue: Boolean
) {
    val signatureIndex: Int get() = info?.signatureIndex(semanticIndex, switchIndex) ?: -1
    val quoted: Boolean get() = argument?.raw?.firstOrNull() in quotes

    companion object {
        val quotes = setOf('"', '\'', '`')
        private val comments: Set<IElementType> = setOf(NsisTokens.LINE_COMMENT, NsisTokens.BLOCK_COMMENT, NsisTokens.UNTERMINATED_COMMENT)

        fun at(file: NsisFile, offset: Int): NsisArgumentContext? {
            if (offset !in 0..file.textLength) return null
            val leaf = file.findElementAt(offset)
            val previous = file.findElementAt(offset - 1)
            if (leaf?.node?.elementType in comments || previous?.node?.elementType in comments &&
                (previous?.textRange?.endOffset == offset && (leaf == null || previous.node.elementType == NsisTokens.LINE_COMMENT))) return null
            val line = NsisModel.get(file).lines.lastOrNull { it.offset <= offset } ?: return null
            val last = line.element.textRange.endOffset
            if (offset > last) {
                val gap = file.text.substring(last, offset)
                val lexer = NsisLexer()
                lexer.start(gap)
                while (lexer.tokenType != null) {
                    if (lexer.tokenType != TokenType.WHITE_SPACE) return null
                    lexer.advance()
                }
            }
            if (leaf?.node?.elementType == NsisTypes.NEWLINE && offset > last) return null
            val operandIndex = line.operands.indexOfFirst { offset in it.range.startOffset..it.range.endOffset }
                .takeIf { it >= 0 } ?: line.operands.count { it.range.endOffset < offset }
            if (offset <= line.arguments.first().range.endOffset) return null
            val argument = line.operands.getOrNull(operandIndex)?.takeIf { offset in it.range.startOffset..it.range.endOffset }
            val rawPrefix = argument?.let { file.text.substring(it.range.startOffset, offset.coerceAtMost(it.range.endOffset)) }.orEmpty()
            val prefix = if (rawPrefix.firstOrNull() in quotes) rawPrefix.drop(1) else rawPrefix
            val info = NsisCommandMetadata.find(line.command)?.forOperands(line.operands)
            var semanticIndex = 0
            var pendingSwitch: Int? = null
            for (operand in line.operands.take(operandIndex)) {
                if (pendingSwitch != null) { pendingSwitch = null; continue }
                val index = info?.switches?.indexOfFirst { it.afterOperands == semanticIndex && it.matches(operand.value) } ?: -1
                if (index >= 0) {
                    if (info!!.switches[index].takesValue) pendingSwitch = index
                } else semanticIndex++
            }
            val currentSwitch = if (pendingSwitch == null) info?.switches?.indexOfFirst {
                it.afterOperands == semanticIndex && it.matches(argument?.value.orEmpty())
            }?.takeIf { it >= 0 } else null
            return NsisArgumentContext(line, operandIndex, argument, prefix, info, semanticIndex, pendingSwitch ?: currentSwitch, pendingSwitch != null)
        }
    }
}
