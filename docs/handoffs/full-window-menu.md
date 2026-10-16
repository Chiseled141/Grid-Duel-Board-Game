# Full-window menu — 2026-10-17

Removed the in-window Game/Help menu bar and its full-width strip. Screens occupy
the complete window content area; normal desktop window controls remain. The
music control is the existing transparent speaker toggle, floating upper-right
on menu screens. It retains the saved preference and audio worker; persistence
runs off the EDT. The match keeps its existing Menu and Surrender controls.

Home footer stacks account, Leaderboard, How to Play and About as small text links.
The latter two open the existing frame dialogs, including live-rule help. Reduced
Motion moves from the deleted Game menu to Settings; selecting it updates the model
and saves through the existing settings worker. Quit keeps its save-and-close path.

Removed only the animated cloth at (143,313), beside the large main flag, from
RenderBattlefieldLoop. The clean plate and its bare pole are unchanged. All other
flag, flower, cloud, windmill, seed and wisp layers remain. Regenerated
menu-battlefield.gif, battlefield.png and artifacts/menu-battlefield.mp4.
ffprobe: 1280×720, 400 frames, 16 seconds; GIF tests verify 40ms delays and looping.

Changed code: ClientFrame, LobbyPanel, RenderBattlefieldLoop; GamePanel disables
Surrender until a recipient view arrives. Removed obsolete Game-menu handlers;
existing guard tests now exercise the actual Surrender button instead.

Native `mvn test`: 342 passed; zero failures/errors/skips.
Independent Agent 2 full native suite: 342 passed, no actionable findings.
Coverage includes full scene bounds with no JMenuBar, transparent music overlay,
help/about callbacks, settings save/Reduced Motion behavior, menu bounds, current
help rules, Surrender guards and the actual regenerated animation.
Visual QA: artifacts/full-menu.png, rendered from the native frame with a local
preview profile; no account or network actions were sent.

Final `mvn clean verify`: 342 tests passed; shaded `target/onitama.jar` rebuilt.
