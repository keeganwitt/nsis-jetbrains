package dev.nsis.jetbrains.editor

import com.intellij.lang.Commenter
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.navigation.ItemPresentation
import com.intellij.pom.Navigatable
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import dev.nsis.jetbrains.NsisFile
import dev.nsis.jetbrains.NsisFileType
import dev.nsis.jetbrains.model.NsisModel
import dev.nsis.jetbrains.psi.NsisStatement

class NsisCommenter : Commenter {
    override fun getLineCommentPrefix() = ";"
    override fun getBlockCommentPrefix() = "/*"
    override fun getBlockCommentSuffix() = "*/"
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}

class NsisFoldingBuilder : FoldingBuilderEx() {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val file = root as? NsisFile ?: return emptyArray()
        return NsisModel.get(file).blocks.mapNotNull { block ->
            val closing = block.closing ?: return@mapNotNull null
            val start = block.opening.element.textRange.endOffset
            val end = closing.offset
            if (start >= end || document.getLineNumber(start) == document.getLineNumber(end)) null
            else FoldingDescriptor(block.opening.element.node, TextRange(start, end))
        }.toTypedArray()
    }
    override fun getPlaceholderText(node: ASTNode) = " … "
    override fun isCollapsedByDefault(node: ASTNode) = false
}

class NsisStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder = object : TreeBasedStructureViewBuilder() {
        override fun createStructureViewModel(editor: Editor?) = StructureViewModelBase(psiFile, editor, NsisStructureElement(psiFile, psiFile.name))
            .withSuitableClasses(NsisStatement::class.java)
    }
}

class NsisStructureElement(private val element: PsiElement, private val title: String) : StructureViewTreeElement {
    override fun getValue(): PsiElement = element
    override fun navigate(requestFocus: Boolean) { (element as? Navigatable)?.navigate(requestFocus) }
    override fun canNavigate() = (element as? Navigatable)?.canNavigate() == true
    override fun canNavigateToSource() = (element as? Navigatable)?.canNavigateToSource() == true
    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText() = title
        override fun getLocationString(): String? = null
        override fun getIcon(unused: Boolean) = NsisFileType.icon
    }
    override fun getChildren(): Array<StructureViewTreeElement> {
        val file = element as? NsisFile ?: return emptyArray()
        return NsisModel.get(file).blocks.filter { it.kind in setOf("function", "section", "sectiongroup", "!macro") }
            .map { NsisStructureElement(it.opening.element, "${it.kind.removePrefix("!")} ${it.name}") }.toTypedArray()
    }
}
