"""Render preview.png, piece PNGs and the empty board with the system Chrome.
Waits for fonts + images before each screenshot."""
import asyncio, pathlib
from playwright.async_api import async_playwright

HERE = pathlib.Path(__file__).resolve().parent
READY = """async () => { await document.fonts.ready;
  await Promise.all([...document.images].map(i => i.decode().catch(() => {})));
  await new Promise(r => setTimeout(r, 300)); }"""

async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch(channel="chrome", args=["--no-sandbox"])
        pg = await b.new_page(viewport={"width": 1280, "height": 800})
        await pg.goto((HERE / "board.html").as_uri()); await pg.evaluate(READY)
        await pg.screenshot(path=str(HERE / "preview.png"))

        ctx = await b.new_context(viewport={"width": 256, "height": 256})
        pg = await ctx.new_page()
        (HERE / "png").mkdir(exist_ok=True)
        for t in ("master", "student"):
            for s in ("red", "blue"):
                await pg.goto((HERE / "pieces.html").as_uri() + f"?piece={t}-{s}")
                await pg.evaluate(READY)
                await pg.screenshot(path=str(HERE / "png" / f"{t}-{s}.png"), omit_background=True)

        ctx = await b.new_context(viewport={"width": 1000, "height": 1000}, device_scale_factor=2)
        pg = await ctx.new_page()
        await pg.goto((HERE / "pieces.html").as_uri() + "?board=empty"); await pg.evaluate(READY)
        bb = await pg.locator("body > .ink-board").bounding_box()   # include the 7px offset shadow
        await pg.screenshot(path=str(HERE / "png" / "board-empty.png"), omit_background=True,
                            clip={"x": 0, "y": 0, "width": bb["width"] + 8, "height": bb["height"] + 8})
        await b.close()

asyncio.run(main())
