# Executed checks — 2026-10-21

Environment: macOS aarch64, Java 26.0.1; standalone draft sources compiled with
`javac --release 17`. No runtime source, catalog or resource changes in this task.

| Input / check | Expected | Actual | Status |
| --- | --- | --- | --- |
| Five Poland IDs from current catalog | Knights reference plus four drafts; points 5, 2, 3, 4, 4 | Five exported with those values | PASS |
| Native image and exact enlargement | 80×120 / 320×480, binary alpha, opaque interior, at most 16 colours including transparency | All five passed; counts in VERIFICATION.md | PASS |
| Repeat rendering | Identical pixels | All five identical | PASS |
| Knights versus prior runtime native export | Identical pixels | Identical across all 9,600 pixels | PASS |
| Visual review | Readable figures at 3× and 104×156; still awaiting user approval | Both contact sheets inspected | PASS |
| `mvn test` with desktop / localhost access | No failures, errors or skips | 153 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS, 28.052 s | PASS |
| `git diff --check` | No whitespace errors | No output, exit 0 | PASS |

The first sandboxed Maven run could not bind localhost sockets or initialize the
JavaFX library cache. The full rerun with those permissions succeeded as recorded above.
Images are offline Java2D previews, not screenshots from a live match.
