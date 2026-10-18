# Installed LoR reference inspection

Date: 2026-10-18. Scope: the three user-selected installed files only. **Partial
static inspection, not a reconstruction of LoR gameplay.** LoR was never launched,
attached to, or contacted. No other asset bundles were opened. No LoR source,
decompiled code, artwork, audio, or extracted resources were added to this repo.

**Follow-up:** [public-footage animation study](animation-study.md) examines
hover/drag, draw, play and targeting from an actually acquired 2022 recording.
It has separate [video provenance](video-evidence-index.json); its historical
visual observations are not attributed to these installed binaries or to REA.

## Findings and evidence

REA 6.7.0 was connected to OpenCode 2.0.32 and actually called through MCP.
[`rea-evidence.json`](rea-evidence.json) contains its three complete successful
Evidence records: native PE resource metadata, the managed-classification record,
and its linked summary. Resource payloads are **not** in that export.
[`evidence-index.json`](evidence-index.json) records hashes, failed operations,
resource ranges and the separate supplemental byte-inspection provenance.

| Input | Bytes | SHA-256 |
| --- | ---: | --- |
| `C:\Riot Games\LoR\live\Game\LoR.exe` | 683928 | `ae020946a0e9e469975a965c0463b4227382558570029b7b437b02233bc98252` |
| `C:\Riot Games\LoR\live\Game\GameAssembly.dll` | 56072928 | `93673081f7c74805d1d2ffdd45bac8c101d1b7c56e007e852ccee1edc5edea9f` |
| `C:\Riot Games\LoR\live\PatcherData\PatchableFiles\AssetBundles\lor.bbm` | 3172744 | `7a40bda618665ff432db7c763c248730757e915002f286a550bb92d1e4177731` |

1. **Observed by REA:** `LoR.exe` is an x64 PE32+ image. Its resource tree has
   twelve leaves: nine icons, one icon group, one version resource, one manifest.
   The inventory examined 28 directory entries. Icon references resolve with
   matching declared sizes; neither images nor live resource loading were decoded.
2. **Observed by REA:** `GameAssembly.dll` is an x64 PE32+ image with seven
   sections and **no CLI data directory**. Managed metadata/CIL recovery is
   unavailable. This does not establish its gameplay architecture or prove that
   the whole installation lacks managed metadata elsewhere.
3. **Observed limitation:** native opening with Ghidra returned
   `provider_unavailable`: `GHIDRA_INSTALL_DIR` is unset. Hopper is unsupported
   on Windows, IDA and Kuna are unconfigured; Kuna also does not admit PE here.
   No native functions were decompiled. No engine was installed or configured.
4. **Observed limitation:** opening `lor.bbm` returned `target_unavailable`,
   `unsupported_target`, `file_format_rejected`. REA did not inventory or extract
   its contents. No successful BBM Evidence ID exists.
5. **Supplemental, not REA:** using REA-established executable resource ranges,
   a read-only Python script extracted only the manifest and version payload to
   the external analysis directory. The decoded manifest identifies Unity and
   declares per-monitor DPI awareness; that declaration is not observed DPI behavior.
   Filtered ASCII runs in the DLL include Unity engine tooltip entry-point names;
   these generic engine names do **not** prove LoR uses those APIs or any particular
   card-tooltip design. No gameplay method bodies or card descriptions were recovered.
6. **Supplemental candidate structure, not a validated BBM decoder:** the first
   two little-endian words are 1 and 21319. Treating the second as a count and
   the following bytes as 7-bit-length-prefixed UTF-8 strings decodes 21319
   `.bbq` path candidates through offset 810104. Three non-hash-style names are
   `alwaysloaded.bbq`, `monobehavior.bbq`, and `shaders.bbq`. **2362640 bytes remain
   undecoded.** The first word's meaning, tail schema, dependencies, semantic roles,
   compression and resource contents remain unknown. No relevant specific card/UI
   bundle could be identified safely, so none was guessed or bulk-extracted.

## External analysis outputs

All extracted material and broad string/path inventories remain outside the repo:

`C:\Users\Kem\AppData\Local\Temp\opencode\lor-analysis-20261018\`

- `rea-evidence.json`: original MCP export (3 records, 17606 bytes).
- `input-hashes.json`: full paths, lengths, modification times and SHA-256.
- `inspect_readable.py`: analyst-authored static inspection/extraction script.
- `LoR.manifest.xml`, `LoR.version.bin`: non-art payloads extracted using the
  offsets in REA Evidence; not copied into the project.
- `*.readable.json`, `readable-summary.json`: complete-file ASCII runs of at
  least five printable bytes, filtered by the recorded keyword regex, with offsets.
  Binary/compressed/UTF-16 content is not covered; negative hits prove no absence.
- `bbm-candidate-path-table.json`: offset-bound candidate table, not an asset dump.

Reproduction: hash the three files with `Get-FileHash -Algorithm SHA256`;
call `inspect_pe_resources({path: LoR.exe})` and
`inspect_managed_artifact({path: GameAssembly.dll, detail: "summary"})`;
record the failures from `open_binary` for BBM and native DLL; export Evidence
with `export_evidence_bundle`. The external Python script reads only the three
selected files and writes only beside itself. Its initial stricter candidate-name
assumptions rejected `_scene` and named paths; the final parser admits all observed
safe printable `.bbq` names. That refinement is not proof of the rest of the format.

## Grid Duel decision

See [`requirement-mapping.md`](requirement-mapping.md) for the observation-to-spec
mapping, inspected implementation paths and no-change decisions. No LoR-specific
gameplay behavior was established, so no LoR rules are proposed or imported.

One independently verified **Grid Duel spec §6 gap** is corrected: skill faces
previously painted only name/cost/art/category while live rules existed exclusively
in inspection popups. `CardArt` now prints `SkillText.render(definition)` in a
bounded body region, retaining current project art, category ornaments, cached
per-definition faces and unabridged escaped popup details. Long custom descriptions
show an explicit details notice rather than overlapping the footer. The old test
requiring descriptions to remain off-face contradicted §6 and was replaced with
meaningful live-description, parameter-change and overflow/escaping coverage.

This is a spec correction, **not a claim that LoR renders cards this way**.
No changes to rules, phases, mana, recycling, private views, socket code, dependencies,
or card identity are needed. Historical architecture prose saying descriptions
appear only in popups is superseded for this one rendering path by this correction.

## Validation and unresolved work

See [`validation.md`](validation.md) for actual commands, results and review.
The initial worktree was clean. Baseline `mvn test` already failed before repo
edits: 563 tests, five `GamePanelLayoutTest` failures and twelve DB test temporary
file cleanup errors on Windows. These unrelated failures must not be hidden by
changing assertions, disabling tests, or broadening this task into DB/layout work.

Unresolved: native code semantics, BBM's remaining schema/resources, live LoR
behavior, and any deeper feature comparison. A configured native provider and a
supported BBM/Unity resource decoder would be needed for that follow-up; target
execution remains outside this request. This report is not a full spec audit.
