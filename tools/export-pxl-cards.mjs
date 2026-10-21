// Authoring only: turn pxl's exact pixels into compact Java drawing instructions.
// Usage: node tools/export-pxl-cards.mjs [path-to-claude-pxl] [--check]
import { readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const pxlRoot = process.argv.slice(2).find(arg => arg !== '--check')
  ?? resolve(homedir(), '.claude/skills/pxl-pixel-art');
const { loadProject } = await import(pathToFileURL(resolve(pxlRoot, 'src/node/load.ts')));
const project = loadProject(resolve(root, 'art/cards/pxl/spells.px.js'));
const names = ['disable-tags','disable-points','destroy-unit','random-discard','random-steal',
  'draw-two','exchange','reconnaissance','spy','clear-disable','card-back'];
const entries = names.map(name => {
  const image = project.image(name);
  if (image.w !== 80 || image.h !== 120) throw Error(`${name}: expected 80x120`);
  const palette = [0];
  for (const pixel of image.data) {
    if ((pixel & 255) !== 0 && (pixel & 255) !== 255) throw Error(`${name}: partial alpha`);
    if (!palette.includes(pixel)) palette.push(pixel);
  }
  if (palette.length > 16) throw Error(`${name}: exceeds 16 colours`);
  const rows = [];
  for (let y = 0; y < image.h; y++) {
    let row = '';
    for (let x = 0; x < image.w;) {
      const pixel = image.data[y * image.w + x];
      let end = x + 1;
      while (end < image.w && image.data[y * image.w + end] === pixel) end++;
      row += palette.indexOf(pixel).toString(16) + (end-x).toString(16).padStart(2,'0');
      x = end;
    }
    rows.push(`                    "${row}"`);
  }
  const colors = palette.map(value => `new Color(0x${(((value >>> 8) | ((value & 255) << 24)) >>> 0).toString(16).padStart(8,'0')}, true)`);
  return `            Map.entry("${name}", new Art(new Color[] {\n                    ${colors.join(',\n                    ')}\n            }, new String[] {\n${rows.join(',\n')}\n            }))`;
});
const java = `package deckduel.client.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Map;

/** Generated from art/cards/pxl/spells.px.js by tools/export-pxl-cards.mjs. Do not hand-edit. */
final class PxlCardArt {
    private PxlCardArt() { }

    /** Draws exact pxl pixels without file I/O, image decoding or a runtime Node dependency. */
    static void paint(Graphics2D g, String id) {
        Art art = ART.get(id);
        if (art == null) throw new IllegalArgumentException("No pxl illustration for " + id);
        for (int y = 0; y < art.rows().length; y++) {
            String row = art.rows()[y];
            int x = 0;
            for (int offset = 0; offset < row.length(); offset += 3) {
                Color color = art.palette()[Character.digit(row.charAt(offset), 16)];
                int width = Integer.parseInt(row.substring(offset + 1, offset + 3), 16);
                if (color.getAlpha() != 0) {
                    g.setColor(color);
                    g.fillRect(x, y, width, 1);
                }
                x += width;
            }
        }
    }

    private record Art(Color[] palette, String[] rows) { }

    private static final Map<String, Art> ART = Map.ofEntries(
${entries.join(',\n')});
}
`;
const destination = resolve(root, 'src/main/java/deckduel/client/ui/PxlCardArt.java');
if (process.argv.includes('--check')) {
  if (readFileSync(destination, 'utf8') !== java) throw Error('PxlCardArt.java is stale; regenerate it');
  console.log(`PASS: all ${names.length} runtime designs exactly match pxl source`);
} else {
  writeFileSync(destination, java);
  console.log(`Exported ${names.length} designs to ${destination}`);
}
