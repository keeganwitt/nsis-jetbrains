package dev.nsis.jetbrains.editor

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.documentation.LookupElementDocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import dev.nsis.jetbrains.NsisFile
import dev.nsis.jetbrains.NsisFileType
import dev.nsis.jetbrains.NsisTokens
import dev.nsis.jetbrains.model.NsisCatalog
import dev.nsis.jetbrains.model.NsisCommandInfo
import dev.nsis.jetbrains.model.NsisCommandMetadata
import dev.nsis.jetbrains.model.NsisModel

data class NsisCommandLookup(val name: String)

class NsisCommandDocumentationTarget(
    private val name: String,
    private val info: NsisCommandInfo? = NsisCommandMetadata.find(name)
) : DocumentationTarget {
    override fun createPointer(): Pointer<NsisCommandDocumentationTarget> = Pointer.hardPointer(this)
    override fun computePresentation(): TargetPresentation = TargetPresentation.builder(name).icon(NsisFileType.icon).presentation()
    override fun computeDocumentationHint(): String = StringUtil.escapeXmlEntities(info?.signature ?: name)
    override fun computeDocumentation(): DocumentationResult = DocumentationResult.documentation(documentationHtml())
        .externalUrl(info?.url ?: NsisCommandMetadata.referenceUrl(name))

    fun documentationHtml(): String = buildString {
        fun escape(text: String) = StringUtil.escapeXmlEntities(text)
        append(DocumentationMarkup.DEFINITION_START).append(escape(info?.signature ?: name)).append(DocumentationMarkup.DEFINITION_END)
        append(DocumentationMarkup.CONTENT_START)
        if (info != null) {
            append("<p>").append(escape(info.summary)).append("</p><dl>")
            for (parameter in info.parameters) {
                append("<dt><code>").append(escape(parameter.name)).append("</code></dt><dd>").append(escape(parameter.description))
                if (parameter.choices.isNotEmpty()) append(" Values: ").append(escape(parameter.choices.joinToString(", ")))
                append("</dd>")
            }
            for (switch in info.switches) {
                append("<dt><code>").append(escape(switch.name)).append("</code></dt><dd>").append(escape(switch.description)).append("</dd>")
            }
            append("</dl><p>Example:</p><pre>").append(escape(info.example)).append("</pre>")
        }
        append("<p><a href=\"").append(escape(info?.url ?: NsisCommandMetadata.referenceUrl(name)))
            .append("\">Official NSIS reference</a></p>").append(DocumentationMarkup.CONTENT_END)
    }
}

class NsisDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        if (file !is NsisFile) return emptyList()
        val line = NsisModel.get(file).lineAt(offset) ?: return emptyList()
        val command = line.arguments.firstOrNull() ?: return emptyList()
        if (offset !in command.range.startOffset..command.range.endOffset) return emptyList()
        if (file.findElementAt(offset)?.node?.elementType in setOf(NsisTokens.LINE_COMMENT, NsisTokens.BLOCK_COMMENT, NsisTokens.UNTERMINATED_COMMENT)) return emptyList()
        val name = (NsisCatalog.commands + NsisCatalog.directives).firstOrNull { it.equals(command.value, true) } ?: return emptyList()
        return listOf(NsisCommandDocumentationTarget(name, NsisCommandMetadata.find(name)?.forOperands(line.operands)))
    }
}

class NsisLookupDocumentationTargetProvider : LookupElementDocumentationTargetProvider {
    override fun documentationTarget(file: PsiFile, lookupElement: LookupElement, offset: Int): DocumentationTarget? {
        if (file !is NsisFile) return null
        val command = lookupElement.`object` as? NsisCommandLookup ?: return null
        return NsisCommandDocumentationTarget(command.name)
    }
}
