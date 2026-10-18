# Free movement and hand-control migration

User-authorized rules revision: replace movement cards with one optional orthogonal
one-square move per turn, retain movement skills, add theft, private scouting,
extra draws and discard/redraw, plus visibly torn overflow cards. Supersedes old movement-card rules in the harness/spec.

Acceptance: direct piece selection; no live movement hands/deck or movement draws;
friendly landing rejected, enemy landing captures; skills and opening still work;
private intel survives reconnect only for its caster and expires at End Turn;
rejected/duplicate actions never repeat random theft or draws; bots use permitted views.
Hand cap 6. Steal (4 mana) transfers a random opposing skill in place of the usual
replacement; rejects an empty opposing hand. Spy (2) privately snapshots the enemy
hand until End Turn. Draw Two (2) draws up to two extra skills, then the
normal replacement if available. At a full six-card hand, draws are permanently
destroyed in a private host zone; only the drawer sees the torn face. The opponent
receives an identity-free burn event and animates the shared back. Played skill
leaves the hand before effects resolve, freeing its slot before any draws. Refocus (1) permanently discards all other held
skills and draws that many, then consumes itself and draws the usual replacement.

Implementation: engine/config + versioned views/protocol, client targeting/help and
private-intel panel, bot policy, migrated rule tests and new privacy/conservation tests.
Keep reserved movement snapshot fields empty for source compatibility; no live route
uses the retired deck. MovePiece becomes (pieceId, to). Protocol 6 / rules grid-duel-v4.

Additional user request: more control skills. Added Sabotage (3 mana, random enemy
skill discarded permanently), Barter (2, exchange random other own/enemy skills),
Siphon (2, transfer up to two enemy mana after cost within own cap). Thirty skills
total, three initial per side and twenty-four left in deck by default.
