# How to Play

The main menu's **How to Play** entry opens the **Quick guide**: seven steps down the left
(play cards, combos, formations, Spells, swapping, Kings, winning), each one line of text over a
short GIF clip. Previous/Next or the arrow keys move between steps; reduced motion shows each
clip's first frame. `GuideSteps` lists the steps and is shared with the match's pause menu
(Menu → How to play), so both teach the same thing.

The clips in `src/main/resources/assets/guide/` were recorded on 2026-10-29 from a real practice
match (seed 7) on the real `CardTableView`: the engine (`MatchState`) applied every move, the
greedy bot played the opponent, and scripted pointer input pressed, dragged and released on the
table as a player would. Frames were snapshot at 10 per second and encoded with ffmpeg
(palette + Bayer dither). The drawn arrow pointer is added to the frames; no
screenshot of the desktop was taken.

Back (or Escape anywhere on the page) returns to the menu. The page reads only bundled files and
sends nothing to the server. The Recorded lessons view (17 `.duel` training recordings shown in a
read-only replay) was removed at the user's request on 2026-10-29; personal replays stay under
History → Replay ([replay controls](replays.md)).

## Reproducing the quick-guide clips (macOS/Linux, graphical session)

```sh
mvn -q -DskipTests compile
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
javac -cp "target/classes:$(cat target/cp.txt)" -d target/guide-tools tools/RecordGuideClips.java
java -cp "target/guide-tools:target/classes:$(cat target/cp.txt)" deckduel.client.ui.RecordGuideClips target/guide-frames 7
java -cp "target/guide-tools:target/classes:$(cat target/cp.txt)" deckduel.client.ui.RecordGuideClips target/guide-win 7 win
```

The first run writes `kings`, `play`, `combos`, `formation`, `cast`, `swap` and `win` frame folders;
the second re-records only the ending (the bot passes in round 3 so the clip ends in a victory) and
was used for `win.gif`. Encode each folder with
`ffmpeg -framerate 10 -i <dir>/f%04d.png -vf "scale=1920:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=160:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle" -loop 0 <name>.gif`
(`play.gif` keeps its first 95 frames). Frames are rendered at 2x (2560×1440) and the GIFs are
1920×1080, about 36 MB in total; the How to Play screen shrinks a clip to fit but never draws it
larger than one clip pixel per screen pixel, so it stays sharp on HiDPI displays.

Separately, Unit/Spell hover popups are disabled during the entire live King-selection phase,
including when the eye button hides the offer. The hand remains visible and King inspection is
preserved; ordinary in-progress hover behavior is unchanged.

See [verification notes](testing/README.md) for reproducible checks of the guide and its recordings.
