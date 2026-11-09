package dev.nsis.jetbrains.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;
import static dev.nsis.jetbrains.psi.NsisTypes.*;
import static dev.nsis.jetbrains.NsisTokens.*;

%%
%public
%class _NsisLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

NL = \r\n|\r|\n
SPACE = [ \t\f]+
CONT = \\[ \t]*{NL}
ESC = \$\\[^\r\n]
DQBODY = ([^\"\r\n$]|{ESC}|\$[^\\\"\r\n]|{CONT})*
SQBODY = ([^'\r\n$]|{ESC}|\$[^\\'\r\n]|{CONT})*
BQBODY = ([^`\r\n$]|{ESC}|\$[^\\`\r\n]|{CONT})*
IDENT = [a-zA-Z_0-9.]+
BLOCK = [/][*]([^*]|[*]+[^*/])*[*]+[/]
OPENBLOCK = [/][*]([^*]|[*]+[^*/])*[*]*
WORDPART = [^ \t\f\r\n\"'`$;#!]

%%
{SPACE}                    { return TokenType.WHITE_SPACE; }
{CONT}                     { return TokenType.WHITE_SPACE; }
{NL}                       { return NEWLINE; }
[;#]([^\r\n]|{CONT})*      { return LINE_COMMENT; }
{BLOCK}                    { return BLOCK_COMMENT; }
{OPENBLOCK}                { return UNTERMINATED_COMMENT; }
\"{DQBODY}\$?\"          { return STRING; }
'{SQBODY}\$?'              { return STRING; }
`{BQBODY}\$?`              { return STRING; }
\"{DQBODY}\$?             { return UNTERMINATED_STRING; }
'{SQBODY}\$?               { return UNTERMINATED_STRING; }
`{BQBODY}\$?               { return UNTERMINATED_STRING; }
\$\{[^}\r\n]*\}          { return DEFINE_REF; }
\$\([^\)\r\n]*\)         { return LANG_REF; }
\$%[^%\r\n]*%             { return VARIABLE; }
\${IDENT}                  { return VARIABLE; }
{ESC}                      { return SYMBOL; }
"!"[a-zA-Z_]+             { return DIRECTIVE; }
{WORDPART}+                { return WORD; }
[^]                        { return SYMBOL; }
