# Card catalog — `cards-neutral-1`

The game reads one file, `src/main/resources/cards/catalog.txt`; no code names a card. Its
version, `cards-neutral-1`, is recorded with every match result. `CatalogLoader` validates it
at startup: every nation has exactly one Unit of each class, a class prints the same points in
every nation, Spells and Landmarks are neutral, and there are at least five Landmarks to offer.

## Units — approved initial playtest values

Seven nations (Hungary, Poland, Bohemia, Austria, Styria, Croatia, Transylvania) × five
classes = 35 Units. Each Unit has exactly one nation, one class and no ability.

| Class | Printed points |
| --- | --- |
| Footmen | 1 |
| Pikemen | 2 |
| Bowmen | 3 |
| Riders | 4 |
| Knights | 5 |

Chaplain and Envoy, and the earlier Infantry/Cavalry/Archer/Clergy/Noble tags, are retired.

## Deck — approved

Every player receives the same 46 cards: all 35 Units and one copy of each of the 11 Spells,
shuffled independently by the server (`DeckRules.deck`). Landmarks are not in the deck.
Players never build or choose a deck.

## Spells — neutral, one copy each

| Card | Effect (engine) | Target |
| --- | --- | --- |
| Disable Tags | `DISABLE OPPONENT TAGS` until the round ends | an enemy Unit |
| Disable Points | `DISABLE OPPONENT POINTS` this round; its combos still count | an enemy Unit |
| Destroy Unit | `DESTROY OPPONENT`: the Unit leaves the row with its points and combos | an enemy Unit |
| Random Discard | `RANDOM_DISCARD 1` from the enemy hand (server picks) | none |
| Random Steal | `RANDOM_STEAL 1` from the enemy hand into yours (server picks) | none |
| Draw Two | `DRAW 2` from your deck (not a manual draw) | none |
| Exchange | `EXCHANGE 1 2`: discard the chosen card, then draw 2 | another card in your hand |
| Reconnaissance | `RECONNAISSANCE 3`: look at the top 3, keep 1, the rest to the bottom | none; then a private keep choice |
| Sabotage | `SABOTAGE`: the Landmark's effect is off until the round ends | an enemy Landmark |
| Spy | `SPY 3`: three random enemy hand cards are revealed to you only | none |
| Clear Disable | `CLEAR_DISABLE`: removes the disabled points and tags | one of your disabled Units |

A Spell whose target type has no legal target may still be cast, to no effect, so a hand of
Spells never leaves a player without a play. Destroy Landmark does not exist.

## Landmarks — neutral; numbers are PROVISIONAL

| Card | Effect (engine) | Provisional value |
| --- | --- | --- |
| Embassy | `SHARED_TAG_BONUS REGION 3` | +3 to each scoring pair sharing a nationality |
| Guild Hall | `SHARED_TAG_BONUS CLASS 3` | +3 to each scoring pair sharing a class |
| Armory | `WEAK_UNIT_BONUS 2 1` | Units printing 1–2 get +1 printed point |
| Ruined Keep | `EMPTY_SPACE_BONUS 2` | +2 per empty Unit space |
| Archive | `FIRST_SPELL_DRAW 1` | your first Spell each round also draws 1 |
| Watchtower | `DECK_TOP_VIEW` | you privately see your own deck's top card |

The base all-seven-nations combo (+15, `Scoring.ALL_NATIONS_BONUS`) is part of scoring, not a
Landmark. Every provisional value and edge-case ruling is listed in AGENTS.md under
"Provisional implementation values"; change the numbers here, not in code (except the
seven-nation constant).

## Score order

Row total = printed points (with Armory) + pair bonuses (with Embassy/Guild Hall) + the
seven-nation combo + Ruined Keep's bonus. A sabotaged Landmark contributes nothing until the
round ends. A Unit with disabled tags makes no pairs and does not count toward the seven
nations; disabled points remove its printed points including Armory's.

## Artwork

Unit art: `docs/design/card-art/` manuscript set (sources in
`src/main/resources/assets/cards/manuscript-v1/`, excluded from the jar). Spell and Landmark
art: `docs/design/card-art/drafts/neutral-v4/`. Runtime copies (320×480) live in
`src/main/resources/assets/cards/runtime/`:

```sh
java tools/ResizeArt.java src/main/resources/assets/cards/manuscript-v1 src/main/resources/assets/cards/runtime 320 480 jpg
java tools/ResizeArt.java docs/design/card-art/drafts/neutral-v4/spell-set src/main/resources/assets/cards/runtime 320 480 jpg
java tools/ResizeArt.java docs/design/card-art/drafts/neutral-v4/landmark-set src/main/resources/assets/cards/runtime 320 480 jpg
```

The neutral art prints its own kind, title and description, so the table adds no text over
it. Retired art is archived in `docs/design/card-art/retired/`.
