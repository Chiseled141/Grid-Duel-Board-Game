# Physical card interaction and Golden fields music

Restore previous UiKit action pills; enlarge public opponent cards; require an
upward drag to draw own cards (now also optional click selection); animate accepted cards beside the board; add the
provided Golden fields music and run the rebuilt game. No gameplay/protocol edits.

GamePanel binds CardPlayOverlay instead of legacy click callbacks, preserves
selected cards through targeting/pending requests, and queues accepted public
plays to PlayedCardArea (own left/opponent right). Hand reserves raised-card space
and retains reflow. Conflicting controls/input are blocked during540ms flights;
short drags/cancellation return over200ms. Esc returns selection before another
card is drawn. Duplicate/stale filtering remains in ClientModel. Lifecycle and
reduced-motion clear masks/timers. Public skill events alone create enemy faces.
Restored UiKit pills supply original hover/press/disabled variants. Public enemy
movement scale grows0.27→0.48; backs retain privacy. Board uses more of its canvas.

MenuTheme maps tracks, LobbyPanel notifies ClientFrame on theme changes, MenuMusic
switches clips on its existing worker. The supplied MP3 is bundled as32kHz stereo
16-bit PCM golden-fields.wav for Java Sound; no new runtime dependency. Existing
sound preference is respected; gameplay pauses menu music.

Validation: full `mvn test`322 tests, zero failures/errors/skips. New controller
checks cover clicks/threshold, cancellation, invalid phase/button, raised selection,
conflicting input, accepted flight completion and reduced motion. A real GamePanel
integration verifies drag→piece→destination submits one request without consuming
before acceptance. Layout checks cover1280x660 through1960x1180, both viewers and
3/6 skills, side areas outside board squares and enlarged public enemy cards.
Both music assets decode via Java Sound without opening a device. Headless rendered
large, medium, minimum and valley scenes; inspected board/hand/button arrangement.
Native desktop interaction and audible playback remain for user inspection.
Independent scoped review found and fixed reduced-motion/final-outcome plays not
updating the persistent side areas. Public card references now update even when
transient effects are skipped; regression verifies reduced-motion acceptance.
Targeted review PASS, no remaining actionable findings. Final independent `mvn clean verify`:322 tests,0failures/errors/skips; shaded
JAR built. Started rebuilt client and localhost server for user inspection.
