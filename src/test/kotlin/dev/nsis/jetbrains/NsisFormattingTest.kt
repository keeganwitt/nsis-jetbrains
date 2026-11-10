package dev.nsis.jetbrains

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import dev.nsis.jetbrains.editor.NsisCodeStyleSettingsProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NsisFormattingTest : BasePlatformTestCase() {
    private fun format(source: String, spaces: Int = 2): String {
        val settings = CodeStyleSettingsManager.getInstance(project).createTemporarySettings()
        settings.AUTODETECT_INDENTS = false
        settings.getCommonSettings(NsisLanguage).initIndentOptions().apply {
            INDENT_SIZE = spaces
            CONTINUATION_INDENT_SIZE = spaces * 2
            TAB_SIZE = spaces
            USE_TAB_CHARACTER = false
        }
        CodeStyleSettingsManager.getInstance(project).setTemporarySettings(settings)
        CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged()
        myFixture.configureByText(NsisFileType, source)
        WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformat(myFixture.file) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        return myFixture.file.text
    }

    override fun tearDown() {
        try { CodeStyleSettingsManager.getInstance(project).dropTemporarySettings() } finally { super.tearDown() }
    }

    fun testNativeAndConditionalBlocks() {
        val source = "!ifdef MODE\nFunction Setup\nDetailPrint   \"hello  world\"\nFunctionEnd\n!else\nSection Install\nSetOutPath   ${'$'}INSTDIR\nSectionEnd\n!endif\n"
        val expected = "!ifdef MODE\n  Function Setup\n    DetailPrint \"hello  world\"\n  FunctionEnd\n!else\n  Section Install\n    SetOutPath ${'$'}INSTDIR\n  SectionEnd\n!endif\n"
        assertEquals(expected, format(source))
        assertEquals(expected, format(expected))
    }

    fun testLogicLibAndConfigurableIndent() {
        val source = "Section Install\n${'$'}{If} ${'$'}0 == 1\nDetailPrint yes\n${'$'}{Else}\nDetailPrint no\n${'$'}{EndIf}\nSectionEnd\n"
        assertEquals("Section Install\n    ${'$'}{If} ${'$'}0 == 1\n        DetailPrint yes\n    ${'$'}{Else}\n        DetailPrint no\n    ${'$'}{EndIf}\nSectionEnd\n", format(source, 4))
    }

    fun testCommentsBlankLinesContinuationsAndConcatenatedArgumentsSurvive() {
        val source = "Section Install\n; retain   comment\n\nSetOutPath   ${'$'}INSTDIR\\bin\nMessageBox MB_OK \\\n      \"continued  string\"\n/* block\n   comment */\nSectionEnd\n"
        val expected = "Section Install\n  ; retain   comment\n\n  SetOutPath ${'$'}INSTDIR\\bin\n  MessageBox MB_OK \\\n      \"continued  string\"\n  /* block\n   comment */\nSectionEnd\n"
        assertEquals(expected, format(source))
        assertEquals(expected, format(expected))
    }

    fun testAdjustLineIndentAndEnter() {
        format("Section Install\nDetailPrint hello\nSectionEnd\n")
        val document = myFixture.editor.document
        val offset = document.getLineStartOffset(1)
        WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).adjustLineIndent(myFixture.file, offset) }
        assertEquals("  DetailPrint hello", document.getText(com.intellij.openapi.util.TextRange(document.getLineStartOffset(1), document.getLineEndOffset(1))))
        myFixture.editor.caretModel.moveToOffset(document.getLineEndOffset(0))
        myFixture.type('\n')
        assertEquals("  ", document.getText(com.intellij.openapi.util.TextRange(document.getLineStartOffset(1), document.getLineEndOffset(1))))
    }

    fun testSelectSwitchAndLoopBranches() {
        val source = "Function Setup\n${'$'}{Switch} ${'$'}0\n${'$'}{Case} 1\nDetailPrint one\n${'$'}{Default}\n${'$'}{Do}\nDetailPrint loop\n${'$'}{LoopWhile} ${'$'}1 == 1\n${'$'}{EndSwitch}\nFunctionEnd\n"
        val expected = "Function Setup\n  ${'$'}{Switch} ${'$'}0\n    ${'$'}{Case} 1\n      DetailPrint one\n    ${'$'}{Default}\n      ${'$'}{Do}\n        DetailPrint loop\n      ${'$'}{LoopWhile} ${'$'}1 == 1\n  ${'$'}{EndSwitch}\nFunctionEnd\n"
        assertEquals(expected, format(source))
    }

    fun testMultilineStringContentIsPreserved() {
        val source = "Section Install\nDetailPrint \"hello\\\n       world\"\nSectionEnd\n"
        assertEquals("Section Install\n  DetailPrint \"hello\\\n       world\"\nSectionEnd\n", format(source))
    }

    fun testCommentBeforeBlockOpening() {
        assertEquals("/* note */ Function Setup\n  DetailPrint hello\nFunctionEnd\n", format("/* note */ Function Setup\nDetailPrint hello\nFunctionEnd\n"))
    }

    fun testTabsAndFormattingSelection() {
        format("Section Install\nDetailPrint hello\nSectionEnd\n", 4)
        val settings = CodeStyleSettingsManager.getInstance(project).temporarySettings!!
        settings.getCommonSettings(NsisLanguage).indentOptions!!.USE_TAB_CHARACTER = true
        CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged()
        WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformat(myFixture.file) }
        assertEquals("Section Install\n\tDetailPrint hello\nSectionEnd\n", myFixture.file.text)
        myFixture.configureByText(NsisFileType, "Section Install\nDetailPrint    hello\nDetailPrint    untouched\nSectionEnd\n")
        assertTrue("Language indent options", settings.getIndentOptions(NsisFileType).USE_TAB_CHARACTER)
        assertTrue("File indent options", com.intellij.application.options.CodeStyle.getIndentOptions(myFixture.file).USE_TAB_CHARACTER)
        val document = myFixture.editor.document
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformatText(myFixture.file, document.getLineStartOffset(1), document.getLineEndOffset(1))
        }
        assertEquals("Section Install\n\tDetailPrint hello\nDetailPrint    untouched\nSectionEnd\n", myFixture.file.text)
    }

    fun testCodeStyleSettingsPageCanBeCreated() {
        val manager = CodeStyleSettingsManager.getInstance(project)
        val configurable = NsisCodeStyleSettingsProvider().createConfigurable(manager.createSettings(), manager.createSettings())
        try { assertNotNull(configurable.createComponent()) } finally { configurable.disposeUIResources() }
    }
}
