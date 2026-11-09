package dev.nsis.jetbrains

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lexer.FlexAdapter
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import dev.nsis.jetbrains.lexer._NsisLexer
import dev.nsis.jetbrains.parser.NsisParser
import dev.nsis.jetbrains.psi.NsisTypes

class NsisFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, NsisLanguage) {
    override fun getFileType() = NsisFileType
}

open class NsisLexer : FlexAdapter(_NsisLexer(null))

class NsisParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?) = NsisLexer()
    override fun createParser(project: Project?) = NsisParser()
    override fun getFileNodeType() = FILE
    override fun getCommentTokens() = TokenSet.create(
        NsisTokens.LINE_COMMENT, NsisTokens.BLOCK_COMMENT, NsisTokens.UNTERMINATED_COMMENT
    )
    override fun getStringLiteralElements() = TokenSet.create(NsisTypes.STRING, NsisTypes.UNTERMINATED_STRING)
    override fun createElement(node: ASTNode): PsiElement = NsisTypes.Factory.createElement(node)
    override fun createFile(viewProvider: FileViewProvider) = NsisFile(viewProvider)

    companion object {
        val FILE = IFileElementType(NsisLanguage)
    }
}
