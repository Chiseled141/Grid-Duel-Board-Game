# Third-party sprite pack drafts — 2026-10-21

**Status: review only, not integrated.** The game still draws the hand-pixel v2 figures.

The user asked to try free pixel-art packs instead of hand-drawn figures. Two packs that allow
use and modification were downloaded (price set to 0 on itch.io) and placed in the current card
frame. **The packs themselves are not in this repository** (they were extracted to a scratch
directory); only these preview images and the composition source are here.

| Pack | Licence (from the pack / page) | What it covers |
| --- | --- | --- |
| [MtPixls — Medieval Army Pack V3](https://mtpixls.itch.io/medieval-army-pack) | Personal and commercial use, may modify; **no redistribution or resale** (modified or not); no NFT or AI-training use; credit appreciated | Spear, Archer, Sword&Shield, Halberd, Mace, Pike&Shield, Crossbow, … 64×64/80 idle frames. **No mounted soldier and no axeman.** |
| [AnGav — Medieval Pixel Characters](https://kiendko.itch.io/medieval-pixel-characters) | Free and commercial use, may adapt; no resale; credit appreciated | Archer and Spearman only, 32×32 |

Mapping used (MtPixls): Pikemen → Spear Soldier, Bowmen → Archer, Knights → Sword&Shield,
Axemen → Halberd (axe-bladed polearm, nearest available), Riders → not in the pack (hand-pixel
v2 shown). Green and deep-blue cloth is recoloured to each nation's palette; steel, wood and skin
keep the pack colours. First idle frame, trimmed, drawn at the largest whole scale (MtPixls 1×,
AnGav 2×), nearest neighbour, bottom-aligned in the figure area.

## Files

- `compare-poland.png`: current game, AnGav and MtPixls for the five Poland classes, 3×.
- `mtpixls-nations.png`: MtPixls Knights recoloured for all seven nations, 3×.
- `source/PackDraft.java`: compositor; compile with `../hand-pixel-v2/source/HandPixelRenderer.java`.

## Notes for integration (if chosen)

- Pack faces use 17–30 colours; `PixelCardFaceTest` currently caps a face at 16.
- Riders need another source (or keep the hand-pixel v2 rider); Axemen would be a halberdier.
- The runtime would load PNG frames from resources instead of drawing in code; the
  redistribution clause means the repository and game files must stay private.
