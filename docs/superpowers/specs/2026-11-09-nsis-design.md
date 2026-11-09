# NSIS Language Support

Implement the user-approved fresh native Kotlin plugin for .nsi/.nsh editing, using
JFlex and Grammar-Kit, shared platform APIs, and conservative cross-file analysis.
Target platform branches 262–263, Java 25, and validate IDEA 2026.3 and Rider
2026.2.4.1. No compilation, full preprocessing, rename, formatting, remote creation,
or publication. Display name NSIS Language Support; ID dev.nsis.jetbrains; MIT.

Provide highlighting, completion, definition navigation, folding, structure view,
comments and reliable syntax diagnostics. Include settings accept ordered roots and
an optional SDK; dynamic preprocessing remains unresolved without false errors.
Cache include graphs with cycle handling and invalidation. Test language behavior,
package a ZIP, verify binary compatibility, and document actual validation limits.

Approaches considered: extend the archived highlighting plugin or build fresh with
native PSI. User selected fresh native implementation to support deeper features.
