package dev.nsis.jetbrains.editor

import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReference
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import com.intellij.util.containers.MultiMap
import dev.nsis.jetbrains.NsisAtomMixin
import dev.nsis.jetbrains.NsisFile
import dev.nsis.jetbrains.NsisFileType
import dev.nsis.jetbrains.model.NsisCatalog
import dev.nsis.jetbrains.model.NsisIncludes
import dev.nsis.jetbrains.model.NsisModel
import dev.nsis.jetbrains.model.SymbolKind
import dev.nsis.jetbrains.psi.NsisAtom

class NsisFindUsagesProvider : FindUsagesProvider {
    override fun canFindUsagesFor(element: PsiElement) = (element as? NsisAtomMixin)?.declaration() != null
    override fun getHelpId(element: PsiElement): String? = null
    override fun getType(element: PsiElement) = (element as? NsisAtomMixin)?.declaration()?.kind?.name?.lowercase() ?: "NSIS symbol"
    override fun getDescriptiveName(element: PsiElement) = (element as? NsisAtomMixin)?.name ?: element.text
    override fun getNodeText(element: PsiElement, useFullName: Boolean) = getDescriptiveName(element)
}

class NsisNamesValidator : NamesValidator {
    override fun isKeyword(name: String, project: Project?) = false
    override fun isIdentifier(name: String, project: Project?) =
        Regex("[A-Za-z_0-9.]+").matches(name) && name.any { !it.isDigit() && it != '.' }
}

internal fun nsisProjectFiles(element: PsiElement): List<NsisFile> {
    val manager = PsiManager.getInstance(element.project)
    val files = linkedSetOf<NsisFile>()
    (element.containingFile as? NsisFile)?.let { files.add(it) }
    for (virtualFile in FileTypeIndex.getFiles(NsisFileType, GlobalSearchScope.projectScope(element.project))) {
        ProgressManager.checkCanceled()
        (manager.findFile(virtualFile) as? NsisFile)?.let { files.add(it) }
    }
    return files.flatMap { NsisIncludes.graph(it).files }.distinct()
}

class NsisReferencesSearch : QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
    override fun execute(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>): Boolean {
        val target = parameters.elementToSearch as? NsisAtomMixin ?: return true
        if (target.declaration() == null) return true
        for (file in nsisProjectFiles(target)) {
            for (atom in PsiTreeUtil.findChildrenOfType(file, NsisAtom::class.java)) {
                ProgressManager.checkCanceled()
                if (!PsiSearchScopeUtil.isInScope(parameters.effectiveSearchScope, atom)) continue
                for (reference in atom.references) {
                    if (reference.isReferenceTo(target) && !consumer.process(reference)) return false
                }
            }
        }
        return true
    }
}

class NsisRenameProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement) = (element as? NsisAtomMixin)?.declaration() != null
    override fun isInplaceRenameSupported() = false

    override fun renameElement(element: PsiElement, newName: String, usages: Array<out UsageInfo>, listener: RefactoringElementListener?) {
        val conflicts = MultiMap<PsiElement, String>()
        findExistingNameConflicts(element, newName, conflicts)
        if (!conflicts.isEmpty) throw IncorrectOperationException(conflicts.values().first())
        super.renameElement(element, newName, usages, listener)
    }

    override fun findExistingNameConflicts(element: PsiElement, newName: String, conflicts: MultiMap<PsiElement, String>) {
        val atom = element as? NsisAtomMixin ?: return
        val symbol = atom.declaration() ?: return
        if (newName == symbol.name) return
        if (!NsisNamesValidator().isIdentifier(newName, element.project) ||
            (symbol.kind == SymbolKind.LABEL && (newName.first().isDigit() || newName.startsWith('.') != symbol.name.startsWith('.')))) {
            conflicts.putValue(element, "Invalid NSIS name: $newName")
            return
        }
        if (symbol.kind == SymbolKind.VARIABLE && "$$newName" in NsisCatalog.variables) {
            conflicts.putValue(element, "The built-in variable $$newName already exists")
        }
        if (symbol.kind == SymbolKind.DEFINE && newName in NsisCatalog.defines) {
            conflicts.putValue(element, "The built-in define $newName already exists")
        }
        val files = nsisProjectFiles(element)
        val contexts = files.filter { file -> element.containingFile in NsisIncludes.graph(file).files }
        val related = contexts.flatMap { NsisIncludes.graph(it).files }.toSet()
        for (file in related) {
            for (other in NsisModel.get(file).symbols) {
                if (other.element == element || other.kind != symbol.kind || other.name != newName) continue
                val overlaps = if (file == element.containingFile) {
                    other.scope == null || symbol.scope == null || other.scope == symbol.scope
                } else (other.scope == null && symbol.scope == null) ||
                    (symbol.kind == SymbolKind.DEFINE && (other.scope == null || symbol.scope == null))
                if (overlaps) conflicts.putValue(other.element, "An NSIS ${symbol.kind.name.lowercase()} named $newName already exists")
            }
        }
        ReferencesSearch.search(element).forEach(Processor { reference ->
            if (reference is PsiPolyVariantReference && reference.multiResolve(false).size != 1) {
                conflicts.putValue(reference.element, "This reference has multiple possible declarations")
            }
            true
        })
    }
}
