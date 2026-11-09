package dev.nsis.jetbrains.editor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.util.PsiTreeUtil
import dev.nsis.jetbrains.NsisFile
import dev.nsis.jetbrains.NsisTokens
import dev.nsis.jetbrains.model.NsisCatalog
import dev.nsis.jetbrains.model.NsisIncludes
import dev.nsis.jetbrains.model.NsisIdentifiers
import dev.nsis.jetbrains.model.NsisModel
import dev.nsis.jetbrains.model.NsisReferenceKinds
import dev.nsis.jetbrains.model.SymbolKind
import dev.nsis.jetbrains.psi.NsisStatement

class NsisCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.originalFile as? NsisFile ?: return
        val offset = parameters.offset.coerceAtMost(file.textLength)
        val leaf = file.findElementAt((offset - 1).coerceAtLeast(0))
        if (leaf?.node?.elementType in setOf(NsisTokens.LINE_COMMENT, NsisTokens.BLOCK_COMMENT, NsisTokens.UNTERMINATED_COMMENT)) return
        val before = file.text.substring(0, offset)
        val define = Regex("\\$\\{([A-Za-z_0-9.]*)$").find(before)
        val variable = Regex("(?<!\\$)\\$([${NsisIdentifiers.VARIABLE_CHARACTERS}]*)$").find(before)
        val symbols by lazy { NsisIncludes.graph(file).symbols(file, offset) }
        fun offer(names: Iterable<String>, prefix: String, type: String) {
            val matching = result.withPrefixMatcher(prefix)
            for (name in names.distinct()) {
                ProgressManager.checkCanceled()
                matching.addElement(LookupElementBuilder.create(name).withTypeText(type, true))
            }
        }
        if (define != null) {
            offer(NsisCatalog.defines + symbols.filter { it.kind == SymbolKind.DEFINE }.map { it.name }, define.groupValues[1], "define")
            return
        }
        if (variable != null) {
            offer(NsisCatalog.variables.map { it.drop(1) } + symbols.filter { it.kind == SymbolKind.VARIABLE }.map { it.name }, variable.groupValues[1], "variable")
            return
        }
        val copiedStatement = PsiTreeUtil.getParentOfType(parameters.position, NsisStatement::class.java)
        val copiedFile = copiedStatement?.containingFile as? NsisFile ?: return
        val line = NsisModel.get(copiedFile).lineAt(parameters.position.textOffset) ?: return
        val argumentIndex = line.arguments.indexOfFirst { parameters.position.textOffset in it.range.startOffset until it.range.endOffset }
        if (argumentIndex <= 0) {
            val prefix = before.takeLastWhile { !it.isWhitespace() }
            offer(if (prefix.startsWith('!')) NsisCatalog.directives else NsisCatalog.commands + NsisCatalog.directives, prefix, "NSIS")
            return
        }
        val kind = NsisReferenceKinds.operandKind(line, argumentIndex - 1) ?: return
        val prefix = before.takeLastWhile { !it.isWhitespace() && it !in charArrayOf('"', '\'', '`', ':') }
        offer(symbols.filter { it.kind == kind }.map { it.name }, prefix, kind.name.lowercase())
    }
}
