/* ==========================================================================
   Onitama sumi-e move card renderer
   --------------------------------------------------------------------------
   renderCard({ name, img, moves, quote, side, stamp, kanji? }) -> HTMLElement

     name   : "Tiger"
     img    : "../onitama-icons/tiger.png"
     moves  : [[dx, dy], ...]  relative to the piece; negative dy = forward (up)
     quote  : flavour text
     side   : 'red' | 'blue'   owner colour (hanko seal + inner rule + targets)
     stamp  : 'red' | 'blue'   colour of the small indicator dot
     kanji  : optional seal character (defaults from the name, e.g. 虎)

   Requires card.css. Shared SVG filters are injected once into <body>.
   ========================================================================== */
(function (global) {
  "use strict";

  const SVG_NS = "http://www.w3.org/2000/svg";

  // Seal characters for the 16 base-game animals (+ fallbacks).
  const KANJI = {
    tiger: "虎", elephant: "象", dragon: "龍", frog: "蛙", rabbit: "兎",
    crab: "蟹", goose: "鵝", rooster: "鶏", monkey: "猿", mantis: "螂",
    horse: "馬", ox: "牛", crane: "鶴", boar: "猪", eel: "鰻", cobra: "蛇",
  };

  /* Inject the shared ink filters once per document. */
  function ensureInkDefs() {
    if (document.getElementById("ink-card-defs")) return;
    const holder = document.createElement("div");
    holder.innerHTML = `
<svg id="ink-card-defs" width="0" height="0" style="position:absolute;width:0;height:0" aria-hidden="true">
  <defs>
    <!-- wobbly, fibrous brush stroke for the card border -->
    <filter id="ink-rough" x="-5%" y="-5%" width="110%" height="110%">
      <feTurbulence type="fractalNoise" baseFrequency="0.02 0.03" numOctaves="3" seed="3" result="warp"/>
      <feDisplacementMap in="SourceGraphic" in2="warp" scale="4" xChannelSelector="R" yChannelSelector="G" result="d"/>
      <feTurbulence type="fractalNoise" baseFrequency="0.09" numOctaves="3" seed="9" result="grain"/>
      <feColorMatrix in="grain" type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 -2.2 1.9" result="grainA"/>
      <feComposite in="d" in2="grainA" operator="in"/>
    </filter>
    <!-- smaller-scale roughening for grid outline and enso -->
    <filter id="ink-rough-sm" x="-10%" y="-10%" width="120%" height="120%">
      <feTurbulence type="fractalNoise" baseFrequency="0.08" numOctaves="2" seed="5" result="warp"/>
      <feDisplacementMap in="SourceGraphic" in2="warp" scale="2.4" xChannelSelector="R" yChannelSelector="G" result="d"/>
      <feTurbulence type="fractalNoise" baseFrequency="1.2" numOctaves="1" seed="2" result="grain"/>
      <feColorMatrix in="grain" type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 -1.2 1.3" result="grainA"/>
      <feComposite in="d" in2="grainA" operator="in"/>
    </filter>
    <!-- very light edge jitter for fine rules -->
    <filter id="ink-edge" x="-5%" y="-50%" width="110%" height="200%">
      <feTurbulence type="fractalNoise" baseFrequency="0.25" numOctaves="1" seed="11" result="warp"/>
      <feDisplacementMap in="SourceGraphic" in2="warp" scale="1.4" xChannelSelector="R" yChannelSelector="G"/>
    </filter>
    <!-- same, for grid lines (user-space region so zero-width lines aren't clipped) -->
    <filter id="ink-edge-u" filterUnits="userSpaceOnUse" x="-10" y="-10" width="400" height="400">
      <feTurbulence type="fractalNoise" baseFrequency="0.3" numOctaves="1" seed="12" result="warp"/>
      <feDisplacementMap in="SourceGraphic" in2="warp" scale="1.6" xChannelSelector="R" yChannelSelector="G"/>
    </filter>
    <!-- pigment-filled squares: rough edges + uneven density -->
    <filter id="ink-bleed" x="-15%" y="-15%" width="130%" height="130%">
      <feTurbulence type="fractalNoise" baseFrequency="0.18" numOctaves="2" seed="4" result="warp"/>
      <feDisplacementMap in="SourceGraphic" in2="warp" scale="3" xChannelSelector="R" yChannelSelector="G" result="d"/>
      <feTurbulence type="fractalNoise" baseFrequency="0.06" numOctaves="2" seed="8" result="mottle"/>
      <feColorMatrix in="mottle" type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 -0.6 1.25" result="mottleA"/>
      <feComposite in="d" in2="mottleA" operator="in"/>
    </filter>
    <!-- hanko stamp: speckled, uneven pressure -->
    <filter id="ink-stamp" x="-10%" y="-10%" width="120%" height="120%">
      <feTurbulence type="fractalNoise" baseFrequency="0.6" numOctaves="2" seed="21" result="grain"/>
      <feColorMatrix in="grain" type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 -1.6 1.6" result="grainA"/>
      <feComposite in="SourceGraphic" in2="grainA" operator="in" result="speck"/>
      <feTurbulence type="fractalNoise" baseFrequency="0.12" numOctaves="1" seed="6" result="warp"/>
      <feDisplacementMap in="speck" in2="warp" scale="1.6" xChannelSelector="R" yChannelSelector="G"/>
    </filter>
  </defs>
</svg>`;
    document.body.appendChild(holder.firstElementChild);
  }

  function svg(tag, attrs, parent) {
    const el = document.createElementNS(SVG_NS, tag);
    for (const k in attrs) el.setAttribute(k, attrs[k]);
    if (parent) parent.appendChild(el);
    return el;
  }

  /* Card border: heavy brush rectangle + faint echo + thin side-colour rule. */
  function buildFrame() {
    const s = svg("svg", { class: "ink-card__frame", viewBox: "0 0 400 460", preserveAspectRatio: "none", "aria-hidden": "true" });
    svg("rect", { class: "brush",  x: 5.5, y: 5.5, width: 389, height: 449, rx: 5 }, s);
    svg("rect", { class: "core",   x: 6,   y: 6,   width: 388, height: 448, rx: 5 }, s);
    svg("rect", { class: "brush2", x: 9.5, y: 10,  width: 381, height: 440, rx: 3 }, s);
    svg("rect", { class: "rule",   x: 12.5, y: 12.5, width: 375, height: 435, rx: 2 }, s);
    return s;
  }

  /* 5x5 move grid. Centre cell = the piece (enso); moves are relative. */
  function buildGrid(moves) {
    const N = 5, C = 30, P = 0;               // cell size 30 -> 150px
    const S = N * C;
    const g = svg("svg", { class: "ink-card__grid", viewBox: `-4 -4 ${S + 8} ${S + 8}`, role: "img" });

    svg("rect", { class: "wash", x: 0, y: 0, width: S, height: S }, g);

    // targets first so the lines sit on top like ink over pigment
    for (const [dx, dy] of moves) {
      const cx = 2 + dx, cy = 2 + dy;
      if (cx < 0 || cx >= N || cy < 0 || cy >= N) continue;
      svg("rect", { class: "target", x: cx * C + 3, y: cy * C + 3, width: C - 6, height: C - 6, rx: 1.5 }, g);
    }

    const lines = svg("g", { class: "lines" }, g);
    for (let i = 1; i < N; i++) {
      svg("line", { x1: i * C, y1: P, x2: i * C, y2: S }, lines);
      svg("line", { x1: P, y1: i * C, x2: S, y2: i * C }, lines);
    }
    svg("rect", { class: "outer", x: 0, y: 0, width: S, height: S }, g);

    // Enso: an open brush circle, thick at the start, tapering off.
    const cx = 2.5 * C, cy = 2.5 * C, r = 10.5;
    const arc = (a0, a1, w, op) => {
      const p0 = [cx + r * Math.cos(a0), cy + r * Math.sin(a0)];
      const p1 = [cx + r * Math.cos(a1), cy + r * Math.sin(a1)];
      const large = (a1 - a0) % (2 * Math.PI) > Math.PI ? 1 : 0;
      svg("path", { class: "enso", d: `M${p0} A${r} ${r} 0 ${large} 1 ${p1}`, "stroke-width": w, opacity: op }, g);
    };
    const start = -2.2;
    arc(start, start + 3.4, 3.6, 0.95);
    arc(start + 3.0, start + 5.2, 2.6, 0.85);
    arc(start + 5.0, start + 5.75, 1.4, 0.6);

    const title = document.createElementNS(SVG_NS, "title");
    title.textContent = "Moves: " + moves.map(([x, y]) => `(${x},${y})`).join(" ");
    g.prepend(title);
    return g;
  }

  function renderCard({ name, img, moves = [], quote = "", side = "red", stamp = "blue", kanji }) {
    ensureInkDefs();

    const card = document.createElement("article");
    card.className = `ink-card side-${side} stamp-${stamp}`;
    card.setAttribute("aria-label", `${name} card`);

    const art = document.createElement("div");
    art.className = "ink-card__art";
    const im = document.createElement("img");
    im.src = img;
    im.alt = name;
    im.decoding = "async";
    art.appendChild(im);

    const title = document.createElement("h2");
    title.className = "ink-card__title";
    title.textContent = name;

    const seal = document.createElement("div");
    seal.className = "ink-card__seal";
    seal.title = side === "red" ? "Red side" : "Blue side";
    const sk = document.createElement("span");
    sk.textContent = kanji || KANJI[String(name).toLowerCase()] || String(name)[0].toUpperCase();
    seal.appendChild(sk);

    const q = document.createElement("p");
    q.className = "ink-card__quote";
    q.textContent = quote;
    const dot = document.createElement("i");
    dot.className = "ink-card__dot";
    dot.title = `${stamp} indicator`;
    q.appendChild(dot);

    card.append(art, title, buildGrid(moves), q, seal, buildFrame());
    return card;
  }

  global.renderCard = renderCard;
  global.ensureInkDefs = ensureInkDefs;
  if (typeof module !== "undefined" && module.exports) module.exports = { renderCard, ensureInkDefs };
})(typeof window !== "undefined" ? window : globalThis);
