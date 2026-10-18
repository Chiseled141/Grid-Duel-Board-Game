# Observation → requirement → repository decision

The spec is authoritative; observations below never grant permission to adopt
LoR rules. REA evidence abbreviations refer to the complete IDs in
[`evidence-index.json`](evidence-index.json). `S` entries are supplemental analyst
byte observations, **not** REA Evidence or execution traces.

| Observation and strength | Matching Grid Duel requirement | Repository inspection / gap | Adaptation and decision |
| --- | --- | --- | --- |
| R1: executable has separate typed icon, version and manifest resources (static resource-table observation only). | §6: retain own art/fallbacks; render names, costs and descriptions from live definitions rather than baked artwork. | `CardArt.createSkill` already separates name/cost and illustration, but omitted `SkillText` entirely. `SkillCardPanel.updateTooltip` alone exposed live descriptions. Existing `MedievalPresentationTest` explicitly enforced that omission. | Keep all Grid Duel art and Java2D rendering. **Fill §6 gap:** print live descriptions on the face; preserve full details. Resource separation motivates checking the boundary, not copying LoR resources or claiming LoR card layout. |
| S1: manifest declares per-monitor DPI awareness; no window behavior measured. | §6: enlarged usable board/hands; Swing-only UI and non-blocking EDT. | `GamePanel`/`CardHandPanel` use actual layout bounds; rendering uses vector geometry/scaled faces. Baseline `GamePanelLayoutTest` has five failures at supported sizes. | No LoR manifest or Unity dependency. Existing layout failures are documented as unresolved pre-existing issues, not evidence of LoR behavior or permission for unrelated layout refactoring. |
| S2: DLL contains generic Unity tooltip API strings, without call-site or runtime evidence. | §6: live card tooltips and context guidance; no Card details tab. Step 3 §9: escape plain text before HTML. | `CardDetails` supplies non-modal details; `SkillCardPanel` escapes name/category/rendered rule text. Tooltip assertions pass. A tooltip is not a replacement for §6's on-face description. | Retain current details and escaping. No guessed hover duration, LoR tooltip styling, timing or API copied. New overflow test preserves complete escaped details. |
| R2: DLL is native x64 PE with no CLI metadata; native analysis unavailable (R3). | §§0, 3, 7: Java 17/Maven/Swing, pure core, fixed architecture/dependencies. | `pom.xml` targets release 17 with only the permitted SQLite/JUnit dependencies; `RulesEngine` remains pure. | **No change.** Unity/native architecture is not an adaptation target. No decompiled code or native bridge imported. |
| R4/S3: BBM unsupported by REA; supplemental candidate table names 21319 `.bbq` paths, with an undecoded tail. No semantic contents or card rules recovered. | §§2.3–2.7, Appendix B: fixed movement recycling, permanent skills, mana/targeting/Foresight/privacy; §6 owns card presentation. | `RulesEngine.applyMove` returns→shuffles→draws; `applyCastSkill` consumes skills; `applyResolveForesight` resolves a private committed choice. `CardBackPanel` takes no hidden definition/ID. Focused existing deck, Foresight, privacy and protocol tests cover these boundaries. | **No change.** Do not guess hashed bundles or infer LoR mana, stacks, timing, deck rules or hidden-information semantics. No LoR-specific behavioral mapping is possible on this evidence. |

## Implementation boundary

- Runtime: only `src/main/java/onitama/client/ui/CardArt.java`.
- Regression coverage: only `src/test/java/onitama/client/ui/MedievalPresentationTest.java`.
- Data source remains the match's immutable `SkillDefinition` through `SkillText`.
  The existing full-definition cache key includes descriptions/parameters, so
  distinct match definitions cannot reuse stale text.
- Own/public faces share the renderer; concealed opponent cards still render only
  `CardArt.backFace()` through identity-free `CardBackPanel`. Public skill faces
  are shown only on accepted commitment by the unchanged presentation path.
- No game rules, new mechanics, new assets, dependencies or protocol fields.
