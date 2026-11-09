# Validation

This file records executed checks, separately from configured CI checks.

## Executed on Windows, November 9, 2026

| Check | Result |
| --- | --- |
| Editor and lexer tests in IDEA 2026.3 | 28 passed, zero failures |
| Compilation and ZIP packaging against Rider's 262 platform | Passed |
| Plugin Verifier 1.411 against IDEA 2026.3, build 263.7307.200 | Compatible |
| Plugin Verifier 1.411 against Rider 2026.2.4.1, build 262.11879.119 | Compatible |
| Headless Rider startup and settings search-index generation | Passed during packaging |
| Actual distributable ZIP loaded in an isolated Rider profile on Windows | Passed; plugin loaded and settings index generated |
| Independent source review | Five regressions reproduced and fixed; no deferred findings |

The tests use physical virtual-filesystem fixtures and exercise NSIS 3
syntax, with no NSIS compiler installed. SDK include lookup is tested with an
optional SDK directory fixture. This verifies editing behavior, not compilation of
the example installers.

Local verification used JDK 25 and installed IDEs through `localIdePath`,
`verifyIdeaPath`, and `verifyRiderPath`. Plugin Verifier ran with
`-PverifierOffline=true`; both verdicts were Compatible with no reported plugin
compatibility problems. It emitted SDK-layout warnings about absent paths in the
installed IDE metadata. The initial online run was stopped while downloading
unrelated dependencies; it is not counted as a successful verification.

## Clean CI

All three jobs in [the NSIS 3-only CI run](https://github.com/keeganwitt/nsis-jetbrains/actions/runs/42571537952)
passed for commit `1a59077bff8a698b01c661c8b4f494ddef8e9e6e`:

- Windows: 20 editor tests plus 8 lexer tests, zero failures; ZIP packaged.
- Linux: 20 editor tests plus 8 lexer tests, zero failures; ZIP packaged.
- Plugin Verifier: Compatible with both IDEA 2026.3 and Rider 2026.2.4.1.

Both CI builds use the pinned IDEA 2026.2.4 baseline SDK. Reports and plugin ZIPs
are available as workflow artifacts. The final documentation-only commit does not
change the verified implementation.

## Remaining manual checks

Interactive **Install Plugin from Disk**, visual color-scheme inspection, and
click-through testing in the native IDE UI have not been performed. An automated
Windows smoke test extracted the deliverable ZIP into an isolated plugins
directory and started Rider with separate config/system/log paths; Rider loaded
NSIS Language Support 0.1.0 and completed settings index generation. Language
behavior, settings initialization and binary compatibility have automated coverage.
Marketplace publication is outside this delivery.
