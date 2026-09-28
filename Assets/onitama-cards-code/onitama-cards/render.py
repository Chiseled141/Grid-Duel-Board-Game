"""Re-render preview-all.png and png/<id>-<side>.png with the system Chrome.
Waits for fonts + every <img> to be decoded before each screenshot."""
import asyncio, pathlib
from playwright.async_api import async_playwright

HERE = pathlib.Path(__file__).resolve().parent
IDS = "tiger dragon frog rabbit crab elephant goose rooster monkey mantis horse ox crane boar eel cobra".split()
READY = """async () => { await document.fonts.ready;
  await Promise.all([...document.images].map(i => i.decode().catch(() => {}))); }"""

async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch(channel="chrome", args=["--no-sandbox"])
        pg = await b.new_page(viewport={"width": 1800, "height": 2100})
        await pg.goto((HERE / "cards.html").as_uri()); await pg.evaluate(READY)
        await pg.screenshot(path=str(HERE / "preview-all.png"), full_page=True)
        ctx = await b.new_context(viewport={"width": 400, "height": 460}, device_scale_factor=2)
        pg = await ctx.new_page()
        (HERE / "png").mkdir(exist_ok=True)
        for i in IDS:
            for s in ("red", "blue"):
                await pg.goto((HERE / "single.html").as_uri() + f"?id={i}&side={s}")
                await pg.evaluate(READY)
                await pg.screenshot(path=str(HERE / "png" / f"{i}-{s}.png"), omit_background=True)
        await b.close()

asyncio.run(main())
