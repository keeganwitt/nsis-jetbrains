package dev.nsis.jetbrains

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.containers.MultiMap
import dev.nsis.jetbrains.editor.NsisRenameProcessor
import dev.nsis.jetbrains.model.NsisModel
import dev.nsis.jetbrains.model.SymbolKind

class NsisRefactoringTest : BasePlatformTestCase() {
    private fun script(text: String): NsisFile = myFixture.configureByText(NsisFileType, text) as NsisFile
    private fun declaration(file: NsisFile, name: String) = NsisModel.get(file).symbols.first { it.name == name }.element
    private fun rename(file: NsisFile, name: String, replacement: String) {
        RenameProcessor(project, declaration(file, name), replacement, false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    fun testFunctionUsagesAndQuotedRename() {
        val file = script("Function Install\nFunctionEnd\nCall Install\nCall 'Install'\n; Install\nDetailPrint \"Install\"\n")
        val target = declaration(file, "Install")
        assertEquals("Install", (target as PsiNamedElement).name)
        assertEquals(2, ReferencesSearch.search(target).findAll().size)
        assertEquals(2, myFixture.findUsages(target).size)
        rename(file, "Install", "Setup")
        assertEquals("Function Setup\nFunctionEnd\nCall Setup\nCall 'Setup'\n; Install\nDetailPrint \"Install\"\n", file.text)
    }

    fun testCrossFileDefineUsagesAndMultipleReferencesInString() {
        val header = myFixture.addFileToProject("shared.nsh", "!define APP Example\n") as NsisFile
        val script = myFixture.addFileToProject("setup.nsi", "!include shared.nsh\nName \"${'$'}{APP} ${'$'}{APP}\"\n")
        val unrelated = myFixture.addFileToProject("other.nsi", "!define APP Other\nName \"${'$'}{APP}\"\n")
        assertEquals(2, ReferencesSearch.search(declaration(header, "APP")).findAll().size)
        rename(header, "APP", "PRODUCT")
        assertEquals("!define PRODUCT Example\n", header.text)
        assertEquals("!include shared.nsh\nName \"${'$'}{PRODUCT} ${'$'}{PRODUCT}\"\n", script.text)
        assertEquals("!define APP Other\nName \"${'$'}{APP}\"\n", unrelated.text)
    }

    fun testDottedVariableAndDefineReferencesKeepSigils() {
        val file = script("Var app.version\nStrCpy ${'$'}app.version 1\nDetailPrint \"${'$'}app.version ${'$'}app.version\"\n")
        assertEquals(3, ReferencesSearch.search(declaration(file, "app.version")).findAll().size)
        rename(file, "app.version", "product.version")
        assertEquals("Var product.version\nStrCpy ${'$'}product.version 1\nDetailPrint \"${'$'}product.version ${'$'}product.version\"\n", file.text)
    }

    fun testLabelsKeepColonAndScope() {
        val file = script("Function One\nagain:\nGoto again\nCall :again\nFunctionEnd\nFunction Two\nagain:\nGoto again\nFunctionEnd\n")
        assertEquals(2, ReferencesSearch.search(declaration(file, "again")).findAll().size)
        rename(file, "again", "retry")
        assertEquals("Function One\nretry:\nGoto retry\nCall :retry\nFunctionEnd\nFunction Two\nagain:\nGoto again\nFunctionEnd\n", file.text)
    }

    fun testMacroAndParameterRename() {
        val file = script("!macro Declare ARG\nDetailPrint \"${'$'}{ARG}\"\n!macroend\n!macro Other ARG\nDetailPrint \"${'$'}{ARG}\"\n!macroend\n!insertmacro Declare Hello\n")
        rename(file, "ARG", "VALUE")
        assertTrue(file.text.contains("!macro Declare VALUE\nDetailPrint \"${'$'}{VALUE}\""))
        assertTrue(file.text.contains("!macro Other ARG\nDetailPrint \"${'$'}{ARG}\""))
        rename(file, "Declare", "Print")
        assertTrue(file.text.contains("!insertmacro Print Hello"))
    }

    fun testCollisionsAndInvalidNames() {
        val file = script("Function One\nFunctionEnd\nFunction Two\nFunctionEnd\n")
        for (name in listOf("Two", "bad name", "123")) {
            val conflicts = MultiMap<com.intellij.psi.PsiElement, String>()
            NsisRenameProcessor().findExistingNameConflicts(declaration(file, "One"), name, conflicts)
            assertFalse("Expected conflict for $name", conflicts.isEmpty)
        }
        assertEquals(listOf("One", "Two"), NsisModel.get(file).symbols.map { it.name })
    }

    fun testAmbiguousConditionalReferencesPreventRename() {
        val file = script("!ifdef MODE\nFunction Shared\nFunctionEnd\n!else\nFunction Shared\nFunctionEnd\n!endif\nCall Shared\n")
        val conflicts = MultiMap<com.intellij.psi.PsiElement, String>()
        NsisRenameProcessor().findExistingNameConflicts(declaration(file, "Shared"), "Renamed", conflicts)
        assertFalse(conflicts.isEmpty)
        assertEquals(2, NsisModel.get(file).symbols.count { it.kind == SymbolKind.FUNCTION })
    }

    fun testRenameFromReference() {
        val file = script("Function Install\nFunctionEnd\nCall Ins<caret>tall\n")
        myFixture.renameElementAtCaret("Setup")
        assertEquals("Function Setup\nFunctionEnd\nCall Setup\n", file.text)
    }

    fun testCompoundConditionalAndUndefReferences() {
        val file = script("!define APP 1\n!ifdef OTHER & APP\n!else ifndef OTHER | APP\n!endif\n!undef /noerrors OTHER APP\n")
        assertEquals(3, ReferencesSearch.search(declaration(file, "APP")).findAll().size)
        rename(file, "APP", "PRODUCT")
        assertEquals("!define PRODUCT 1\n!ifdef OTHER & PRODUCT\n!else ifndef OTHER | PRODUCT\n!endif\n!undef /noerrors OTHER PRODUCT\n", file.text)
    }

    fun testPageCallbacksAndMessageBoxJumpReferences() {
        val file = script("Function Setup\nFunctionEnd\nPage custom Setup Setup Caption\nPage instfiles Setup Setup Setup\nPageEx instfiles\nPageCallbacks Setup Setup Setup\nPageExEnd\nSection Install\nagain:\nMessageBox MB_OK question IDOK again\nMessageBox MB_OK question /SD IDOK IDOK again\nSectionEnd\n")
        assertEquals(8, ReferencesSearch.search(declaration(file, "Setup")).findAll().size)
        assertEquals(2, ReferencesSearch.search(declaration(file, "again")).findAll().size)
        rename(file, "Setup", "Initialize")
        rename(file, "again", "retry")
        assertTrue(file.text.contains("Page custom Initialize Initialize Caption"))
        assertTrue(file.text.contains("PageCallbacks Initialize Initialize Initialize"))
        assertTrue(file.text.contains("MessageBox MB_OK question /SD IDOK IDOK retry"))
    }

    fun testRenameCannotBypassConflicts() {
        val file = script("Var value\nDetailPrint ${'$'}value\n")
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            try {
                NsisRenameProcessor().renameElement(declaration(file, "value"), "INSTDIR", emptyArray(), null)
                fail("Renamed to a built-in variable")
            } catch (_: com.intellij.util.IncorrectOperationException) { }
        }
        assertEquals("Var value\nDetailPrint ${'$'}value\n", file.text)
    }

    fun testLabelsRejectNumericPrefixesAndScopeChanges() {
        val file = script("Function Setup\nagain:\nGoto again\nFunctionEnd\n")
        for (name in listOf("1again", ".again")) {
            val conflicts = MultiMap<com.intellij.psi.PsiElement, String>()
            NsisRenameProcessor().findExistingNameConflicts(declaration(file, "again"), name, conflicts)
            assertFalse(conflicts.isEmpty)
        }
    }
}
