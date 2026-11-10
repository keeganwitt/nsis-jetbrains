package dev.nsis.jetbrains.model

import com.intellij.patterns.PlatformPatterns
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.ResolveResult
import com.intellij.psi.PsiManager
import com.intellij.util.ProcessingContext
import dev.nsis.jetbrains.NsisFile
import dev.nsis.jetbrains.NsisAtomMixin
import dev.nsis.jetbrains.psi.NsisAtom

data class NsisInterpolation(val name: String, val kind: SymbolKind, val range: TextRange)

object NsisInterpolations {
    fun scan(text: String): List<NsisInterpolation> = buildList {
        var i = 0
        var closingBracesRemain = true
        while (i < text.length) {
            if (i % 1024 == 0) com.intellij.openapi.progress.ProgressManager.checkCanceled()
            if (text[i] != '$' || i + 1 == text.length) { i++; continue }
            when (text[i + 1]) {
                '$' -> i += 2
                '\\' -> i = (i + 3).coerceAtMost(text.length)
                '{' -> {
                    if (!closingBracesRemain) { i += 2; continue }
                    val end = text.indexOf('}', i + 2)
                    if (end < 0) { closingBracesRemain = false; i += 2; continue }
                    val name = text.substring(i + 2, end)
                    if (name.isNotBlank() && '$' !in name) add(NsisInterpolation(name, SymbolKind.DEFINE, TextRange(i + 2, end)))
                    i = end + 1
                }
                else -> {
                    var end = i + 1
                    while (end < text.length && NsisIdentifiers.isVariablePart(text[end])) end++
                    if (end > i + 1) add(NsisInterpolation(text.substring(i + 1, end), SymbolKind.VARIABLE, TextRange(i + 1, end)))
                    i = maxOf(end, i + 2)
                }
            }
        }
    }
}

object NsisReferenceKinds {
    fun operandKind(line: NsisLine, operandIndex: Int): SymbolKind? = when (line.command) {
        "!insertmacro", "!macroundef" -> if (operandIndex == 0) SymbolKind.MACRO else null
        "!ifmacrodef", "!ifmacrondef" -> if (operandIndex % 2 == 0) SymbolKind.MACRO else null
        "!ifdef", "!ifndef" -> if (operandIndex % 2 == 0) SymbolKind.DEFINE else null
        "!undef" -> if (line.operands.getOrNull(operandIndex)?.value?.startsWith('/') == false) SymbolKind.DEFINE else null
        "!else" -> when (line.operands.firstOrNull()?.value?.lowercase()) {
            "ifdef", "ifndef" -> if (operandIndex % 2 == 1) SymbolKind.DEFINE else null
            "ifmacrodef", "ifmacrondef" -> if (operandIndex % 2 == 1) SymbolKind.MACRO else null
            else -> null
        }
        "call" -> if (operandIndex == 0) {
            if (line.operands.firstOrNull()?.value?.startsWith(':') == true) SymbolKind.LABEL else SymbolKind.FUNCTION
        } else null
        "getfunctionaddress" -> if (operandIndex == 1) SymbolKind.FUNCTION else null
        "page", "uninstpage" -> {
            val lastCallback = if (line.operands.firstOrNull()?.value.equals("custom", true)) 2 else 3
            if (operandIndex in 1..lastCallback) SymbolKind.FUNCTION else null
        }
        "pagecallbacks" -> if (operandIndex in 0..2) SymbolKind.FUNCTION else null
        "getlabeladdress" -> if (operandIndex == 1) SymbolKind.LABEL else null
        "goto" -> if (operandIndex == 0) SymbolKind.LABEL else null
        "ifabort", "iferrors", "ifrebootflag", "ifsilent", "ifshellvarcontextall", "ifrtllanguage" -> if (operandIndex in 0..1) SymbolKind.LABEL else null
        "iffileexists" -> if (operandIndex in 1..2) SymbolKind.LABEL else null
        "messagebox" -> {
            val firstJump = if (line.operands.getOrNull(2)?.value.equals("/SD", true)) 5 else 3
            if (operandIndex == firstJump || operandIndex == firstJump + 2) SymbolKind.LABEL else null
        }
        "strcmp", "strcmps" -> if (operandIndex in 2..3) SymbolKind.LABEL else null
        "intcmp", "intcmpu", "int64cmp", "int64cmpu", "intptrcmp", "intptrcmpu" -> if (operandIndex in 2..4) SymbolKind.LABEL else null
        else -> null
    }
}

class NsisReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(NsisAtom::class.java), object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                val atom = element as NsisAtom
                val file = atom.containingFile as? NsisFile ?: return PsiReference.EMPTY_ARRAY
                val model = NsisModel.get(file)
                val line = model.lineAt(atom.textOffset) ?: return PsiReference.EMPTY_ARRAY
                val argument = line.arguments.firstOrNull { atom in it.atoms } ?: return PsiReference.EMPTY_ARRAY
                val references = NsisInterpolations.scan(atom.text).mapTo(mutableListOf<PsiReference>()) {
                    NsisSymbolReference(atom, it.range, it.name, it.kind)
                }
                val operandIndex = line.operands.indexOf(argument)
                if (operandIndex >= 0 && argument.atoms.size == 1 && argument.literal != null) {
                    var name = argument.value
                    var start = if (atom.text[0] in charArrayOf('"', '\'', '`')) 1 else 0
                    if (line.command == "!include" && line.includeArgument == argument) {
                        references.add(NsisIncludeReference(atom, TextRange(start, start + name.length), line, argument))
                    } else {
                        val kind = NsisReferenceKinds.operandKind(line, operandIndex)
                        if (kind == SymbolKind.LABEL && name.startsWith(':')) { start++; name = name.drop(1) }
                        if (kind != null && name.isNotEmpty() && !name.matches(Regex("[+-]?\\d+"))) {
                            references.add(NsisSymbolReference(atom, TextRange(start, start + name.length), name, kind))
                        }
                    }
                }
                return references.toTypedArray()
            }
        })
    }
}

class NsisSymbolReference(element: NsisAtom, range: TextRange, private val name: String, private val kind: SymbolKind) :
    PsiPolyVariantReferenceBase<NsisAtom>(element, range, true) {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val file = element.containingFile as? NsisFile ?: return ResolveResult.EMPTY_ARRAY
        return NsisIncludes.graph(file).symbols(file, element.textOffset)
            .filter { it.name == name && it.kind == kind }
            .map { PsiElementResolveResult(it.element) }.toTypedArray()
    }
    override fun getVariants(): Array<Any> = emptyArray()
    override fun handleElementRename(newElementName: String): PsiElement =
        (element as NsisAtomMixin).replaceText(rangeInElement.startOffset, rangeInElement.endOffset, newElementName)
}

class NsisIncludeReference(element: NsisAtom, range: TextRange, private val line: NsisLine, private val argument: NsisArgument) :
    PsiPolyVariantReferenceBase<NsisAtom>(element, range, true) {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val file = element.containingFile as? NsisFile ?: return ResolveResult.EMPTY_ARRAY
        val target = NsisIncludes.resolve(file, line, argument) ?: return ResolveResult.EMPTY_ARRAY
        val psi = PsiManager.getInstance(file.project).findFile(target) ?: return ResolveResult.EMPTY_ARRAY
        return arrayOf(PsiElementResolveResult(psi))
    }
    override fun getVariants(): Array<Any> = emptyArray()
}
