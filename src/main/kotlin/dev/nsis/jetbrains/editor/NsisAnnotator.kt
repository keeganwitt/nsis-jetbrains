package dev.nsis.jetbrains.editor

import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import dev.nsis.jetbrains.NsisFile
import dev.nsis.jetbrains.NsisTokens
import dev.nsis.jetbrains.model.NsisModel
import dev.nsis.jetbrains.psi.NsisTypes

class NsisAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is NsisFile) return
        NsisModel.get(element).structuralProblems.forEach {
            holder.newAnnotation(HighlightSeverity.ERROR, it.message).range(it.range).create()
        }
        PsiTreeUtil.collectElements(element) {
            it.firstChild == null && it.node.elementType in setOf(NsisTypes.UNTERMINATED_STRING, NsisTokens.UNTERMINATED_COMMENT)
        }.forEach {
            val message = if (it.node.elementType == NsisTypes.UNTERMINATED_STRING) "Unterminated string" else "Unterminated block comment"
            holder.newAnnotation(HighlightSeverity.ERROR, message).range(it).create()
        }
    }
}
