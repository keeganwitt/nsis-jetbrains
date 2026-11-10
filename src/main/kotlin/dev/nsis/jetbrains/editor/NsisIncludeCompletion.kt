package dev.nsis.jetbrains.editor

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import dev.nsis.jetbrains.NsisFile
import dev.nsis.jetbrains.model.NsisIncludes

object NsisIncludeCompletion {
    fun complete(file: NsisFile, offset: Int, argument: NsisArgumentContext, result: CompletionResultSet) {
        if (argument.line.command != "!include" || argument.semanticIndex != 0 || argument.switchIndex != null ||
            '$' in argument.argument?.raw.orEmpty()) return
        val prefix = argument.prefix
        val separatorIndex = prefix.indexOfLast { it == '/' || it == '\\' }
        val directoryPrefix = prefix.substring(0, separatorIndex + 1)
        val filenamePrefix = prefix.substring(separatorIndex + 1)
        val separator = if ('\\' in prefix) '\\' else '/'
        val offered = hashSetOf<String>()
        val matching = result.withPrefixMatcher(filenamePrefix)
        val start = argument.argument?.range?.startOffset ?: offset
        val suffix = argument.argument?.let { file.text.substring(offset, it.range.endOffset) }.orEmpty()
        val existingQuote = argument.argument?.raw?.firstOrNull()?.takeIf { it in NsisArgumentContext.quotes }
        for (root in NsisIncludes.searchRoots(file, argument.line)) {
            ProgressManager.checkCanceled()
            val directory = NsisIncludes.findFrom(root, directoryPrefix)?.takeIf { it.isDirectory } ?: continue
            for (child in directory.children) {
                ProgressManager.checkCanceled()
                if ('$' in child.name) continue
                val name = child.name + if (child.isDirectory) separator else ""
                if (!offered.add(name)) continue
                val path = directoryPrefix + name
                val item = LookupElementBuilder.create(name).withIcon(child.fileType.icon)
                    .withTypeText(root.presentableUrl, true)
                    .withInsertHandler { insertion, _ ->
                        val document = insertion.document
                        val tail = insertion.tailOffset
                        val remaining = document.charsSequence.subSequence(tail, document.textLength).toString()
                        val end = tail + when {
                            suffix.isNotEmpty() && remaining.startsWith(suffix) -> suffix.length
                            existingQuote != null && remaining.startsWith(existingQuote) -> 1
                            else -> 0
                        }
                        val quote = existingQuote ?: '"'.takeIf { path.any { it.isWhitespace() || it in ";#\"'`" } }
                        val text = if (quote != null) "$quote${path.replace(quote.toString(), "${'$'}\\$quote")}$quote" else path
                        document.replaceString(start, end, text)
                        val caret = start + text.length - if (child.isDirectory && quote != null) 1 else 0
                        insertion.tailOffset = start + text.length
                        insertion.editor.caretModel.moveToOffset(caret)
                    }.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE)
                matching.addElement(item)
            }
        }
    }
}
