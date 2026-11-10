package dev.nsis.jetbrains.editor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.util.PsiTreeUtil
import dev.nsis.jetbrains.NsisFile
import dev.nsis.jetbrains.NsisTokens
import dev.nsis.jetbrains.model.NsisCatalog
import dev.nsis.jetbrains.model.NsisCommandMetadata
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
                val item = if (type == "NSIS") LookupElementBuilder.create(NsisCommandLookup(name), name).withCaseSensitivity(false)
                    .withTailText(NsisCommandMetadata.find(name)?.signature?.removePrefix(name).orEmpty(), true)
                else LookupElementBuilder.create(name)
                matching.addElement(item.withTypeText(type, true))
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
        val argument = NsisArgumentContext.at(file, offset)
        if (argument != null) {
            NsisIncludeCompletion.complete(file, offset, argument, result)
            completeArguments(argument, result)
        }
        val kind = NsisReferenceKinds.operandKind(line, argumentIndex - 1) ?: return
        val prefix = before.takeLastWhile { !it.isWhitespace() && it !in charArrayOf('"', '\'', '`', ':') }
        offer(symbols.filter { it.kind == kind }.map { it.name }, prefix, kind.name.lowercase())
    }

    private fun completeArguments(argument: NsisArgumentContext, result: CompletionResultSet) {
        val info = argument.info ?: return
        val prefix = argument.prefix
        fun offer(names: List<String>, matchingPrefix: String = prefix) {
            val matching = result.withPrefixMatcher(matchingPrefix)
            for (name in names.distinct()) {
                ProgressManager.checkCanceled()
                matching.addElement(LookupElementBuilder.create(name).withCaseSensitivity(false).withTypeText("NSIS argument", true)
                    .withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE))
            }
        }
        if (argument.switchValue) {
            offer(info.switches[argument.switchIndex!!].choices)
            return
        }
        val switch = argument.switchIndex?.let { info.switches[it] }
        if (switch != null) {
            if (switch.name.endsWith('=')) offer(switch.choices.map { switch.name + it })
            return
        }
        if (!argument.quoted && (prefix.isEmpty() || prefix.startsWith('/'))) {
            val available = info.switches.filter { it.afterOperands == argument.semanticIndex &&
                argument.line.operands.take(argument.operandIndex).none { operand -> it.matches(operand.value) } }
            offer(available.map { it.name })
        }
        val parameter = info.parameters.getOrNull(argument.semanticIndex)
            ?: info.parameters.lastOrNull()?.takeIf { it.repeated } ?: return
        if (parameter.combined) {
            val used = prefix.substringBeforeLast('|', "").split('|').toSet()
            offer(parameter.choices.filterNot { it in used }, prefix.substringAfterLast('|'))
        } else offer(parameter.choices)
    }
}
