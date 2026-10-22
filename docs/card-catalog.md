# Card catalog — `cards-neutral-4`

The game reads one file, `src/main/resources/cards/catalog.txt`; no code names a card. Its
version, `cards-neutral-4`, is recorded with every match result. `CatalogLoader` validates it
at startup: every nation has exactly one Unit of each class, a class prints the same points in
every nation, and Spells are neutral. Landmarks and the Sabotage Spell were removed on
2026-10-21; matches recorded under `cards-neutral-2` and earlier had them.

## Units — approved initial playtest values

Seven nations (Hungary, Poland, Bohemia, Austria, Styria, Croatia, Transylvania) × five
classes = 35 Units. Each Unit has exactly one nation, one class and no ability.

| Class | Printed points |
| --- | --- |
| Pikemen | 2 |
| Bowmen | 3 |
| Riders | 4 |
| Knights | 5 |
| Axemen | 4 |

Axemen (4 points) replaced Battering Ram (6) in every nation on 2026-10-21; Battering Ram had
replaced Footmen (1) on 2026-10-20. Matches recorded under `cards-neutral-1` used Footmen and
under `-2`/`-3` Battering Ram. Riders and Axemen both print 4 but are different classes: pairs
and formations compare class tags, never points. Unit faces are drawn in Java (see Artwork).
The game draws every card from code.

Chaplain and Envoy, and the earlier Infantry/Cavalry/Archer/Clergy/Noble tags, are retired.

## Deck — approved

Every player receives the same 45 cards: all 35 Units and one copy of each of the 10 Spells,
shuffled independently by the server (`DeckRules.deck`). Players never build or choose a deck.
The eleventh Spell slot, left by Sabotage, is open until a new Spell is designed.

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
| Spy | `SPY 3`: three random enemy hand cards are revealed to you only | none |
| Clear Disable | `CLEAR_DISABLE`: removes the disabled points and tags | one of your disabled Units |

A Spell whose target type has no legal target may still be cast, to no effect, so a hand of
Spells never leaves a player without a play.

## Formations — Balatro-style; values PROVISIONAL

Like a poker hand in Balatro, each row is checked for its best formation; only that one counts.
Every nation formation has a class twin of equal value; when both apply, the strongest wins.
Units with disabled tags do not count toward a formation. Values are in `core/card/Formation`.

| Formation | Needs | Bonus | Mult |
| --- | --- | --- | --- |
| Company | 3 Units of one class | +10 | ×1 |
| Retinue | 3 Units of one nation | +10 | ×1 |
| Two Companies | 2 Units each of two classes | +15 | ×1 |
| Two Banners | 2 Units each of two nations | +15 | ×1 |
| Three Companies | 2 Units each of three classes | +25 | ×1 |
| Three Banners | 2 Units each of three nations | +25 | ×1 |
| Full Regiment | 3 of one class + 2 of another | +20 | ×2 |
| Full Muster | 3 of one nation + 2 of another | +20 | ×2 |
| Regiment | 4 Units of one class | +25 | ×2 |
| Royal Line | one Unit of each class | +30 | ×2 |
| Banner Host | all 5 Units of one nation | +40 | ×3 |

## Score order

Row total = (printed points + pair bonuses + formation bonus) × formation multiplier, then the
all-seven-nations combo (+15, `Scoring.ALL_NATIONS_BONUS`, provisional) is added unmultiplied.
A Unit with disabled tags makes no pairs, does not count toward a formation and does not count
toward the seven nations; disabled points remove its printed points. Example: Hungary's
Pikemen, Bowmen and Riders with Poland's Pikemen and Bowmen make a Full Muster: 14 printed
points and 18 pair points (three Hungarian pairs, one Polish pair, two class pairs) score
(14 + 18 + 20) × 2 = 104.

## Artwork

Card faces are chosen in `CardFace`:

1. **Java pixel art** (`PixelCardFace`, 80×120) covers all
   **35 Units** and all ten Spells (the Spells and back from `PxlCardArt`). Every Unit uses the same class silhouette
   across seven nation palettes, matching cloth/shield colours and seven distinct stylized
   crests. Points, names and labels come from the catalog. Transylvania uses two header lines.
   Native art is created from Java drawing instructions and cached for JavaFX display;
   exported PNGs are verification artifacts, not runtime inputs.
2. Cards outside the catalog fall back to a procedural face.

No card image ships in the jar: the painted Unit and Spell runtime JPEGs were removed. All Riders
display their catalog value 4 through the pixel renderer.
The menu and match-room background artwork are retained. The Atlas section is removed.
