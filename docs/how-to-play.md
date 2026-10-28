# How to Play

The main menu's **How to Play** entry opens the English guide, including in offline practice.
Choose a lesson, read the rule and step through its recorded example with **Next**.
**Details** shows the recorded action and a compact score explanation for both sides.
Back (or Escape from the replay) returns to the menu. Leaving or changing lessons pauses playback.

The example has only Next and Details controls: no timeline, round jumps, Previous, automatic
playback or speed control. Right arrow advances one action; the full replay navigation shortcuts
are disabled here. Choose the lesson again to restart it. Personal replays retain their complete
navigation and searchable journal; see [replay controls](replays.md).

The 17 lessons cover starting matches, Units and pairs, formations/scoring, seven-space rows
and replacement, manual draws/skips, Kings/Secret Edict, rounds/final results and each of the ten
neutral Spells. Rules and observation prompts sit beside playback; private confirmation and
inspected-card steps have specific prompts. Formation values, the seven-nation bonus, King
descriptions and Spell text come from current structured definitions. Provisional balance is labelled.

## Recordings and boundaries

`src/main/resources/tutorial/*.duel` contains curated training recordings, not personal history.
They hold a public training seed and accepted moves, with a marker selecting a contiguous excerpt.
The generator uses the ordinary 45-card deck and `MatchState.apply`; it does not fabricate rows,
scores, hands or frames. The learner's camera is always the first seat's recipient-visible view.
The full-match example completes three rounds without forfeiting. Other clips show the relevant
before/after state, including every Spell with a meaningful legal target or nonempty deck/hand.

`client/guide/TutorialRecording` replays and validates each recorded move against the bundled
catalog on a worker thread, then returns immutable `PlayerView` frames. `TutorialLibrary` pairs
these with English lessons. `HowToPlayPanel` owns chapter selection and observation text;
`ReplayPlayback` owns playback position/speed; `ReplayPanel` renders it independently of the live
match and account replay. No tutorial action can send a network move, create a match, write a
result/replay or change settings. Loading failures have an explicit Retry action. Late worker
completion does not start playback after leaving the guide.

The personal replay path remains History → Replay. Compact replay layouts keep final scores
above controls in the guide's smaller viewport. King art decodes in the background and triggers
repaint on completion. The headless server excludes tutorial/client/JavaFX/artwork resources.

## Reproducing the training assets on Windows

From the repository root, using the current compiled core:

```powershell
mvn -DskipTests compile
javac -cp target/classes -d target/tutorial-tools tools/RecordTutorials.java
java -cp "target/classes;target/tutorial-tools" RecordTutorials
mvn test
```

The tool searches deterministic training deals, records only accepted moves and writes the 17
intentional teaching assets. It never reads the live database or connects to a server. Tests check
that all recordings remain valid and actually demonstrate their named effect, rather than merely
loading successfully. Regenerate and review them when rules or catalog behavior changes.

Separately, Unit/Spell hover popups are disabled during the entire live King-selection phase,
including when the eye button hides the offer. The hand remains visible and King inspection is
preserved; ordinary in-progress hover behavior is unchanged.

See [verification notes](testing/README.md) for reproducible checks of the guide and its recordings.
