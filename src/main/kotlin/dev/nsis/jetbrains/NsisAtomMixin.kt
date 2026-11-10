package dev.nsis.jetbrains

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import dev.nsis.jetbrains.model.NsisModel
import dev.nsis.jetbrains.model.NsisSymbol
import dev.nsis.jetbrains.model.SymbolKind
import dev.nsis.jetbrains.psi.NsisAtom

abstract class NsisAtomMixin(node: ASTNode) : ASTWrapperPsiElement(node), PsiNamedElement {
    override fun getReferences(): Array<PsiReference> = ReferenceProvidersRegistry.getReferencesFromProviders(this)
    override fun getReference(): PsiReference? = references.singleOrNull()
    fun declaration(): NsisSymbol? = (containingFile as? NsisFile)?.let { file ->
        NsisModel.get(file).symbols.firstOrNull { it.element == this }
    }
    override fun getName(): String? = declaration()?.name
    override fun setName(name: String): PsiElement {
        val symbol = declaration() ?: throw IncorrectOperationException("Not an NSIS declaration")
        val start = if (text.first() in charArrayOf('"', '\'', '`')) 1 else 0
        val end = text.length - (if (start == 1) 1 else 0) - (if (symbol.kind == SymbolKind.LABEL) 1 else 0)
        return replaceText(start, end, name)
    }

    fun replaceText(start: Int, end: Int, replacement: String): PsiElement {
        val updated = text.replaceRange(start, end, replacement)
        val file = PsiFileFactory.getInstance(project).createFileFromText("rename.nsi", NsisFileType, "$updated\n")
        val atoms = PsiTreeUtil.findChildrenOfType(file, NsisAtom::class.java)
        if (atoms.size != 1 || atoms.single().text != updated) throw IncorrectOperationException("Invalid NSIS identifier")
        node.replaceChild(node.firstChildNode, atoms.single().node.firstChildNode)
        return this
    }
}
