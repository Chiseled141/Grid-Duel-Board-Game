/* Onitama base-game move cards (16).
   moves: [dx, dy] relative to the piece, negative dy = forward (towards opponent).
   stamp: colour of the indicator dot = who starts if this is the 5th (side) card.
   side is NOT stored here: it depends on who holds the card at runtime.
   Usage: renderCard({ ...ONITAMA_CARDS[i], img: iconDir + ONITAMA_CARDS[i].id + ".png", side: "red" }) */
(function (global) {
  "use strict";
  const ONITAMA_CARDS = [
    { id: "tiger",    name: "Tiger",    stamp: "blue", moves: [[0,-2],[0,1]],                 quote: "Strike far, or step back and pounce." },
    { id: "dragon",   name: "Dragon",   stamp: "red",  moves: [[-2,-1],[2,-1],[-1,1],[1,1]],  quote: "Leap wide, strike from the storm clouds." },
    { id: "frog",     name: "Frog",     stamp: "red",  moves: [[-2,0],[-1,-1],[1,1]],         quote: "Leap aside, then sink into still water." },
    { id: "rabbit",   name: "Rabbit",   stamp: "blue", moves: [[1,-1],[-1,1],[2,0]],          quote: "Bound sideways; be gone before the blow." },
    { id: "crab",     name: "Crab",     stamp: "blue", moves: [[-2,0],[2,0],[0,-1]],          quote: "Scuttle wide, hold the line, press on." },
    { id: "elephant", name: "Elephant", stamp: "red",  moves: [[-1,-1],[1,-1],[-1,0],[1,0]],  quote: "Steady steps carry the greatest weight." },
    { id: "goose",    name: "Goose",    stamp: "blue", moves: [[-1,-1],[-1,0],[1,0],[1,1]],   quote: "Spread your wings to hide your intent." },
    { id: "rooster",  name: "Rooster",  stamp: "red",  moves: [[1,-1],[1,0],[-1,0],[-1,1]],   quote: "Strike sharp and never let them rest." },
    { id: "monkey",   name: "Monkey",   stamp: "blue", moves: [[-1,-1],[1,-1],[-1,1],[1,1]],  quote: "Dance on the diagonals; laugh at walls." },
    { id: "mantis",   name: "Mantis",   stamp: "red",  moves: [[-1,-1],[1,-1],[0,1]],         quote: "Poised in stillness, both blades ready." },
    { id: "horse",    name: "Horse",    stamp: "red",  moves: [[0,-1],[-1,0],[0,1]],          quote: "Tireless and sure, the charge holds firm." },
    { id: "ox",       name: "Ox",       stamp: "blue", moves: [[0,-1],[1,0],[0,1]],           quote: "Plow forward; nothing turns the Ox aside." },
    { id: "crane",    name: "Crane",    stamp: "blue", moves: [[0,-1],[-1,1],[1,1]],          quote: "Stand tall, strike once, glide away." },
    { id: "boar",     name: "Boar",     stamp: "red",  moves: [[0,-1],[-1,0],[1,0]],          quote: "Charge head-on and trample the flanks." },
    { id: "eel",      name: "Eel",      stamp: "blue", moves: [[-1,-1],[-1,1],[1,0]],         quote: "Slip through the current; never be held." },
    { id: "cobra",    name: "Cobra",    stamp: "red",  moves: [[-1,0],[1,-1],[1,1]],          quote: "Coil, sway, and let the fangs find flesh." },
  ];
  global.ONITAMA_CARDS = ONITAMA_CARDS;
  if (typeof module !== "undefined" && module.exports) module.exports = ONITAMA_CARDS;
})(typeof window !== "undefined" ? window : globalThis);
