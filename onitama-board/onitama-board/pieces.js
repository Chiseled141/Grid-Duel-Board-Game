/* ==========================================================================
   Onitama pieces: painted-resin figurines (SVG)
   --------------------------------------------------------------------------
   renderPiece({ type: 'master'|'student', side: 'red'|'blue', size? }) -> SVGElement

   Inspired by the physical Onitama pawns, drawn as sturdy martial artists:
   broad square shoulders, thick obi, split hakama legs in a wide stance.
   - Master : taller; heavy haori over hakama, topknot with gold pin, long
              white beard, forearm folded across the chest, walking staff.
   - Student: shaved head, crossed gi collar, one fist raised in salute by the
              head, the other fist planted on the hip.
   Both stand front-facing on a round base disc, so blue and red read the
   same. Drawn in a 100x100 box (fits one cell; the Master's topknot reaches
   near the top edge). Size with CSS width/height, or pass `size`.
   Shared gradients/filters are injected once (ensurePieceDefs()).
   ========================================================================== */
(function (global) {
  "use strict";
  const NS = "http://www.w3.org/2000/svg";
  const SIDES = {
    red:  { hi: "#e0574a", base: "#b3261e", dark: "#6a130e", deep: "#4a0d09" },
    blue: { hi: "#5f84a8", base: "#2f4f6f", dark: "#1a2d42", deep: "#111f2e" },
  };
  const INK = "#1c1a18";
  const CREAM = "#f4ead2";
  const GOLD = "#e0a93a";

  function ensurePieceDefs() {
    if (document.getElementById("ink-piece-defs-v2")) return;
    const grads = Object.entries(SIDES).map(([k, c]) => `
      <!-- cylindrical resin shading: key light from upper-left -->
      <linearGradient id="pf-body-${k}" x1="0" y1="0" x2="1" y2="0">
        <stop offset="0"   stop-color="${c.base}"/>
        <stop offset=".22" stop-color="${c.hi}"/>
        <stop offset=".5"  stop-color="${c.base}"/>
        <stop offset="1"   stop-color="${c.dark}"/>
      </linearGradient>
      <radialGradient id="pf-head-${k}" cx="36%" cy="32%" r="70%">
        <stop offset="0"   stop-color="${c.hi}"/>
        <stop offset=".55" stop-color="${c.base}"/>
        <stop offset="1"   stop-color="${c.dark}"/>
      </radialGradient>
      <linearGradient id="pf-base-${k}" x1="0" y1="0" x2="1" y2="0">
        <stop offset="0"  stop-color="${c.dark}"/>
        <stop offset=".3" stop-color="${c.base}"/>
        <stop offset="1"  stop-color="${c.deep}"/>
      </linearGradient>
      <radialGradient id="pf-basetop-${k}" cx="40%" cy="35%" r="70%">
        <stop offset="0" stop-color="${c.base}"/>
        <stop offset="1" stop-color="${c.dark}"/>
      </radialGradient>`).join("");
    const h = document.createElement("div");
    h.innerHTML = `
<svg id="ink-piece-defs-v2" width="0" height="0" style="position:absolute;width:0;height:0" aria-hidden="true">
  <defs>
    ${grads}
    <linearGradient id="pf-beard" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0" stop-color="#fbf5e6"/><stop offset=".6" stop-color="#e8dcc0"/><stop offset="1" stop-color="#bfae8c"/>
    </linearGradient>
    <!-- ink outline wobble (subtle, keeps the silhouette crisp) -->
    <filter id="pf-ink" x="-10%" y="-10%" width="120%" height="120%">
      <feTurbulence type="fractalNoise" baseFrequency="0.08" numOctaves="2" seed="4" result="w"/>
      <feDisplacementMap in="SourceGraphic" in2="w" scale="1.1" xChannelSelector="R" yChannelSelector="G"/>
    </filter>
    <!-- cream brush detail lines -->
    <filter id="pf-brush" x="-15%" y="-15%" width="130%" height="130%">
      <feTurbulence type="fractalNoise" baseFrequency="0.12" numOctaves="2" seed="7" result="w"/>
      <feDisplacementMap in="SourceGraphic" in2="w" scale="1.2" xChannelSelector="R" yChannelSelector="G"/>
    </filter>
    <!-- fine grain so the resin doesn't look flat -->
    <filter id="pf-grain" x="0" y="0" width="100%" height="100%">
      <feTurbulence type="fractalNoise" baseFrequency="1.1" numOctaves="2" seed="11"/>
      <feColorMatrix type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 .35 -.1"/>
      <feComposite in2="SourceGraphic" operator="in"/>
    </filter>
    <filter id="pf-shadow" x="-40%" y="-80%" width="180%" height="260%">
      <feGaussianBlur stdDeviation="2.2"/>
    </filter>
  </defs>
</svg>`;
    document.body.appendChild(h.firstElementChild);
  }

  function el(tag, attrs, parent) {
    const e = document.createElementNS(NS, tag);
    for (const k in attrs) e.setAttribute(k, attrs[k]);
    if (parent) parent.appendChild(e);
    return e;
  }

  // Filled resin part with ink outline.
  function part(g, d, fill, extra = {}) {
    return el("path", Object.assign({ d, fill, stroke: INK, "stroke-width": 1.9, "stroke-linejoin": "round" }, extra), g);
  }
  // Cream painted detail line.
  function line(g, d, w = 1.3, op = .9) {
    return el("path", { d, fill: "none", stroke: CREAM, "stroke-width": w, "stroke-linecap": "round", "stroke-linejoin": "round", opacity: op }, g);
  }
  // Specular highlight streak.
  function sheen(g, d, op = .28) {
    return el("path", { d, fill: "none", stroke: "#fff", "stroke-width": 2.2, "stroke-linecap": "round", opacity: op }, g);
  }

  function base(s, side, cx, cy, rx, ry, depth) {
    el("ellipse", { cx: cx + 1.5, cy: cy + depth + 2, rx: rx + 2, ry: ry + 1.5, fill: "#000", opacity: .5, filter: "url(#pf-shadow)" }, s);
    const g = el("g", { filter: "url(#pf-ink)" }, s);
    // disc side band
    part(g, `M${cx - rx} ${cy} V${cy + depth} A${rx} ${ry} 0 0 0 ${cx + rx} ${cy + depth} V${cy} Z`, `url(#pf-base-${side})`);
    // disc top
    el("ellipse", { cx, cy, rx, ry, fill: `url(#pf-basetop-${side})`, stroke: INK, "stroke-width": 1.9 }, g);
    // cream rim line on the top face
    el("ellipse", { cx, cy, rx: rx - 3, ry: ry - 1.4, fill: "none", stroke: CREAM, "stroke-width": .9, opacity: .55 }, s);
  }

  // Two hakama legs in a wide stance (split, never a skirt).
  function legs(g, B, hipY, crotchY, footY, hipL, hipR, footOutL, footInL) {
    const cx = 50, fr = 100 - footOutL, fi = 100 - footInL, hr = 100 - hipL;
    part(g, `M${hipL} ${hipY} L${cx} ${hipY} L${cx} ${crotchY} L${footInL} ${footY} Q${(footInL + footOutL) / 2} ${footY + 1.6} ${footOutL} ${footY} Z`, B);
    part(g, `M${hr} ${hipY} L${cx} ${hipY} L${cx} ${crotchY} L${fi} ${footY} Q${(fi + fr) / 2} ${footY + 1.6} ${fr} ${footY} Z`, B);
  }

  function drawMaster(s, side) {
    const B = `url(#pf-body-${side})`, H = `url(#pf-head-${side})`, D = `url(#pf-base-${side})`;
    base(s, side, 50, 82, 28, 7.5, 4.5);
    const g = el("g", { filter: "url(#pf-ink)" }, s);
    // walking staff (behind everything else)
    part(g, "M23 5.5 Q24.6 4 26.2 5.5 L26.4 83 Q24.7 84.4 23 83 Z", D, { "stroke-width": 1.6 });
    // hakama: two legs, planted wide
    legs(g, B, 63, 70, 81.5, 38, 50, 29.5, 44);
    // heavy haori: broad square shoulders, straight sides
    part(g, "M31 35 Q30.5 32.5 34 32 L66 32 Q69.5 32.5 69 35 L67.5 66 Q50 68.5 32.5 66 Z", B);
    // obi belt in the open front
    part(g, "M40 55.5 H60 V61.5 H40 Z", D, { "stroke-width": 1.5 });
    // staff arm (viewer's left): wide sleeve down to the fist on the staff
    part(g, "M35 33.5 Q28.5 34.5 27 43 L25.5 53 Q27.5 56.5 31 55.5 L34.5 45 Z", B);
    el("circle", { cx: 26.6, cy: 55.5, r: 3.8, fill: H, stroke: INK, "stroke-width": 1.6 }, g);
    // other arm (viewer's right): hanging sleeve + forearm folded across the chest
    part(g, "M65 33.5 Q72 35 72.5 44 L73 58 Q70.5 60.5 66.5 58.5 L66 53 Q57 53.5 47 51.5 Q45.5 47.5 48.5 45.5 L65 44 Z", B);
    // long white beard over the chest
    part(g, "M44.8 24 Q44 32 46.3 39.5 Q48 45 50 49 Q52 45 53.7 39.5 Q56 32 55.2 24 Q50 27 44.8 24 Z", "url(#pf-beard)");
    // head, bun, tie
    el("circle", { cx: 50, cy: 19, r: 7.4, fill: H, stroke: INK, "stroke-width": 1.9 }, g);
    el("ellipse", { cx: 50, cy: 8.8, rx: 3.9, ry: 3.4, fill: "#2a2522", stroke: INK, "stroke-width": 1.6 }, g);
    el("path", { d: "M43 16.5 Q44.3 11.4 50 11.2 Q55.7 11.4 57 16.5 Q53 14.3 50 14.5 Q47 14.3 43 16.5 Z", fill: "#2a2522" }, g);

    const d = el("g", { filter: "url(#pf-brush)" }, s);
    el("path", { d: "M46 9 L54.4 8.1", stroke: GOLD, "stroke-width": 1.3, "stroke-linecap": "round" }, d);   // hairpin
    line(d, "M45.8 18.6 Q47.3 17.8 48.6 18.6 M51.4 18.6 Q52.7 17.8 54.2 18.6", 1.1, .85);             // brows
    line(d, "M46.7 23.2 Q50 22 53.3 23.2", 1.1, .75);                                                // moustache
    el("path", { d: "M47.8 28 Q48.3 37 50 45.5 M52.2 28 Q51.7 37 50 45.5", fill: "none", stroke: INK, "stroke-width": .8, opacity: .45 }, d);
    line(d, "M41.5 32.5 L44.5 39 M58.5 32.5 L55.5 39", 1.4);                                          // collar
    line(d, "M43.5 44 L42.5 66 M56.5 58 L57.5 66", 1.3, .8);                                         // haori front edges
    line(d, "M40.5 56.6 H59.5 M40.5 60.4 H59.5", .9, .8);                                            // obi edges
    el("path", { d: "M47.5 53.8 Q57 55.4 65.5 54.6", fill: "none", stroke: GOLD, "stroke-width": 1.2, opacity: .9 }, d); // cuff trim
    line(d, "M34 64.5 Q50 66.5 66 64.5", 1, .5);                                                     // haori hem
    line(d, "M42.5 66 L35.8 80 M57.5 66 L64.2 80 M46.5 66.5 L42.5 76 M53.5 66.5 L57.5 76", .9, .5); // hakama pleats
    line(d, "M22.5 20 H26.7 M22.5 23 H26.7", 1.2, .8);                                               // staff binding
    el("path", { d: "M29 81.2 Q36.8 83 44.2 81.3 M55.8 81.3 Q63.2 83 71 81.2", fill: "none", stroke: GOLD, "stroke-width": 1.3, opacity: .9 }, d); // hakama hems

    sheen(s, "M33.5 38 Q33 50 34 62", .25);
    sheen(s, "M36 67 L32 78", .22);
    sheen(s, "M45.6 14.6 Q47 13 49.4 12.8", .45);
  }

  function drawStudent(s, side) {
    const B = `url(#pf-body-${side})`, H = `url(#pf-head-${side})`, D = `url(#pf-base-${side})`;
    base(s, side, 50, 85, 24, 6.5, 4);
    const g = el("g", { filter: "url(#pf-ink)" }, s);
    // hakama legs, wide stance
    legs(g, B, 66, 71.5, 84, 40, 50, 33, 45);
    // neck + broad square-shouldered gi torso, tapering slightly to the waist
    part(g, "M46 42 H54 V49 H46 Z", H, { "stroke-width": 1.5 });
    part(g, "M34.5 47 L65.5 47 Q68.3 47.6 67.8 50.8 L61.5 67.5 Q50 69.3 38.5 67.5 L32.2 50.8 Q31.7 47.6 34.5 47 Z", B);
    // thick obi
    part(g, "M38.3 61.5 H61.7 V67.5 H38.3 Z", D, { "stroke-width": 1.6 });
    // raised salute arm (viewer's right): short sleeve, bare forearm, fist by the head
    part(g, "M63 47.2 Q69.5 46.8 72 52 L73.5 57.5 Q71.5 60.5 68 59.3 L65.5 54 Z", B);
    part(g, "M68.3 58 L67.6 41.5 L73.2 41.5 L73.6 57.2 Z", H, { "stroke-width": 1.6 });
    el("circle", { cx: 70.4, cy: 39.2, r: 3.9, fill: H, stroke: INK, "stroke-width": 1.7 }, g);
    // other arm (viewer's left): elbow out, fist planted on the hip
    part(g, "M37 47.2 Q30.5 46.8 28 52 L26.5 57.5 Q28.5 60.5 32 59.3 L34.5 54 Z", B);
    part(g, "M27.6 56.4 L37.4 61 L36 65.2 L26.6 60.6 Z", H, { "stroke-width": 1.6 });
    el("circle", { cx: 38.6, cy: 63.4, r: 3.6, fill: H, stroke: INK, "stroke-width": 1.6 }, g);
    // shaved head
    el("circle", { cx: 50, cy: 37.5, r: 7.4, fill: H, stroke: INK, "stroke-width": 1.9 }, g);

    const d = el("g", { filter: "url(#pf-brush)" }, s);
    line(d, "M45.9 37.2 Q47.2 36.4 48.5 37.2 M51.5 37.2 Q52.8 36.4 54.1 37.2", 1.1, .85);   // stern brows
    line(d, "M47.8 41.2 H52.2", 1, .6);                                                        // set mouth
    line(d, "M42.5 47.4 L51.5 59 M57.5 47.4 L50 56.5", 1.4);                                  // crossed gi collar
    line(d, "M39 62.6 H61 M39 66.4 H61", .9, .75);                                            // obi edges
    line(d, "M49 67.5 L47 73.5 M51.5 67.5 L53.5 73", 1.3, .85);                               // obi tie ends
    line(d, "M67.9 56.5 L73.5 56", 1, .7);                                                    // sleeve cuffs
    line(d, "M27.5 56 L32 58.8", 1, .7);
    line(d, "M43.5 69 L38.2 82.5 M56.5 69 L61.8 82.5", .9, .5);                               // hakama pleats
    line(d, "M33.6 82.8 Q39 84.4 44.4 82.8 M55.6 82.8 Q61 84.4 66.4 82.8", 1, .75);           // hems

    sheen(s, "M35.5 50.5 Q37.5 58 39.5 64", .25);
    sheen(s, "M40.5 69.5 L36 80", .22);
    sheen(s, "M45.6 33.3 Q47 31.7 49.4 31.5", .45);
  }

  function renderPiece({ type = "student", side = "red", size } = {}) {
    ensurePieceDefs();
    const s = el("svg", {
      viewBox: "0 0 100 100",
      class: `ink-piece ink-piece--${type} ink-piece--${side}`,
      role: "img",
      "aria-label": `${side} ${type}`,
      overflow: "visible",
    });
    if (size) { s.setAttribute("width", size); s.setAttribute("height", size); }
    const root = el("g", {}, s);
    (type === "master" ? drawMaster : drawStudent)(root, side);
    return s;
  }

  global.renderPiece = renderPiece;
  global.ensurePieceDefs = ensurePieceDefs;
  if (typeof module !== "undefined" && module.exports) module.exports = { renderPiece, ensurePieceDefs };
})(typeof window !== "undefined" ? window : globalThis);
