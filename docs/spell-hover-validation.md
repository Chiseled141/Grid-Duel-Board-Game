# Spell hover validation — 2026-10-23

Environment: Windows, Temurin JDK 21.0.10, Java 17 compilation target,
OpenJFX 21.0.5, graphical session. JavaFX table tests run at 1280 × 720.

`CardTableViewTest.hoveringEverySpellShowsReadableRulesWithoutSendingAMove`
supplies all ten catalog Spells in a recipient-visible hand and moves the pointer
onto each card. Expected and actual results agree (PASS):

| Input | Expected and actual result |
|---|---|
| Hover each Spell, wait 550 ms | Name and full rules match the catalog definition; panel is inside the window |
| Move away from a Spell | Panel disappears |
| Press a Spell | Panel disappears; card input is not intercepted |
| Receive a newer view while a hover is pending | Old hover is canceled |
| Hover without clicking | No move is sent |

Initial `mvn test`: 159 tests, one failure in an obsolete compiled
`PixelSpellArtTest` absent from the source tree. `mvn clean verify` could not
delete `target/deck-duel.jar` because the running game holds it open on Windows.

Clean verification used a temporary copy of `pom.xml` at the repository root,
named `.spell-hover-build.xml`, with only the build directory changed to
`${project.basedir}/target/spell-hover`. Command:
`mvn -f .spell-hover-build.xml clean verify`.

Result: BUILD SUCCESS, **155 tests, 0 failures, 0 errors, 0 skipped**;
23 tests in `CardTableViewTest`. The temporary POM was removed after the run.
Reports remain in `target/spell-hover/surefire-reports`; packaged application:
`target/spell-hover/deck-duel.jar`. Normal clean builds use `mvn clean verify`
after the game processes have released the default jar.
