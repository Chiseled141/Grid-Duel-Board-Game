// Deck Duel: neutral Spell illustrations and the shared card back.
// 80x120 native pixels, hard edges, light from the upper left. The name and the two caption
// lines are composed from CardDef in Java (PixelSpellArt): title at y 8, captions at y 96/103.
// Shared language: a padlock means "disabled", an open padlock means "cleared".
pal({ k: '#232b3c', n: '#292d45', d: '#343b56', v: '#67567f', p: '#b6a1c7',
  t: '#527c82', T: '#78c9b0', g: '#d7a95b', G: '#9a6a3c', w: '#f4e9ce', h: '#fff6df',
  r: '#c54846', R: '#793949', s: '#8dabae', S: '#506777' });

// ---------- shared parts ----------

function star(x, y, colour = 'g', r = 3) {
  return [line(x-r,y,x+r,y,colour), line(x,y-r,x,y+r,colour), px(x,y,'h')];
}

// Small face-down card (14x19 with outline).
sprite('_minicard', { w: 12, h: 17, outline: 'k' },
  rect(0,0,12,17,'g'), rect(1,1,10,15,'t'), rect(2,2,8,13,'n'),
  poly([6,4,9,8,6,13,3,8],'t'), poly([6,6,8,8,6,11,4,8],'T'), px(1,1,'h'));
sprite('_minicard_v', { base: '_minicard', swap: { t: 'v', T: 'p' } });
sprite('_minicard_r', { base: '_minicard', swap: { t: 'R', T: 'r' } });

// Small face-up card: a revealed card (14x19 with outline).
sprite('_faceup', { w: 12, h: 17, outline: 'k' },
  rect(0,0,12,17,'g'), rect(1,1,10,15,'w'), rect(10,1,1,15,'p'), rect(1,15,10,1,'p'),
  px(1,1,'h'), poly([6,4,9,8,6,12,3,8],'v'), poly([6,6,7,8,6,10,5,8],'p'));

// Large face-down card (22x30 with outline).
sprite('_bigcard', { w: 20, h: 28, outline: 'k' },
  rect(0,0,20,28,'g'), rect(19,0,1,28,'G'), rect(0,27,20,1,'G'), rect(1,1,17,1,'h'),
  rect(2,2,16,24,'t'), rect(3,3,14,22,'n'),
  poly([10,6,16,14,10,22,4,14],'t'), poly([10,9,13,14,10,19,7,14],'T'), px(9,12,'h'));
sprite('_bigcard_v', { base: '_bigcard', swap: { t: 'v', T: 'p' } });

// Chain link (6x6 with outline), shaded from the upper left.
sprite('_link', { outline: 'k' }, `
  .hs.
  s..S
  s..S
  .SS.`);
function chain(x0, y0, x1, y1) {
  const steps = Math.round(Math.hypot(x1-x0, y1-y0) / 4);
  const out = [];
  for (let i = 0; i <= steps; i++)
    out.push(stamp('_link', Math.round(x0 + (x1-x0)*i/steps) - 3, Math.round(y0 + (y1-y0)*i/steps) - 3));
  return out;
}

// Closed padlock (15x17 with outline): the "disabled" seal.
sprite('_lock', { w: 13, h: 15, outline: 'k' },
  clip([0,0,13,7], ring(6,6,5,'s'), ring(6,6,4,'S')),
  rect(0,6,13,9,'g'), rect(11,6,2,9,'G'), rect(0,13,13,2,'G'), rect(1,7,4,1,'h'),
  rect(5,9,3,2,'k'), rect(6,11,1,2,'k'));

// Open shackle for Clear Disable, lifted out of the body on the right (with outline).
sprite('_shackle_open', { w: 21, h: 22, outline: 'k' },
  clip([0,0,21,11], ring(10,10,10,'s'), ring(10,10,9,'s'), ring(10,10,8,'S')),
  rect(0,10,3,12,'s'), rect(2,10,1,12,'S'), px(1,2,'h'), px(0,6,'h'),
  rect(18,10,3,3,'s'), rect(20,10,1,3,'S'));

// Great helm for Destroy Unit (30x34 with outline).
sprite('_helm', { w: 28, h: 32, outline: 'k' },
  poly([6,0,22,0,28,6,28,32,0,32,0,6],'s'),
  poly([19,0,22,0,28,6,28,32,19,32],'S'),
  line(2,7,2,29,'h'), line(3,4,6,1,'h'),
  rect(0,9,28,2,'g'), rect(19,9,9,2,'G'),
  rect(3,14,22,2,'k'), rect(13,16,2,16,'g'), rect(14,16,1,16,'G'),
  px(7,22,'k'), px(7,25,'k'), px(20,22,'k'), px(20,25,'k'));

// Card frame. Spells get an art window with a soft spotlight; the back fills its panel.
function frame(accent, window = true) {
  const items = [rect(3,0,74,120,'k'), rect(0,3,80,114,'k'),
    rect(3,1,74,118,accent), rect(1,3,78,114,accent),
    rect(5,4,70,112,'n'), rect(4,5,72,110,'n'),
    ...[6,71].flatMap(x => [rect(x,5,3,1,'g'), rect(x,5,1,3,'g'),
      rect(x,114,3,1,'g'), rect(x+2,112,1,3,'g')])];
  if (window) items.push(rect(6,17,68,75,accent), rect(7,18,66,73,'n'),
    layer('spot', { density: 0.5 }, circ(40,54,31,'d')), circ(40,54,25,'d'),
    ...star(40,17,'g',2), ...star(40,91,'g',2));
  return items;
}
function spell(id, accent, ...art) {
  sprite(id, { w: 80, h: 120 }, ...frame(accent), ...art);
}

// ---------- the ten Spells ----------

// Disable Tags: a nation/class banner chained shut.
spell('disable-tags', 'v',
  rect(16,24,48,5,'k'), rect(17,25,46,2,'G'), rect(17,25,46,1,'g'),
  circ(16,26,3,'k'), circ(16,26,2,'g'), circ(64,26,3,'k'), circ(64,26,2,'g'),
  poly([21,28,59,28,59,78,40,68,21,78],'k'),
  poly([23,28,57,28,57,75,40,65,23,75],'v'),
  poly([23,28,27,28,27,71,23,75],'p'), poly([52,28,57,28,57,75,52,72],'d'),
  ...star(40,40,'g',5), px(40,40,'w'),
  ...chain(18,38,62,74), ...chain(62,38,18,74),
  stamp('_lock', 33, 49));

// Disable Points: a points coin chained shut.
spell('disable-points', 'v',
  circ(40,43,17,'k'), circ(40,43,16,'G'), circ(39,42,15,'g'), ring(40,43,11,'G'),
  line(28,34,32,30,'h'), px(27,37,'h'),
  text(41,36,'5','G',{scale:2,align:'c'}), text(40,35,'5','w',{scale:2,align:'c'}),
  ...chain(14,42,66,76), ...chain(66,42,14,76),
  stamp('_lock', 33, 52),
  ...star(64,26,'p',2), ...star(16,24,'g',2));

// Destroy Unit: a great helm split along a red fracture.
spell('destroy-unit', 'R',
  clip([0,0,37,0,37,30,34,40,39,52,33,64,37,72,37,120,0,120], stamp('_helm', 22, 38)),
  clip([43,0,80,0,80,120,43,120,43,72,39,64,45,52,40,40,43,30], stamp('_helm', 28, 34)),
  line(40,22,40,30,'r',2), line(40,30,37,40,'r',2), line(37,40,42,52,'r',2),
  line(42,52,36,64,'r',2), line(36,64,40,72,'r',2), line(40,72,40,86,'r',2),
  line(40,22,40,30,'h'), line(40,30,37,40,'h'), line(37,40,42,52,'h'),
  px(36,30,'s'), px(51,26,'s'), px(34,72,'S'), px(54,70,'S'), px(50,80,'s'),
  ...star(20,28,'g',2), ...star(62,84,'r',2), ...star(60,24,'r',3));

// Random Discard: an enemy card burning from the top.
spell('random-discard', 'R',
  rect(24,40,32,46,'k'), rect(25,41,30,44,'g'), rect(54,41,1,44,'G'), rect(25,84,30,1,'G'),
  rect(27,43,26,40,'R'), rect(29,45,22,36,'n'),
  poly([40,52,50,66,40,80,30,66],'R'), poly([40,57,46,66,40,75,34,66],'r'),
  poly([24,40,56,40,56,49,51,53,46,49,41,55,36,50,31,54,24,49],'k'),
  poly([24,49,31,54,36,50,41,55,46,49,51,53,56,49,56,51,51,55,46,51,41,57,36,52,31,56,24,51],'G'),
  poly([22,52,23,38,27,44,29,28,34,38,38,20,42,34,47,24,49,38,54,30,58,46,58,52,51,52,46,48,41,53,36,49,31,53],'r'),
  poly([26,50,28,42,31,46,35,32,39,42,43,30,46,42,51,36,54,50,46,46,41,51,36,47],'g'),
  poly([31,48,34,42,37,45,40,37,43,44,47,41,49,48,41,48],'h'),
  px(19,30,'g'), px(61,24,'g'), px(63,38,'r'), px(17,42,'r'), px(58,18+2,'h'));

// Random Steal: a hook pulls an enemy card back to you.
sprite('_hook', { outline: 'k' }, `
  .sss.......
  s...s......
  .sss.......
  ..s........
  ..s........
  ..s........
  ..s........
  ..s.....s..
  ..s....ss..
  ..s.....s..
  ...s....s..
  ....ssss...`);
spell('random-steal', 't',
  line(64,26,69,21,'T'), line(65,35,70,30,'T'), line(65,44,70,39,'T'),
  stamp('_bigcard_v', 40, 22),
  wire(39,41,12,86,10,'G'), wire(38,42,11,87,10,'g'),
  stamp('_hook', 37, 38),
  line(30,72,20,82,'T',2), poly([15,80,22,87,13,89],'T'),
  ...star(62,76,'g',3), ...star(22,30,'p',2));

// Draw Two: two cards rise from your deck.
spell('draw-two', 'v',
  rect(20,72,40,14,'k'), rect(21,73,38,2,'g'), rect(21,73,38,1,'h'),
  rect(21,75,38,2,'t'), rect(21,77,38,1,'n'), rect(21,78,38,2,'t'), rect(21,80,38,1,'n'),
  rect(21,81,38,2,'t'), rect(21,83,38,2,'n'),
  stamp('_bigcard', 16, 34), stamp('_bigcard_v', 41, 28),
  line(27,66,27,69,'T'), line(33,66,33,70,'T'), line(48,60,48,69,'T'), line(54,60,54,67,'T'),
  ...star(64,26,'g',4), ...star(14,28,'p',2));

// Exchange: one card out, two cards in, around a turning cycle.
spell('exchange', 'v',
  clip([0,0,80,50], ring(40,54,24,'T'), ring(40,54,23,'T'), ring(40,54,22,'t')),
  poly([60,48,69,48,64,55],'T'),
  clip([0,58,80,40], ring(40,54,24,'g'), ring(40,54,23,'g'), ring(40,54,22,'G')),
  poly([11,60,20,60,16,53],'g'),
  stamp('_minicard_r', 20, 44), stamp('_minicard', 41, 39), stamp('_minicard_v', 47, 49),
  ...star(36,46,'p',2));

// Reconnaissance: look at the top three cards; a lens reveals the one you keep.
spell('reconnaissance', 't',
  stamp('_minicard_v', 13, 30), stamp('_minicard', 33, 24), stamp('_minicard_v', 53, 30),
  line(20,52,20,76,'T'), line(60,52,60,76,'T'),
  poly([16,74,25,74,20,79],'T'), poly([56,74,65,74,60,79],'T'),
  rect(14,80,52,6,'k'), rect(15,81,50,2,'t'), rect(15,83,50,2,'n'),
  line(51,58,63,72,'k',7), line(51,58,63,72,'G',3), line(51,58,54,61,'s',3),
  circ(40,48,16,'k'), circ(40,48,15,'g'), ring(40,48,15,'G'), circ(40,48,12,'T'),
  clip([40,48,11], rect(33,38,15,21,'G'), rect(34,39,13,19,'w'),
    poly([40,42,45,48,40,54,35,48],'v'), poly([40,45,42,48,40,51,38,48],'p')),
  line(30,44,33,40,'h'), px(32,47,'h'),
  ...star(64,22,'g',2));

// Spy: a hooded watcher with three revealed cards from the enemy hand.
spell('spy', 't',
  poly([40,21,55,29,62,46,64,66,16,66,18,46,25,29],'k'),
  poly([40,23,53,31,60,47,62,64,18,64,20,47,27,31],'t'),
  poly([40,23,27,31,20,47,18,64,24,64,25,48,31,34],'T'),
  poly([53,31,60,47,62,64,56,64,55,48,49,35],'S'),
  ellipse(40,47,12,10,'k'), ellipse(40,48,10,8,'n'),
  rect(32,46,5,2,'T'), rect(43,46,5,2,'T'), px(34,46,'h'), px(45,46,'h'),
  stamp('_faceup', 15, 64), stamp('_faceup', 33, 61), stamp('_faceup', 51, 64),
  ...star(64,24,'T',2));

// Clear Disable: an unlocked padlock, light breaking out, broken chain falling away.
spell('clear-disable', 't',
  line(40,60,16,30,'T'), line(40,60,64,30,'T'), line(40,60,12,56,'T'), line(40,60,68,56,'T'),
  line(40,60,40,22,'p'), line(40,60,22,84,'p'), line(40,60,58,84,'p'),
  stamp('_shackle_open', 29, 28),
  rect(27,49,26,22,'k'), rect(28,50,24,20,'g'), rect(48,50,4,20,'G'), rect(28,66,24,4,'G'),
  rect(29,51,8,1,'h'), rect(29,52,1,5,'h'),
  rect(38,55,5,4,'k'), rect(39,59,3,5,'k'), px(40,57,'h'),
  stamp('_link', 13, 70), stamp('_link', 18, 76), stamp('_link', 60, 66), stamp('_link', 63, 73),
  ...star(18,24,'g',3), ...star(63,22,'h',2), ...star(40,85,'T',2));

// ---------- shared back ----------

// Identical for every hidden card, with no gameplay metadata.
sprite('card-back', { w: 80, h: 120 }, ...frame('t', false),
  rect(8,9,64,102,'d'), rect(9,10,62,100,'n'),
  (c) => { for (let y = 10; y < 110; y++) for (let x = 9; x < 71; x++)
    if ((x + y) % 8 === 0 || (x - y + 120) % 8 === 0) c.px(x, y, 'd'); },
  box(8,9,64,102,'g'), box(11,12,58,96,'t'),
  circ(40,60,24,'k'), circ(40,60,23,'g'), ring(40,60,23,'G'), circ(40,60,20,'n'), ring(40,60,18,'t'),
  line(27,47,53,73,'k',4), line(53,47,27,73,'k',4),
  line(28,48,52,72,'s',2), line(52,48,28,72,'s',2), line(28,48,37,57,'h'),
  line(29,67,33,71,'g',2), line(51,67,47,71,'g',2),
  poly([34,52,46,52,46,62,43,67,40,69,37,67,34,62],'k'),
  poly([35,53,45,53,45,61,42,66,40,67,38,66,35,61],'t'),
  poly([35,53,40,53,40,67,38,66,35,61],'T'),
  ...star(40,58,'w',2),
  ...star(40,26,'g',3), ...star(40,94,'g',3));

sheet('spell-art', ['disable-tags','disable-points','destroy-unit','random-discard',
  'random-steal','draw-two','exchange','reconnaissance','spy','clear-disable','card-back'], {cols:6,pad:4});
