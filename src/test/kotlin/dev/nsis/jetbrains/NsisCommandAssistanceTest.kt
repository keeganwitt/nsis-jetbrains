package dev.nsis.jetbrains

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.nsis.jetbrains.editor.NsisArgumentContext
import dev.nsis.jetbrains.editor.NsisDocumentationTargetProvider
import dev.nsis.jetbrains.editor.NsisLookupDocumentationTargetProvider
import dev.nsis.jetbrains.editor.NsisCommandDocumentationTarget
import dev.nsis.jetbrains.editor.NsisCommandLookup
import dev.nsis.jetbrains.editor.NsisParameterInfoHandler
import dev.nsis.jetbrains.model.NsisCommandMetadata
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.Lookup
import dev.nsis.jetbrains.settings.NsisSettings
import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.documentation.LookupElementDocumentationTargetProvider
import com.intellij.lang.parameterInfo.LanguageParameterInfo
import java.lang.reflect.Proxy
import java.awt.Color

class NsisCommandAssistanceTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            NsisSettings.get(project).update(emptyList(), "")
        } finally {
            super.tearDown()
        }
    }

    private fun context(text: String): NsisArgumentContext? {
        myFixture.configureByText(NsisFileType, text)
        return NsisArgumentContext.at(myFixture.file as NsisFile, myFixture.caretOffset)
    }

    fun testRequiredCommandMetadata() {
        val names = "File Exec ExecWait ExecShell MessageBox ReadRegStr WriteRegStr WriteRegDWORD DeleteRegKey DeleteRegValue SetRegView SetOutPath StrCpy StrCmp IntCmp Goto Call DetailPrint Name OutFile InstallDir RequestExecutionLevel SetShellVarContext Unicode !include !addincludedir !define !insertmacro".split(' ')
        for (name in names) {
            val info = NsisCommandMetadata.find(name.uppercase())!!
            assertTrue(name, info.parameters.isNotEmpty())
            assertTrue(name, info.summary.isNotBlank())
            assertTrue(name, info.example.isNotBlank())
            assertTrue(name, info.url.startsWith("https://nsis.sourceforge.io/Docs/"))
        }
        assertNull(NsisCommandMetadata.find("MadeUpCommand"))
    }

    fun testArgumentContextTracksQuotedOperandsAndWhitespace() {
        assertEquals(1, context("WriteRegStr HKCU <caret>")!!.operandIndex)
        assertEquals(2, context("WriteRegStr HKCU \"Software\\My App\" \"na<caret>me\" \"value\"")!!.operandIndex)
        assertEquals("na", context("WriteRegStr HKCU \"Software\\My App\" \"na<caret>me\"")!!.prefix)
        assertEquals(1, context("ExecWait \"app.exe\" \\\n  <caret>")!!.operandIndex)
        assertNull(context("MessageBox MB_OK ; <caret>"))
        assertNull(context("/* MessageBox <caret> */"))
        assertNull(context("Name App\n<caret>"))
    }

    fun testOptionalSwitchesDoNotShiftSemanticOperands() {
        val include = context("!include /NONFATAL /CHARSET=UTF8 \"he<caret>\"")!!
        assertEquals(0, include.semanticIndex)
        assertEquals("he", include.prefix)
        assertEquals(0, context("File /r /nonfatal <caret>")!!.semanticIndex)
        assertEquals(0, context("File /oname=app.exe <caret>")!!.semanticIndex)
        assertEquals(0, context("ExecShell /INVOKEIDLIST o<caret>")!!.semanticIndex)
        assertEquals(0, context("DeleteRegKey /ifempty H<caret>")!!.semanticIndex)
        assertFalse(context("ExecWait \"/path/app\" <caret>")!!.info!!.isSwitch("/path/app"))
    }

    fun testSwitchNamesInPositionalStringsRemainOperands() {
        val define = context("!define MODE \"/math\"<caret>")!!
        assertEquals(listOf("name", "value"), define.info!!.parameters.map { it.name })
        assertEquals(1, define.semanticIndex)
        val text = context("MessageBox MB_OK \"/SD<caret>\"")!!
        assertNull(text.switchIndex)
        assertEquals(1, text.semanticIndex)
        val shell = context("ExecShell open \"/INVOKEIDLIST\" <caret>")!!
        assertEquals(2, shell.semanticIndex)
    }

    fun testDocumentationTargetsOnlyCommandTokens() {
        context("ReadReg<caret>Str ${'$'}0 HKCU \"Software\" \"Name\"")
        val target = NsisDocumentationTargetProvider().documentationTargets(myFixture.file, myFixture.caretOffset).single() as NsisCommandDocumentationTarget
        val html = target.documentationHtml()
        assertTrue(html.contains("ReadRegStr output root_key subkey value_name"))
        assertTrue(html.contains("Variable receiving the result"))
        assertTrue(html.contains("#readregstr"))
        assertEquals("ReadRegStr", target.createPointer().dereference()!!.computePresentation().presentableText)
        context("DetailPrint \"ReadReg<caret>Str\"")
        assertEmpty(NsisDocumentationTargetProvider().documentationTargets(myFixture.file, myFixture.caretOffset))
        context("; ReadReg<caret>Str")
        assertEmpty(NsisDocumentationTargetProvider().documentationTargets(myFixture.file, myFixture.caretOffset))
        context("Unknown<caret>Command anything")
        assertEmpty(NsisDocumentationTargetProvider().documentationTargets(myFixture.file, myFixture.caretOffset))
        context("Clear<caret>Errors")
        val fallback = NsisDocumentationTargetProvider().documentationTargets(myFixture.file, myFixture.caretOffset).single() as NsisCommandDocumentationTarget
        assertTrue(fallback.documentationHtml().contains("Chapter4.html"))
        assertFalse(fallback.documentationHtml().contains("Example"))
    }

    fun testDocumentationAtCommandEndAndCommentBoundary() {
        for (text in listOf("ExecWait<caret>", "ExecWait<caret> \"app.exe\"")) {
            context(text)
            assertEquals(1, NsisDocumentationTargetProvider().documentationTargets(myFixture.file, myFixture.caretOffset).size)
        }
        context("ExecWait;<caret> comment")
        assertEmpty(NsisDocumentationTargetProvider().documentationTargets(myFixture.file, myFixture.caretOffset))
        context("ExecWait<caret>; comment")
        assertEmpty(NsisDocumentationTargetProvider().documentationTargets(myFixture.file, myFixture.caretOffset))
    }

    fun testCompletionItemDocumentationUsesSelectedCommand() {
        context("Fi<caret>")
        val provider = NsisLookupDocumentationTargetProvider()
        val lookup = myFixture.completeBasic()!!.first { it.lookupString == "File" }
        val target = provider.documentationTarget(myFixture.file, lookup, myFixture.caretOffset) as NsisCommandDocumentationTarget
        assertTrue(target.documentationHtml().contains("File /r"))
        assertNull(provider.documentationTarget(myFixture.file, LookupElementBuilder.create("File"), myFixture.caretOffset))
        val escaped = NsisCommandMetadata.find("File")!!.copy(summary = "<script>&\"", example = "<img>")
        val html = NsisCommandDocumentationTarget("File", escaped).documentationHtml()
        assertTrue(html.contains("&lt;script&gt;&amp;&quot;"))
        assertFalse(html.contains("<img>"))
    }

    fun testProvidersAreRegisteredWithThePlatform() {
        assertTrue(DocumentationTargetProvider.EP_NAME.extensionList.any { it is NsisDocumentationTargetProvider })
        assertTrue(LookupElementDocumentationTargetProvider.EP_NAME.extensionList.any { it is NsisLookupDocumentationTargetProvider })
        assertTrue(LanguageParameterInfo.INSTANCE.allForLanguage(NsisLanguage).any { it is NsisParameterInfoHandler })
    }

    private inline fun <reified T> platformContext(crossinline handler: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, arguments ->
            handler(method.name, arguments.orEmpty())
        } as T

    fun testParameterPopupLifecycleAndHighlightRange() {
        context("MessageBox MB_OK \"text with spaces\" /SD ID<caret>OK IDNO cancel")
        val handler = NsisParameterInfoHandler()
        var items: Array<out Any?> = emptyArray()
        var owner: com.intellij.psi.PsiElement? = null
        var current = -1
        var removed = false
        val create = platformContext<CreateParameterInfoContext> { method, arguments ->
            when (method) {
                "getFile" -> myFixture.file
                "getOffset" -> myFixture.caretOffset
                "setItemsToShow" -> { items = arguments[0] as Array<*>; null }
                "showHint" -> { owner = arguments[0] as com.intellij.psi.PsiElement; null }
                else -> error(method)
            }
        }
        val statement = handler.findElementForParameterInfo(create)!!
        handler.showParameterInfo(statement, create)
        assertEquals(statement, owner)
        val update = platformContext<UpdateParameterInfoContext> { method, arguments ->
            when (method) {
                "getFile" -> myFixture.file
                "getOffset" -> myFixture.caretOffset
                "getParameterOwner" -> owner
                "setParameterOwner" -> { owner = arguments[0] as com.intellij.psi.PsiElement; null }
                "getObjectsToView" -> items
                "setCurrentParameter" -> { current = arguments[0] as Int; null }
                "removeHint" -> { removed = true; null }
                else -> error(method)
            }
        }
        assertEquals(statement, handler.findElementForUpdatingParameterInfo(update))
        handler.updateParameterInfo(statement, update)
        assertEquals(2, current)
        var highlight = ""
        val ui = platformContext<ParameterInfoUIContext> { method, arguments ->
            when (method) {
                "getCurrentParameterIndex" -> current
                "getDefaultParameterColor" -> Color.BLACK
                "setupUIComponentPresentation" -> {
                    highlight = (arguments[0] as String).substring(arguments[1] as Int, arguments[2] as Int)
                    ""
                }
                else -> error(method)
            }
        }
        handler.updateUI(items.single() as dev.nsis.jetbrains.model.NsisCommandInfo, ui)
        assertEquals("[/SD value]", highlight)
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf("IDNO") + 1)
        handler.updateParameterInfo(statement, update)
        assertEquals(3, current)
        assertFalse(removed)
        context("Name App\nDetailPrint <caret>\"text\"")
        assertNull(handler.findElementForUpdatingParameterInfo(update))
        handler.updateParameterInfo(statement, update)
        assertTrue(removed)
        context("Unknown <caret>argument")
        assertNull(handler.findElementForParameterInfo(create))
        context("MessageBox MB_OK ; <caret>comment")
        assertNull(handler.findElementForParameterInfo(create))
    }

    fun testParameterSelectionIncludesSwitchesAndTheirValues() {
        val info = NsisCommandMetadata.find("MessageBox")!!
        assertTrue(info.signature.startsWith("MessageBox flags text [/SD value]"))
        assertEquals(0, context("MessageBox MB_OK<caret> \"Hello\"")!!.signatureIndex)
        assertEquals(1, context("MessageBox MB_OK \"Hello <caret>there\"")!!.signatureIndex)
        assertEquals(2, context("MessageBox MB_OK \"Hello\" /SD ID<caret>OK")!!.signatureIndex)
        assertEquals(3, context("MessageBox MB_OK \"Hello\" /SD IDOK ID<caret>NO")!!.signatureIndex)
        assertEquals(1, context("ExecWait \"app.exe\" <caret>")!!.signatureIndex)
        assertEquals(2, context("!define /math COUNT 1 +<caret> 2")!!.semanticIndex)
        assertTrue(NsisParameterInfoHandler().isWhitespaceSensitive)
    }

    private fun completion(text: String): Set<String> {
        val file = myFixture.addFileToProject("completion-${System.nanoTime()}.nsi", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(text.indexOf("<caret>"))
        return myFixture.completeBasic()?.map { it.lookupString }?.toSet().orEmpty()
    }

    private fun choose(name: String) {
        val lookup = myFixture.lookup!!
        lookup.currentItem = lookup.items.first { it.lookupString == name }
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    }

    fun testArgumentCompletionUsesCommandAndPosition() {
        assertContainsElements(completion("WriteRegStr H<caret>"), "HKCU", "HKLM")
        assertContainsElements(completion("ReadRegStr ${'$'}0 H<caret>"), "HKCU", "HKLM")
        assertEmpty(completion("WriteRegStr HKCU \"Software\" H<caret>"))
        assertContainsElements(completion("DeleteRegKey /ifempty H<caret>"), "HKCU", "HKLM")
        assertContainsElements(completion("RequestExecutionLevel <caret>"), "user", "admin", "highest")
        assertContainsElements(completion("SetRegView <caret>"), "32", "64", "default")
        assertContainsElements(completion("!include /CHARSET=U<caret>"), "/CHARSET=UTF8", "/CHARSET=UTF16LE", "/CHARSET=UTF16BE")
        assertContainsElements(completion("File /<caret>"), "/r", "/nonfatal", "/oname=")
        assertContainsElements(completion("File /r /<caret>"), "/nonfatal", "/oname=")
        assertFalse(completion("File /r /<caret>").contains("/r"))
        assertContainsElements(completion("!define /math COUNT 1 <caret> 2"), "+", "-", "*")
        assertContainsElements(completion("RequestExecutionLevel \\\n  <caret>"), "user", "admin")
        assertContainsElements(completion("!define APP_NAME Name\n!define APP_VERSION 1\n!include \"${'$'}{APP_<caret>}\""), "APP_NAME", "APP_VERSION")
        assertEmpty(completion("DetailPrint \"/<caret>\""))
        assertEmpty(completion("; WriteRegStr H<caret>"))
    }

    fun testCombinedMessageFlagsPreservePreviousFlags() {
        assertContainsElements(completion("MessageBox MB_OK|MB_ICON<caret> \"Hello\""), "MB_ICONSTOP", "MB_ICONQUESTION")
        choose("MB_ICONQUESTION")
        assertTrue(myFixture.editor.document.text.contains("MB_OK|MB_ICONQUESTION \"Hello\""))
        assertContainsElements(completion("MessageBox MB_OK \"Hello\" /SD ID<caret>"), "IDOK", "IDNO")
        assertContainsElements(completion("MessageBox MB_OK \"Hello\" /SD IDOK ID<caret>"), "IDOK", "IDNO")
    }

    fun testIncludesUseOrderedRootsAndDirectoryPrefixes() {
        myFixture.addFileToProject("local.nsh", "")
        myFixture.addFileToProject("headers/local.nsh", "")
        myFixture.addFileToProject("headers/second.nsh", "")
        myFixture.addFileToProject("headers/nested/child.nsh", "")
        val configured = myFixture.addFileToProject("configured/extra.inc", "")
        val sdk = myFixture.addFileToProject("sdk/Include/sdk.nsh", "")
        NsisSettings.get(project).update(listOf(configured.virtualFile.parent.path), sdk.virtualFile.parent.parent.path)
        val items = completion("!addincludedir headers\n!include /NONFATAL \"<caret>\"")
        assertContainsElements(items, "local.nsh", "second.nsh", "extra.inc", "sdk.nsh", "nested/")
        assertEquals(1, myFixture.lookup!!.items.count { it.lookupString == "local.nsh" })
        assertContainsElements(completion("!include \"headers/ne<caret>\""), "nested/")
        choose("nested/")
        assertTrue(myFixture.editor.document.text.contains("\"headers/nested/\""))
        assertEquals(myFixture.editor.document.text.indexOf("/\"") + 1, myFixture.caretOffset)
        assertContainsElements(completion("!include \"headers\\nested\\ch<caret>\""), "child.nsh")
        choose("child.nsh")
        assertTrue(myFixture.editor.document.text.contains("\"headers\\nested\\child.nsh\""))
        assertFalse(completion("!include sec<caret>\n!addincludedir headers").contains("second.nsh"))
        assertEmpty(completion("!include \"${'$'}{ROOT}/he<caret>\""))
    }

    fun testIncludeInsertionPreservesQuotesAndSurroundingText() {
        myFixture.addFileToProject("my header.nsh", "")
        myFixture.addFileToProject("my help.nsh", "")
        for (quote in listOf("\"", "'", "`", "")) {
            val original = "!include /NONFATAL ${quote}my<caret>${quote} ; keep this"
            assertContainsElements(completion(original), "my header.nsh", "my help.nsh")
            choose("my header.nsh")
            val expectedQuote = quote.ifEmpty { "\"" }
            assertEquals("!include /NONFATAL ${expectedQuote}my header.nsh${expectedQuote} ; keep this", myFixture.editor.document.text)
        }
        assertContainsElements(completion("!include \"my<caret>"), "my header.nsh", "my help.nsh")
        choose("my header.nsh")
        assertEquals("!include \"my header.nsh\"", myFixture.editor.document.text)
        assertContainsElements(completion("!include \"my he<caret>lp.nsh\" ; tail"), "my header.nsh", "my help.nsh")
        choose("my header.nsh")
        assertEquals("!include \"my header.nsh\" ; tail", myFixture.editor.document.text)
    }

    fun testAbsoluteIncludesAndQuotedDirectoryTraversal() {
        val header = myFixture.addFileToProject("absolute/first.nsh", "")
        myFixture.addFileToProject("absolute/final.nsh", "")
        val path = header.virtualFile.parent.path
        assertContainsElements(completion("!include \"$path/f<caret>\""), "first.nsh", "final.nsh")
        choose("first.nsh")
        assertEquals("!include \"$path/first.nsh\"", myFixture.editor.document.text)
        myFixture.addFileToProject("space dir/child.nsh", "")
        assertContainsElements(completion("!include spa<caret>"), "space dir/")
        choose("space dir/")
        assertEquals("!include \"space dir/\"", myFixture.editor.document.text)
        assertEquals(myFixture.editor.document.textLength - 1, myFixture.caretOffset)
        val next = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(next, "child.nsh")
        choose("child.nsh")
        assertEquals("!include \"space dir/child.nsh\"", myFixture.editor.document.text)
    }
}
