package dev.nsis.jetbrains

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.nsis.jetbrains.editor.NsisFoldingBuilder
import dev.nsis.jetbrains.editor.NsisHighlightLexer
import dev.nsis.jetbrains.editor.NsisStructureElement
import dev.nsis.jetbrains.model.NsisIncludes
import dev.nsis.jetbrains.model.NsisModel
import dev.nsis.jetbrains.model.SymbolKind
import dev.nsis.jetbrains.psi.NsisAtom
import dev.nsis.jetbrains.settings.NsisSettings

class NsisInsightTest : BasePlatformTestCase() {
    private var scriptNumber = 0
    private fun script(text: String, name: String = "test.nsi"): NsisFile {
        val uniqueName = "${name.substringBeforeLast('.')}-${scriptNumber++}.${name.substringAfterLast('.')}"
        val file = myFixture.addFileToProject(uniqueName, text.trimIndent())
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        return myFixture.file as NsisFile
    }
    private fun errors(): List<HighlightInfo> = myFixture.doHighlighting().filter { it.severity == HighlightSeverity.ERROR }
    private fun referenceAtCaret(): PsiReference {
        val atom = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.caretOffset), NsisAtom::class.java)
        return atom!!.references.first { it.rangeInElement.contains(myFixture.caretOffset - atom.textOffset) }
    }
    private fun targets(): List<String> = (referenceAtCaret() as PsiPolyVariantReference).multiResolve(false).map { it.element!!.text }
    private fun complete(): Set<String> {
        val items = myFixture.completeBasic()
        return items?.map { it.lookupString }?.toSet() ?: emptySet()
    }

    fun testRecognizesScriptAndHeaderAndRecoversIncompleteCode() {
        val file = script("Name \"broken\nFunction Later\nFunctionEnd\n")
        assertEquals(NsisFileType, file.fileType)
        assertEquals("Later", NsisModel.get(file).symbols.single().name)
        assertTrue(errors().any { it.description == "Unterminated string" })
        assertEquals(NsisFileType, script("!define HEADER 1", "header.nsh").fileType)
    }

    fun testConcatenatedParametersAndContinuations() {
        val file = script("SetOutPath ${'$'}INSTDIR\\bin\nMessageBox MB_OK \\\n  \"continued\"\n")
        val lines = NsisModel.get(file).lines
        assertEquals("${'$'}INSTDIR\\bin", lines[0].operands.single().raw)
        assertEquals(listOf("MB_OK", "continued"), lines[1].operands.map { it.value })
    }

    fun testFunctionsMacrosDefinesAndVariablesNavigate() {
        script("Function Install\nFunctionEnd\nCall Ins<caret>tall\n")
        assertEquals(listOf("Install"), targets())
        script("!macro Setup ARG\nDetailPrint \"${'$'}{AR<caret>G}\"\n!macroend\n")
        assertEquals(listOf("ARG"), targets())
        script("!define APP Example\nName \"${'$'}{AP<caret>P}\"\n")
        assertEquals(listOf("APP"), targets())
        script("Var Destination\nStrCpy ${'$'}Desti<caret>nation ${'$'}INSTDIR\n")
        assertEquals(listOf("Destination"), targets())
        script("!macro Setup\n!macroend\n!insertmacro Se<caret>tup\n")
        assertEquals(listOf("Setup"), targets())
    }

    fun testLabelScopeAndRelativeJumps() {
        script("Function One\nagain:\nGoto ag<caret>ain\nFunctionEnd\nFunction Two\nagain:\nFunctionEnd\n")
        val result = (referenceAtCaret() as PsiPolyVariantReference).multiResolve(false)
        assertEquals(1, result.size)
        assertTrue(result.single().element!!.textOffset < myFixture.caretOffset)
        script("Function One\nhidden:\nFunctionEnd\nFunction Two\nGoto hid<caret>den\nFunctionEnd\n")
        assertTrue(targets().isEmpty())
        script("Function One\nGoto +<caret>1\nFunctionEnd\n")
        val atom = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.caretOffset), NsisAtom::class.java)
        assertTrue(atom!!.references.isEmpty())
    }

    fun testMacroParametersRemainVisibleInNestedFunction() {
        script("!macro Declare NAME\nFunction ${'$'}{NAME}\nDetailPrint \"${'$'}{NA<caret>ME}\"\nFunctionEnd\n!macroend\n")
        assertEquals(listOf("NAME"), targets())
    }

    fun testUnterminatedQuotedOperandsHaveBoundedReferenceRanges() {
        for (text in listOf("!include \"abc", "Call \"foo", "!insertmacro \"Example")) {
            val file = script(text)
            PsiTreeUtil.findChildrenOfType(file, NsisAtom::class.java).forEach { atom ->
                atom.references.forEach {
                    assertTrue(it.rangeInElement.endOffset <= atom.textLength)
                    it.canonicalText
                }
            }
        }
    }

    fun testDottedVariablesNavigateAndCompleteWithoutTruncation() {
        script("Var app\nVar app.version\nDetailPrint \"${'$'}app.ver<caret>sion\"\n")
        assertEquals(listOf("app.version"), targets())
        script("Var app.version\nVar app.vendor\nDetailPrint \"${'$'}app.v<caret>\"\n")
        assertEquals(setOf("app.version", "app.vendor"), complete())
    }

    fun testHeaderFragmentsDoNotReportCrossFileBlockErrors() {
        script("SectionEnd\n", "closing.nsh")
        assertTrue(errors().isEmpty())
        script("Section Install\n", "opening.nsh")
        assertTrue(errors().isEmpty())
        script("DetailPrint \"unfinished\n", "lexical.nsh")
        assertTrue(errors().any { it.description == "Unterminated string" })
    }

    fun testLiteralIncludeClosureCyclesAndAmbiguousConditionalDefinitions() {
        myFixture.addFileToProject("a.nsh", "!include b.nsh\n!ifdef MODE\nFunction Shared\nFunctionEnd\n!endif\n")
        myFixture.addFileToProject("b.nsh", "!include a.nsh\nFunction Shared\nFunctionEnd\n")
        val file = script("!include a.nsh\nCall Sh<caret>ared\n")
        assertEquals(3, NsisIncludes.graph(file).files.size)
        assertEquals(listOf("Shared", "Shared"), targets())
        script("!include \"a.<caret>nsh\"\n")
        assertEquals("a.nsh", referenceAtCaret().resolve()!!.containingFile.name)
    }

    fun testConfiguredIncludeOrderingAndSdkAndSettingsInvalidation() {
        val one = myFixture.addFileToProject("one/shared.nsh", "!define FIRST 1")
        val two = myFixture.addFileToProject("two/shared.nsh", "!define SECOND 1")
        val sdk = myFixture.addFileToProject("sdk/Include/sdk.nsh", "!define SDK_VALUE 1")
        val file = script("!include shared.nsh\n!include sdk.nsh\n")
        val settings = NsisSettings.get(project)
        assertEquals(1, NsisIncludes.graph(file).files.size)
        settings.update(listOf(one.virtualFile.parent.path, two.virtualFile.parent.path), sdk.virtualFile.parent.parent.path)
        val include = NsisModel.get(file).lines.first()
        assertEquals(one.virtualFile, NsisIncludes.resolve(file, include, include.includeArgument!!))
        assertEquals(setOf("FIRST", "SDK_VALUE"), NsisIncludes.graph(file).symbols(file, 0).map { it.name }.toSet())
        settings.update(listOf(two.virtualFile.parent.path), "")
        assertEquals(setOf("SECOND"), NsisIncludes.graph(file).symbols(file, 0).map { it.name }.toSet())
    }

    fun testLiteralSourceRootsAndDynamicIncludes() {
        myFixture.addFileToProject("headers/local.nsh", "!define LOCAL_VALUE 1")
        val file = script("!addincludedir headers\n!include local.nsh\n!include \"${'$'}{DYNAMIC}/missing.nsh\"\n")
        assertEquals(setOf("LOCAL_VALUE"), NsisIncludes.graph(file).symbols(file, 0).map { it.name }.toSet())
        assertTrue(errors().isEmpty())
    }

    fun testAbsoluteIncludeAndCompilerOptions() {
        val header = myFixture.addFileToProject("absolute.nsh", "!define ABSOLUTE 1")
        val file = script("!include /NONFATAL /CHARSET=UTF8 \"${header.virtualFile.path}\"\n")
        assertEquals("ABSOLUTE", NsisIncludes.graph(file).symbols(file, 0).single().name)
    }

    fun testIncludedLocalLabelsDoNotLeakIntoOtherScopes() {
        myFixture.addFileToProject("labels.nsh", "local:\n.global:\n")
        script("!include labels.nsh\nFunction Other\nGoto lo<caret>cal\nFunctionEnd\n")
        assertTrue(targets().isEmpty())
        script("!include labels.nsh\nFunction Other\nGoto .glo<caret>bal\nFunctionEnd\n")
        assertEquals(listOf(".global:"), targets())
    }

    fun testUnsavedEditsInvalidateIncludedSymbols() {
        val header = myFixture.addFileToProject("edit.nsh", "!define OLD 1\n")
        val file = script("!include edit.nsh\n")
        assertEquals("OLD", NsisIncludes.graph(file).symbols(file, 0).single().name)
        val document = PsiDocumentManager.getInstance(project).getDocument(header)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText("!define NEW 1\n") }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals("NEW", NsisIncludes.graph(file).symbols(file, 0).single().name)
    }

    fun testCompletionContexts() {
        script("Fun<caret>")
        assertContainsElements(complete(), "Function", "FunctionEnd")
        script("!if<caret>")
        assertContainsElements(complete(), "!ifdef", "!ifndef", "!ifmacrodef")
        script("Function First\nFunctionEnd\nFunction Final\nFunctionEnd\nCall Fi<caret>")
        assertEquals(setOf("First", "Final"), complete())
        script("Var Destination\nVar Desktop\nStrCpy ${'$'}Des<caret> 1")
        assertContainsElements(complete(), "Destination", "Desktop", "DESKTOP")
        script("!define APP_NAME Test\n!define APP_VERSION 1\nName \"${'$'}{APP_<caret>}\"")
        assertEquals(setOf("APP_NAME", "APP_VERSION"), complete())
        script("; Fun<caret>")
        assertTrue(complete().isEmpty())
    }

    fun testSafeDiagnosticsAndPreprocessorSuppression() {
        script("Function Missing\nDetailPrint \"hello\"\n")
        assertTrue(errors().any { it.description == "Missing functionend" })
        script("FunctionEnd\n")
        assertTrue(errors().any { it.description == "Unmatched FunctionEnd" })
        for (prefix in listOf("!ifdef MODE", "!macro Open", "!insertmacro Dynamic", "!include missing.nsh", "${'$'}{If} 1 == 1")) {
            script("$prefix\nFunction Generated\n")
            assertTrue("False structural error for $prefix", errors().isEmpty())
        }
        script("UnknownPlugin::Run /anything ${'$'}{UNDEFINED}\n")
        assertTrue(errors().isEmpty())
    }

    fun testStructureFoldingAndInterpolationHighlighting() {
        val file = script("Function Setup\nDetailPrint \"${'$'}INSTDIR ${'$'}{APP}\"\nFunctionEnd\nSection \"Install\"\nCall Setup\nSectionEnd\n!macro Example ARG\n!macroend\n")
        assertEquals(3, NsisStructureElement(file, file.name).children.size)
        assertEquals(3, NsisFoldingBuilder().buildFoldRegions(file, myFixture.editor.document, false).size)
        val lexer = NsisHighlightLexer()
        lexer.start(file.text)
        val tokens = mutableListOf<String>()
        while (lexer.tokenType != null) { tokens.add(lexer.tokenType.toString()); lexer.advance() }
        assertContainsElements(tokens, "VARIABLE", "DEFINE_REF", "COMMAND")
        assertTrue(errors().isEmpty())
    }

    fun testAnalysisObservesCancellation() {
        val file = script("!include missing.nsh\n")
        val indicator = EmptyProgressIndicator()
        try {
            ProgressManager.getInstance().runProcess(Runnable { indicator.cancel(); NsisIncludes.graph(file) }, indicator)
            fail("Canceled analysis continued")
        } catch (_: ProcessCanceledException) { }
    }

    fun testRepresentativeMuiAndLogicLibScript() {
        script("""
            !include "MUI2.nsh"
            !include "LogicLib.nsh"
            !define MUI_ABORTWARNING
            !insertmacro MUI_PAGE_DIRECTORY
            !insertmacro MUI_PAGE_INSTFILES
            !insertmacro MUI_LANGUAGE "English"
            Name "Example"
            OutFile "setup.exe"
            Section "Install"
              SetOutPath "${'$'}INSTDIR"
              ${'$'}{If} ${'$'}LANGUAGE == 1033
                DetailPrint "Hello"
              ${'$'}{EndIf}
              nsExec::Exec '"${'$'}INSTDIR\app.exe" /register'
            SectionEnd
        """)
        assertTrue(errors().isEmpty())
    }

    fun testNsis3SyntaxAndUnicodeText() {
        script("Unicode true\nManifestDPIAware true\nManifestLongPathAware true\nName \"café ${'$'}{U+263A}\"\nSection Install\nFileWriteUTF16LE ${'$'}0 \"Welcome${'$'}\\r${'$'}\\n\"\nSectionEnd\n")
        assertTrue(errors().isEmpty())
    }
}
