# Card catalog — `cards-neutral-5`

The game reads one file, `src/main/resources/cards/catalog.txt`; no code names a card. Its
version, `cards-neutral-5`, is recorded with every match result. `CatalogLoader` validates it
at startup: every nation has exactly one Unit of each class, a class prints the same points in
every nation, and Spells are neutral. Landmarks and the Sabotage Spell were removed on
2026-10-21; matches recorded under `cards-neutral-2` and earlier had them.

## Units — from the painted card art

Seven nations (Ashkar, Asterion, Dravorn, Nerath, Solmara, Sylvaren, Vaelora) × five classes =
35 Units. Each Unit has exactly one nation, one class and no ability. Its id is
`<nation>-<class>` in lower case (for example `nerath-knight`), which also names its art file.

| Class | Printed points |
| --- | --- |
| Assassin | 2 |
| Bowman | 3 |
| Guardman | 4 |
| Knight | 5 |
| Wizard | 6 |

The nations, classes and points are those printed on the painted card art, adopted at the
user's request on 2026-10-22 (`cards-neutral-5`). They replaced Hungary, Poland, Bohemia,
Austria, Styria, Croatia and Transylvania with Pikemen 2, Bowmen 3, Riders 4, Knights 5 and
Axemen 4 (`cards-neutral-4`). Earlier versions: `cards-neutral-1` used Footmen, `-2`/`-3`
Battering Ram. Pairs and formations compare class tags, never points.

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
toward the seven nations; disabled points remove its printed points. Example: Ashkar's
Assassin, Bowman and Guardman with Nerath's Assassin and Bowman make a Full Muster: 14 printed
points and 18 pair points (three Ashkar pairs, one Nerath pair, two class pairs) score
(14 + 18 + 20) × 2 = 104.

## Artwork

Card faces are chosen in `CardFace`:

1. **Painted Units** (`PaintedCardArt`): `src/main/resources/assets/cards/<id>.png`, 744×1039,
   one per Unit, plus `card-back.png` for the shared back. Each nation has its own cloth colour,
   ribbon and crest; each class has its own figure. The painting prints the nation, class and
   points, so a Unit uses it only while its id, class and points match what is printed;
   otherwise it falls back to the procedural face. Paintings are centre-cropped to the table's
   2:3 card shape (a few pixels of ribbon at each side are trimmed), given rounded corners and
   scaled down in halving steps. The client decodes them on a background thread at start-up.
2. **Pixel Spells** (`PixelSpellArt`, 80×120): the ten neutral Spells from the drawing data in
   `PxlCardArt`, with catalog names and effect captions, scaled without smoothing.
3. Any other card falls back to a procedural face (`CardArt`).

The paintings add about 28 MB to the jar.
The menu and match-room background artwork are retained. The Atlas section is removed.
