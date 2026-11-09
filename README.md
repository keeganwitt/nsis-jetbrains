# NSIS Language Support

Native [NSIS](https://nsis.sourceforge.io/) editing support for JetBrains IDEs.
Recognizes `.nsi` installer scripts and `.nsh` headers. No compiler or NSIS SDK is
required for basic editing.

NSIS 3 is the primary language reference. Shared NSIS 2 syntax and legacy forms
are accepted; the editor does not flag commands as invalid for a compiler version.
The completion catalog includes NSIS 3 additions such as `Unicode` and UTF-16 file
instructions. It is not filtered to an NSIS 2 compiler. See the NSIS 2 and NSIS 3
scripts in `examples/` and the official [release notes](https://nsis.sourceforge.io/Docs/AppendixF.html).
Configure legacy script encodings through the IDE's file-encoding controls; the
plugin does not infer compiler-specific encoding or ANSI/Unicode defaults.

## Features

- Highlighting for commands, directives, strings, interpolation, comments, variables and labels.
- Context-sensitive completion for built-in commands, directives and variables, plus visible declarations.
- Go to definition for functions, labels, macros, defines, user variables and literal includes.
- Function-local and section-local labels, macro parameters, and multiple navigation targets for ambiguous definitions.
- Function, section and macro structure view; block folding; line and block comments.
- Conservative diagnostics for unterminated strings/comments and clearly unmatched blocks.

The implementation uses shared IntelliJ Platform APIs. The declared compatibility
range is branches 262–263 (2026.2–2026.3). IDEA 2026.3 and Rider 2026.2.4.1 are the
pinned verification targets; see [validation](docs/validation.md) for checks actually
performed. Other compatible JetBrains products have not been individually verified.

## Install

1. Disable any other NSIS plugins to avoid conflicts over `.nsi` and `.nsh` files.
2. Open **Settings → Plugins → gear menu → Install Plugin from Disk**.
3. Select `nsis-language-support-0.1.0.zip` from the build output, then restart if prompted.
4. Open an `.nsi` or `.nsh` file. If it was associated with plain text, use **Associate with File Type → NSIS**.

Use **Settings → Languages & Frameworks → NSIS** to configure ordered include
directories and an optional NSIS SDK root. The SDK's `Include` directory is searched
last. Relative settings paths are relative to the project directory. An SDK is
optional and is never executed.

Use **Settings → Editor → Color Scheme → NSIS** to customize highlighting.

## Static analysis boundaries

Includes are followed only when their paths are literal. Search order is the
containing file's directory, preceding literal `!addincludedir` entries in source
order, configured directories, then the SDK `Include` directory. This is an editor
approximation: the compiler's working directory and preprocessing can produce
different results. Relative includes inside headers are resolved against the
header's directory. Paths containing `$` are left unresolved.

Completion and navigation use the current file and its transitive include graph;
unrelated project scripts are excluded. Conditional declarations may provide more
than one navigation target. Header files are analyzed from their own context, not
every possible script that includes them. Conditional branches are not evaluated,
macros are not expanded, and generated identifiers are not resolved.
Local labels from included files are excluded when their containing scope cannot
be established; explicitly global labels remain available.

Unknown commands and unresolved references are accepted. Structural block errors
are suppressed in files with conditionals, macros, macro calls, or includes because
those constructs can generate block boundaries. Lexical diagnostics still apply.
Header files (`.nsh`) can be fragments of another script, so they also suppress
standalone block-boundary diagnostics.
The plugin does not run `makensis` or execute preprocessor commands. Compilation,
rename refactoring, automatic formatting and full preprocessing are outside v0.1.

## Develop

Requires JDK 25. The wrapper downloads Gradle 9.4.0 and the build pins the 2026.2.4
IntelliJ Platform SDK. From PowerShell:

```powershell
.\gradlew.bat test buildPlugin
.\gradlew.bat verifyPlugin
.\gradlew.bat runIde
```

The installable ZIP is in `build/distributions/`. Generated lexer/parser sources
are in `build/generated/` and are regenerated from `src/main/grammar/` during builds.
Tests cover lexer recovery, completion, scopes, references, include cycles and
cache invalidation. CI runs Windows/Linux tests and packaging, plus verification
against the pinned IDEA and Rider versions.

To use an existing local platform installation:

```powershell
.\gradlew.bat test buildPlugin '-PlocalIdePath=C:\path\to\Rider'
.\gradlew.bat verifyPlugin '-PverifyIdeaPath=C:\path\to\IDEA' '-PverifyRiderPath=C:\path\to\Rider'
```

Add `-PverifierOffline=true` to restrict Plugin Verifier dependency resolution to
the installed IDEs and its local cache. Missing dependencies remain visible in its
reports; this does not disable compatibility checks.

## References and license

Licensed under the [MIT license](LICENSE).
Command names and language behavior follow the official NSIS [scripting reference](https://nsis.sourceforge.io/Docs/Chapter4.html)
and [preprocessor reference](https://nsis.sourceforge.io/Docs/Chapter5.html).
