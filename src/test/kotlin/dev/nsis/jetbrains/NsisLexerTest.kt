package dev.nsis.jetbrains

import dev.nsis.jetbrains.model.NsisInterpolations
import dev.nsis.jetbrains.model.NsisCatalog
import dev.nsis.jetbrains.psi.NsisTypes
import org.junit.Assert.*
import org.junit.Test

class NsisLexerTest {
    private fun tokens(text: String): List<Pair<String, String>> {
        val lexer = NsisLexer()
        lexer.start(text)
        val result = mutableListOf<Pair<String, String>>()
        while (lexer.tokenType != null) {
            check(lexer.tokenEnd > lexer.tokenStart) { "Lexer did not advance" }
            result.add(lexer.tokenType.toString() to lexer.tokenText)
            lexer.advance()
        }
        assertEquals(text, result.joinToString("") { it.second })
        return result
    }

    @Test fun directivesAndPluginCommands() {
        val result = tokens("!include MUI2.nsh\nnsExec::Exec test\n")
        assertEquals(NsisTypes.DIRECTIVE.toString(), result.first().first)
        assertTrue(result.contains("WORD" to "nsExec::Exec"))
    }

    @Test fun quotesEscapesAndInterpolation() {
        val text = "MessageBox MB_OK \"${'$'}\\\"Hi; # ${'$'}INSTDIR ${'$'}{APP}${'$'}\\\"\" 'single' `backtick`\n"
        val result = tokens(text)
        assertEquals(3, result.count { it.first == "STRING" })
        assertFalse(result.any { it.first.contains("COMMENT") })
        assertEquals(listOf("INSTDIR", "APP"), NsisInterpolations.scan(result.first { it.first == "STRING" }.second).map { it.name })
        assertTrue(NsisInterpolations.scan("${'$'}${'$'}literal ${'$'}\\n").isEmpty())
    }

    @Test fun continuationsAndComments() {
        val result = tokens("Name \\\r\n \"Example\"\r\n; comment \\\r\ncontinued\r\n/* block\n comment */\n")
        assertTrue(result.any { it.first == "WHITE_SPACE" && it.second.contains("\r\n") })
        assertTrue(result.any { it.first == "LINE_COMMENT" && it.second.contains("continued") })
        assertTrue(result.any { it.first == "BLOCK_COMMENT" && it.second.contains('\n') })
    }

    @Test fun incompleteTokensRecoverAtNextLine() {
        val result = tokens("Name \"unfinished\nFunction Next\nFunctionEnd\n/* unfinished")
        assertTrue(result.any { it.first == "UNTERMINATED_STRING" })
        assertTrue(result.contains("WORD" to "Function"))
        assertEquals("UNTERMINATED_COMMENT", result.last().first)
    }

    @Test fun arbitraryInputAlwaysAdvances() {
        val random = java.util.Random(73)
        repeat(100) {
            tokens(buildString { repeat(500) { append(random.nextInt(128).toChar()) } })
        }
    }

    @Test fun catalogContainsCommandsRatherThanDocumentationHeadings() {
        assertTrue(NsisCatalog.commands.containsAll(listOf("Unicode", "FileWriteUTF16LE", "SetCompressor", "Function")))
        assertTrue(NsisCatalog.commands.none { it in setOf("Ordering", "Constants", "Callbacks", "UninstPageCallbacks") })
    }

    @Test fun escapedFinalQuoteDoesNotCloseAString() {
        for (quote in charArrayOf('"', '\'', '`')) {
            val result = tokens("DetailPrint $quote" + "abc" + '$' + '\\' + quote + "\n")
            assertTrue("Escaped $quote was treated as a closer", result.any { it.first == "UNTERMINATED_STRING" })
        }
    }

    @Test(timeout = 2000) fun malformedInterpolationScansLinearlyAndRecoversVariables() {
        val text = "${'$'}{".repeat(1_000_000) + " ${'$'}after"
        assertEquals(listOf("after"), NsisInterpolations.scan(text).map { it.name })
    }
}
