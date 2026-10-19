# Claude handoff — clean card-game starting point

Read `AGENTS.md`, then `gameplay-migration-brief.md`. The user requested a full retirement of the old gameplay. This branch now has an account/lobby shell, not a working card game.

## Keep

- The medieval menu: appearance, artwork, animation, music, navigation, account display, and remaining settings.
- Login, registration, PBKDF2 passwords, local TCP transport, rooms, rankings, and SQLite account/history data.
- `DeckPanel`: the renamed Atlas page, retaining its historical region map and banners. Deck selection and the card catalog are not implemented.
- `CardTablePanel`: accessible through Play → Practice → Preview table. It shows the existing room background and two empty eight-space rows.
- `DuelScorePanel`: real Swing score presentation with a large total, Units + Combos tiles, effects, counters, and cosmetic feedback. It has no game arithmetic or server connection. The local preview remains in `docs/design/`.
- Historical match persistence and its rollback tests, Elo, serialization allowlisting, menu tests, and shell integration tests.

## Removed

The entire 7×7 engine, movement deck, skills, mana, pieces, targeting, Foresight, opening-hand and old action animations; the old config/artwork and generation tools; practice/load-test bots; MatchSession; gameplay, chat, rematch and match-reconnect payloads; and tests asserting those retired features. Their old test count is no longer a target.

Only historical `core.PlayerColor`/`core.EndReason` and `server.MatchResult` remain to support the existing DB format. They are not a template for the new game model. No schema/data reset was performed.

## Runtime boundaries

Protocol 8 is intentionally incompatible with old gameplay peers. It carries accounts, room directory, ranking and heartbeat messages only. There is no game, queue, match executor or reconnect token. Join requests return a useful not-ready error without consuming the room. Find-match requests return searching=false and a not-ready error; cancellation remains safe. No fake game or bot is created. The demo/bots commands exit with code 2 until reimplemented. Legacy --config is rejected by the server.

Start implementation with the new card contracts and deterministic scoring/round rules. Define the actual new gameplay payloads before adding them to the allowlist. Wire server, Swing, practice, bots, demo, reconnect, chat and rematch to the new core in bounded steps. Keep account and historical persistence behavior intact.

The full card library remains undecided. Do not silently turn the mockup's generic cards or sample scores into card definitions.
