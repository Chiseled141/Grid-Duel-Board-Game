# Leaderboard and skill-only display — 2026-10-17

LeaderboardPanel now shares the menu's animated battlefield and ClientSettings.
A bounded parchment ledger uses brass rules, serif heading, alternating rows and
an emphasized local-player row. Elo/wins/losses remain server-provided and ordered;
names render literally with Swing HTML disabled. Empty/populated states share the
sheet; Back returns to the existing menu. requestLeaderboard now updates the model
screen so visibility and reduced-motion animation gates follow actual navigation.

The board-side displays accept only publicly committed skill cards. Movement cards
remain visible in the hands and use existing board animation and last-position
markers. Movement still draws replacements but no longer flies into a side display
or overwrites its previous skill. Accepted actions clear the raised-card visual;
public skill flights, hover details, concealment and lifecycle/reset rules remain.

Changed runtime: LeaderboardPanel, ClientFrame (shared settings), ClientModel
(leaderboard route), GamePanel (skill-only events), PlayedCardArea (caption fitting).
Updated RenderMedievalPreview samples show skills in the side slots. Documentation
updates clarify presentation only; no rules, protocol or database changes.

Native mvn test: 347 passed; zero failures/errors/skips. New native leaderboard
coverage checks routing, animation/reduced motion, rows/local highlight, literal
names, empty transitions, back action and supported bounds. Presentation regression
coverage checks both players' skill details, movement exclusion/board markers and
retaining the previous skill after subsequent movement. Existing draw/privacy,
selection and reconnect/lifecycle tests remain green.

Visual QA: artifacts/leaderboard.png uses local sample rows only;
artifacts/medieval-preview/game.png shows both public skill slots.

Final native mvn clean verify: 347 passed; shaded target/onitama.jar rebuilt.
Independent Agent 2 isolated native mvn test: 347 passed; no actionable findings.
Its first run overlapped the clean build and lost a temporary compiled class;
rerunning after the build completed resolved that build-directory race.
