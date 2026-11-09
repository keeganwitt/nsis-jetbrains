# NSIS Language Support

Implement the user-approved fresh native Kotlin plugin for .nsi/.nsh editing, using
JFlex and Grammar-Kit, shared platform APIs, and conservative cross-file analysis.
Target platform branches 262–263, Java 25, and validate IDEA 2026.3 and Rider
2026.2.4.1. No compilation, full preprocessing, rename, formatting, or Marketplace
publication. Display name NSIS Language Support; ID dev.nsis.jetbrains; MIT.
Repository: https://github.com/keeganwitt/nsis-jetbrains.

The language target is NSIS 3 only; NSIS 2 compatibility is outside scope.

Provide highlighting, completion, definition navigation, folding, structure view,
comments and reliable syntax diagnostics. Include settings accept ordered roots and
an optional SDK; dynamic preprocessing remains unresolved without false errors.
Cache include graphs with cycle handling and invalidation. Test language behavior,
package a ZIP, verify binary compatibility, and document actual validation limits.

Architecture: original native Kotlin implementation with PSI supporting
completion, navigation, and diagnostics.
