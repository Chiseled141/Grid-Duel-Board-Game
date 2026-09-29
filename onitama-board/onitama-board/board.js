/* ==========================================================================
   Onitama board renderer
   --------------------------------------------------------------------------
   renderBoard(state) -> HTMLElement (.ink-board)

   state = {
     pieces:         [{ x, y, type: 'master'|'student', side: 'red'|'blue' }],
     selected:       [x, y] | null,
     moveTargets:    [[x, y], ...],
     captureTargets: [[x, y], ...],
     lastMove:       [[x, y], [x, y]] | null,   // from, to
     cell:           82                        // px per cell (optional)
   }
   Coordinates: x = column 0..4 (left->right), y = row 0..4 (top->bottom).
   Blue starts on row 0 (top), red on row 4 (bottom). Temple arches are
   (2,0) blue and (2,4) red. Every cell carries data-x / data-y so you can
   attach click handlers: board.addEventListener('click', e => e.target.closest('.cell')).
   Requires board.css and pieces.js.
   ========================================================================== */
(function (global) {
  "use strict";
  const NS = "http://www.w3.org/2000/svg";

  function ensureBoardDefs() {
    if (document.getElementById("ink-board-defs")) return;
    const h = document.createElement("div");
    h.innerHTML = `
<svg id="ink-board-defs" width="0" height="0" style="position:absolute;width:0;height:0" aria-hidden="true">
  <defs>
    <filter id="bd-line" filterUnits="userSpaceOnUse" x="-20" y="-20" width="2000" height="2000">
      <feTurbulence type="fractalNoise" baseFrequency="0.25" numOctaves="1" seed="12" result="w"/>
      <feDisplacementMap in="SourceGraphic" in2="w" scale="1.8" xChannelSelector="R" yChannelSelector="G"/>
    </filter>
    <filter id="bd-rough" x="-5%" y="-5%" width="110%" height="110%">
      <feTurbulence type="fractalNoise" baseFrequency="0.05" numOctaves="2" seed="5" result="w"/>
      <feDisplacementMap in="SourceGraphic" in2="w" scale="3" xChannelSelector="R" yChannelSelector="G" result="d"/>
      <feTurbulence type="fractalNoise" baseFrequency="1.1" numOctaves="1" seed="2" result="g"/>
      <feColorMatrix in="g" type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 -1.2 1.3" result="ga"/>
      <feComposite in="d" in2="ga" operator="in"/>
    </filter>
    <filter id="bd-brush" x="-15%" y="-15%" width="130%" height="130%">
      <feTurbulence type="fractalNoise" baseFrequency="0.07" numOctaves="2" seed="9" result="w"/>
      <feDisplacementMap in="SourceGraphic" in2="w" scale="3.2" xChannelSelector="R" yChannelSelector="G" result="d"/>
      <feTurbulence type="fractalNoise" baseFrequency="0.5" numOctaves="2" seed="3" result="g"/>
      <feColorMatrix in="g" type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 -1.5 1.55" result="ga"/>
      <feComposite in="d" in2="ga" operator="in"/>
    </filter>
    <filter id="bd-blot" x="-30%" y="-30%" width="160%" height="160%">
      <feTurbulence type="fractalNoise" baseFrequency="0.09" numOctaves="2" seed="6" result="w"/>
      <feDisplacementMap in="SourceGraphic" in2="w" scale="6" xChannelSelector="R" yChannelSelector="G"/>
    </filter>
    <filter id="bd-ring" x="-10%" y="-10%" width="120%" height="120%">
      <feTurbulence type="fractalNoise" baseFrequency="0.05" numOctaves="2" seed="8" result="w"/>
      <feDisplacementMap in="SourceGraphic" in2="w" scale="3" xChannelSelector="R" yChannelSelector="G"/>
    </filter>
    <!-- ink-drawn torii gate, 100x100 box -->
    <symbol id="bd-torii" viewBox="0 0 100 100">
      <path d="M8 24 Q50 14 92 24 L90 31 Q50 23 10 31 Z" fill="currentColor"/>
      <path d="M17 40 H83" stroke="currentColor" stroke-width="6" stroke-linecap="round"/>
      <path d="M30 29 L27 90 M70 29 L73 90" stroke="currentColor" stroke-width="7.5" stroke-linecap="round"/>
      <path d="M50 27 V40" stroke="currentColor" stroke-width="5"/>
      <path d="M20 91 H34 M66 91 H80" stroke="currentColor" stroke-width="3.5" stroke-linecap="round" opacity=".7"/>
    </symbol>
  </defs>
</svg>`;
    document.body.appendChild(h.firstElementChild);
  }

  const key = (x, y) => x + "," + y;

  function renderBoard(state = {}) {
    ensureBoardDefs();
    const cell = state.cell || 82;
    const board = document.createElement("div");
    board.className = "ink-board";
    board.style.setProperty("--cell", cell + "px");

    const field = document.createElement("div");
    field.className = "ink-board__field";
    board.appendChild(field);

    // hand-drawn grid overlay
    const S = cell * 5;
    const g = document.createElementNS(NS, "svg");
    g.setAttribute("class", "ink-board__grid");
    g.setAttribute("viewBox", `-6 -6 ${S + 12} ${S + 12}`);
    let lines = "";
    for (let i = 1; i < 5; i++) {
      lines += `<line x1="${i * cell}" y1="0" x2="${i * cell}" y2="${S}"/><line x1="0" y1="${i * cell}" x2="${S}" y2="${i * cell}"/>`;
    }
    g.innerHTML = `<g class="lines" filter="url(#bd-line)">${lines}</g>
      <rect class="outer" x="0" y="0" width="${S}" height="${S}" filter="url(#bd-rough)"/>`;

    const sets = {
      move: new Set((state.moveTargets || []).map(([x, y]) => key(x, y))),
      cap: new Set((state.captureTargets || []).map(([x, y]) => key(x, y))),
      last: new Set((state.lastMove || []).map(([x, y]) => key(x, y))),
    };
    const pieceAt = {};
    for (const p of state.pieces || []) pieceAt[key(p.x, p.y)] = p;

    for (let y = 0; y < 5; y++) {
      for (let x = 0; x < 5; x++) {
        const c = document.createElement("div");
        c.className = "cell";
        c.dataset.x = x; c.dataset.y = y;
        const k = key(x, y);
        if (x === 2 && (y === 0 || y === 4)) {
          const side = y === 0 ? "blue" : "red";
          c.classList.add("temple", "temple-" + side);
          c.insertAdjacentHTML("beforeend",
            `<svg class="temple-mark" viewBox="0 0 100 100" aria-hidden="true"><use href="#bd-torii" filter="url(#bd-brush)"/></svg>`);
        }
        if (sets.last.has(k)) c.classList.add("last-move");
        if (sets.move.has(k)) c.classList.add("move-target");
        if (sets.cap.has(k)) c.classList.add("capture-target");
        if (state.selected && key(...state.selected) === k) c.classList.add("selected");
        const p = pieceAt[k];
        if (p) { c.classList.add("occupied"); c.appendChild(global.renderPiece({ type: p.type, side: p.side })); }
        field.appendChild(c);
      }
    }
    field.appendChild(g);
    return board;
  }

  /* Standard starting position. */
  function startingPieces() {
    const ps = [];
    for (let x = 0; x < 5; x++) {
      const type = x === 2 ? "master" : "student";
      ps.push({ x, y: 0, type, side: "blue" }, { x, y: 4, type, side: "red" });
    }
    return ps;
  }

  global.renderBoard = renderBoard;
  global.startingPieces = startingPieces;
  if (typeof module !== "undefined" && module.exports) module.exports = { renderBoard, startingPieces };
})(typeof window !== "undefined" ? window : globalThis);
