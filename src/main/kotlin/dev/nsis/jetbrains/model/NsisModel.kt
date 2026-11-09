package dev.nsis.jetbrains.model

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import dev.nsis.jetbrains.NsisFile
import dev.nsis.jetbrains.psi.NsisAtom
import dev.nsis.jetbrains.psi.NsisStatement
import java.util.Locale

data class NsisArgument(val atoms: List<NsisAtom>, val raw: String, val range: TextRange) {
    val value: String get() = raw.unquoted()
    val literal: String? get() {
        if (raw.firstOrNull()?.let { it in charArrayOf('"', '\'', '`') } == true &&
            (raw.length < 2 || raw.last() != raw.first())) return null
        return value.takeUnless { '$' in it || '\n' in it || '\r' in it }
    }
    val element: NsisAtom get() = atoms.first()
}

data class NsisLine(val element: NsisStatement, val arguments: List<NsisArgument>) {
    val command: String get() = arguments.firstOrNull()?.value?.lowercase(Locale.ROOT).orEmpty()
    val offset: Int get() = element.textOffset
    val operands: List<NsisArgument> get() = arguments.drop(1)
    val includeArgument: NsisArgument? get() = operands.firstOrNull {
        !it.value.equals("/NONFATAL", true) && !it.value.startsWith("/CHARSET=", true)
    }
}

enum class SymbolKind { FUNCTION, LABEL, MACRO, DEFINE, VARIABLE }

data class NsisSymbol(val name: String, val kind: SymbolKind, val argument: NsisArgument, val scope: Int? = null) {
    val element: NsisAtom get() = argument.element
}

data class NsisBlock(val kind: String, val opening: NsisLine, val closing: NsisLine?) {
    val range: TextRange get() = TextRange(opening.offset, closing?.element?.textRange?.endOffset ?: opening.element.containingFile.textLength)
    val name: String get() = opening.operands.firstOrNull { !it.value.startsWith('/') }?.value ?: kind
}

data class NsisProblem(val range: TextRange, val message: String)

class NsisModel private constructor(val file: NsisFile) {
    private val source = file.text
    val lines: List<NsisLine> = PsiTreeUtil.getChildrenOfTypeAsList(file, NsisStatement::class.java).map { statement ->
        ProgressManager.checkCanceled()
        val groups = mutableListOf<MutableList<NsisAtom>>()
        for (atom in statement.atomList) {
            val previous = groups.lastOrNull()?.lastOrNull()
            if (previous != null && previous.textRange.endOffset == atom.textRange.startOffset) groups.last().add(atom)
            else groups.add(mutableListOf(atom))
        }
        NsisLine(statement, groups.map { atoms ->
            val range = TextRange(atoms.first().textRange.startOffset, atoms.last().textRange.endOffset)
            NsisArgument(atoms, range.substring(source), range)
        })
    }

    private val blockResults = findBlocks()
    val blocks: List<NsisBlock> = blockResults.first.sortedBy { it.opening.offset }
    val structuralProblems: List<NsisProblem> = if (file.name.endsWith(".nsh", true) || lines.any {
        it.command in uncertainCommands || it.command.startsWith("${'$'}{")
    }) emptyList() else blockResults.second

    val symbols: List<NsisSymbol> = buildList {
        for (line in lines) {
            ProgressManager.checkCanceled()
            val first = line.operands.firstOrNull { !it.value.startsWith('/') }
            when (line.command) {
                "function" -> first?.literal?.let { add(NsisSymbol(it, SymbolKind.FUNCTION, first)) }
                "!macro" -> {
                    first?.literal?.let { add(NsisSymbol(it, SymbolKind.MACRO, first)) }
                    val firstIndex = line.operands.indexOf(first)
                    line.operands.drop(firstIndex + 1).forEach { parameter ->
                        parameter.literal?.let { add(NsisSymbol(it, SymbolKind.DEFINE, parameter, line.offset)) }
                    }
                }
                "!define" -> first?.literal?.let { add(NsisSymbol(it, SymbolKind.DEFINE, first)) }
                "var" -> first?.literal?.let { add(NsisSymbol(it, SymbolKind.VARIABLE, first)) }
                else -> {
                    val argument = line.arguments.firstOrNull() ?: continue
                    val name = argument.literal ?: continue
                    if (name.endsWith(':') && !name.contains("::")) {
                        val label = name.dropLast(1)
                        add(NsisSymbol(label, SymbolKind.LABEL, argument, if (label.startsWith('.')) null else scopeAt(line.offset)))
                    }
                }
            }
        }
    }

    fun lineAt(offset: Int): NsisLine? = lines.firstOrNull { offset in it.element.textRange.startOffset..it.element.textRange.endOffset }

    fun scopeAt(offset: Int): Int? = blocks.lastOrNull {
        it.kind in scopeKinds && offset >= it.opening.offset && offset < it.range.endOffset
    }?.opening?.offset

    fun visible(symbol: NsisSymbol, offset: Int): Boolean {
        if (symbol.scope == null) return true
        if (symbol.kind == SymbolKind.DEFINE) return blocks.any {
            it.kind == "!macro" && it.opening.offset == symbol.scope && it.range.containsOffset(offset)
        }
        return symbol.scope == scopeAt(offset)
    }

    private fun findBlocks(): Pair<List<NsisBlock>, List<NsisProblem>> {
        val stack = mutableListOf<NsisLine>()
        val blocks = mutableListOf<NsisBlock>()
        val problems = mutableListOf<NsisProblem>()
        for (line in lines) {
            ProgressManager.checkCanceled()
            if (line.command in endings) stack.add(line)
            else if (line.command in endings.values) {
                val opening = stack.lastOrNull()
                if (opening != null && endings[opening.command] == line.command) {
                    stack.removeAt(stack.lastIndex)
                    blocks.add(NsisBlock(opening.command, opening, line))
                } else problems.add(NsisProblem(line.arguments.first().range, "Unmatched ${line.arguments.first().value}"))
            }
        }
        stack.forEach {
            blocks.add(NsisBlock(it.command, it, null))
            problems.add(NsisProblem(it.arguments.first().range, "Missing ${endings[it.command]}"))
        }
        return blocks to problems
    }

    companion object {
        private val endings = mapOf("function" to "functionend", "section" to "sectionend", "sectiongroup" to "sectiongroupend", "pageex" to "pageexend", "!macro" to "!macroend")
        private val scopeKinds = setOf("function", "section", "!macro")
        private val uncertainCommands = setOf("!if", "!ifdef", "!ifndef", "!ifmacrodef", "!ifmacrondef", "!else", "!endif", "!macro", "!insertmacro", "!include")

        fun get(file: NsisFile): NsisModel = CachedValuesManager.getCachedValue(file) {
            CachedValueProvider.Result.create(NsisModel(file), file)
        }
    }
}

fun String.unquoted(): String = if (length >= 2 && first() in charArrayOf('"', '\'', '`') && last() == first()) substring(1, lastIndex) else this
