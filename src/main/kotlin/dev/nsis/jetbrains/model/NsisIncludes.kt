package dev.nsis.jetbrains.model

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import dev.nsis.jetbrains.NsisFile
import dev.nsis.jetbrains.settings.NsisSettings

class NsisIncludeGraph(val files: List<NsisFile>) {
    fun symbols(root: NsisFile, offset: Int): List<NsisSymbol> = files.flatMap { file ->
        ProgressManager.checkCanceled()
        val model = NsisModel.get(file)
        model.symbols.filter { symbol ->
            if (file == root) model.visible(symbol, offset)
            else symbol.scope == null && (symbol.kind != SymbolKind.LABEL || symbol.name.startsWith('.'))
        }
    }
}

object NsisIncludes {
    private val key = Key.create<CachedValue<NsisIncludeGraph>>("nsis.include.graph")

    fun graph(root: NsisFile): NsisIncludeGraph = CachedValuesManager.getCachedValue(root, key) {
        val visited = linkedSetOf<NsisFile>()
        val pending = ArrayDeque<NsisFile>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            ProgressManager.checkCanceled()
            val file = pending.removeFirst()
            if (!visited.add(file)) continue
            for (line in NsisModel.get(file).lines) {
                ProgressManager.checkCanceled()
                if (line.command != "!include") continue
                val argument = line.includeArgument ?: continue
                val target = resolve(file, line, argument) ?: continue
                val psi = PsiManager.getInstance(file.project).findFile(target) as? NsisFile ?: continue
                pending.add(psi)
            }
        }
        CachedValueProvider.Result.create(
            NsisIncludeGraph(visited.toList()), PsiModificationTracker.MODIFICATION_COUNT,
            VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS, NsisSettings.get(root.project)
        )
    }

    fun resolve(file: NsisFile, line: NsisLine, argument: NsisArgument): VirtualFile? {
        val name = argument.literal?.replace('\\', '/') ?: return null
        for (root in searchRoots(file, line)) {
            ProgressManager.checkCanceled()
            val found = findFrom(root, name)
            if (found != null && !found.isDirectory) return found
        }
        return null
    }

    fun searchRoots(file: NsisFile, line: NsisLine): List<VirtualFile> {
        val directory = file.virtualFile?.parent ?: return emptyList()
        val settings = NsisSettings.get(file.project).state
        val roots = buildList {
            add(directory.path)
            NsisModel.get(file).lines.asSequence().takeWhile { it.offset < line.offset }
                .filter { it.command == "!addincludedir" }
                .mapNotNull { it.operands.firstOrNull()?.literal }
                .mapNotNull { absolute(directory.path, it) }.forEach { add(it) }
            settings.includeDirectories.mapNotNull { absolute(file.project.basePath ?: directory.path, it) }.forEach { add(it) }
            if (settings.sdkDirectory.isNotBlank()) {
                absolute(file.project.basePath ?: directory.path, settings.sdkDirectory)?.let { add("$it/Include") }
            }
        }
        return roots.distinct().mapNotNull { root ->
            ProgressManager.checkCanceled()
            (directory.fileSystem.findFileByPath(root) ?: LocalFileSystem.getInstance().findFileByPath(root))?.takeIf { it.isDirectory }
        }
    }

    fun findFrom(root: VirtualFile, name: String): VirtualFile? {
        val path = absolute(root.path, name) ?: return null
        return root.fileSystem.findFileByPath(path) ?: LocalFileSystem.getInstance().findFileByPath(path)
    }

    private fun absolute(root: String, child: String): String? {
        if ('\u0000' in root || '\u0000' in child) return null
        val normalized = child.replace('\\', '/')
        val absolute = normalized.startsWith('/') || Regex("^[A-Za-z]:/").containsMatchIn(normalized)
        return FileUtil.toCanonicalPath(if (absolute) normalized else "$root/$normalized")
    }
}
