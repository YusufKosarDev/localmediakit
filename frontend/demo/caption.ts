import type { Page } from "@playwright/test";

/**
 * A caption bar for the recordings.
 *
 * <p>These end up in a README as silent, autoplaying GIFs with no controls and
 * no sound, which a reader scrolls past in a few seconds. Without a line of
 * text saying what is happening, the second recording in particular — a page
 * that deliberately does NOT change — looks like nothing at all.
 *
 * <p>Styled to read as an overlay rather than product chrome: fixed to the
 * bottom, dark, rounded, clearly sitting on top of the page. Nobody should come
 * away thinking the application ships a caption bar.
 */
export async function caption(page: Page, text: string) {
  await page.evaluate((message) => {
    const id = "__demo_caption";
    let bar = document.getElementById(id);
    if (!bar) {
      bar = document.createElement("div");
      bar.id = id;
      Object.assign(bar.style, {
        position: "fixed",
        left: "50%",
        bottom: "28px",
        transform: "translateX(-50%)",
        zIndex: "2147483647",
        padding: "12px 22px",
        borderRadius: "999px",
        background: "rgba(17, 17, 20, 0.92)",
        color: "#fff",
        font: "500 16px/1.4 ui-sans-serif, system-ui, -apple-system, sans-serif",
        letterSpacing: "0.01em",
        boxShadow: "0 8px 30px rgba(0,0,0,0.35)",
        pointerEvents: "none",
        maxWidth: "80vw",
        textAlign: "center",
        transition: "opacity 180ms ease",
      } satisfies Partial<CSSStyleDeclaration>);
      document.body.appendChild(bar);
    }
    bar.textContent = message;
    bar.style.opacity = "1";
  }, text);
}

/** Drops the caption before a screenshot, so stills stay free of it. */
export async function clearCaption(page: Page) {
  await page.evaluate(() => document.getElementById("__demo_caption")?.remove());
}
