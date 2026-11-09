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

## CI and remaining manual checks

GitHub Actions is configured for Windows/Linux tests and packaging against IDEA
2026.2.4, plus verification against the two pinned current-stable IDEs. CI results
will be recorded after the initial push.

Interactive **Install Plugin from Disk**, visual color-scheme inspection, and
click-through testing in the native IDE UI have not been performed. The archive,
language behavior, settings initialization and binary compatibility have automated
coverage. Marketplace publication is outside this delivery.
