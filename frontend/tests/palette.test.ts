import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

/**
 * The accent palette is the product's answer to "what stops a user making an
 * unreadable page?" — a fixed set whose contrast is verified, rather than a
 * colour input plus hope. These tests are that verification: they recompute
 * every ratio from the stylesheet that actually ships, so an accent added
 * later with poor contrast cannot be merged.
 */

const css = readFileSync(resolve(process.cwd(), "app/globals.css"), "utf8");
const ogSource = readFileSync(resolve(process.cwd(), "app/[slug]/opengraph-image.tsx"), "utf8");

const AA = 4.5;
const ACCENTS = ["ocean", "forest", "amber", "rose", "graphite"];

/**
 * Returns every declaration block whose selector list mentions `selector`.
 *
 * <p>The stylesheet mentions the theme selectors in more than one place (a
 * Tailwind @custom-variant, a print override), so taking the first textual
 * match would read the wrong block. Callers pick the block that actually
 * declares the variable they want.
 */
function blocksFor(selector: string): string[] {
  const blocks: string[] = [];
  let from = 0;
  for (;;) {
    const at = css.indexOf(selector, from);
    if (at === -1) break;
    const open = css.indexOf("{", at);
    const close = css.indexOf("}", open);
    if (open !== -1 && close !== -1) blocks.push(css.slice(open, close));
    from = at + selector.length;
  }
  if (blocks.length === 0) throw new Error(`selector not found: ${selector}`);
  return blocks;
}

/** First block under `selector` that declares `--name`. */
function varIn(selector: string, name: string): string {
  for (const block of blocksFor(selector)) {
    const match = new RegExp(`--${name}:\\s*(#[0-9a-fA-F]{6})`).exec(block);
    if (match) return match[1];
  }
  throw new Error(`--${name} not declared under ${selector}`);
}

const LIGHT = '[data-theme="light"]';
const DARK = '[data-theme="dark"]';

const SURFACES = {
  light: { page: varIn(LIGHT, "page"), surface: varIn(LIGHT, "surface") },
  dark: { page: varIn(DARK, "page"), surface: varIn(DARK, "surface") },
};
const WHITE = "#ffffff";

function relativeLuminance(hex: string): number {
  const channels = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255);
  const [r, g, b] = channels.map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [relativeLuminance(a), relativeLuminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

/** Reads one accent's three variables for a mode straight out of globals.css. */
function accentVars(accent: string, mode: "light" | "dark") {
  const base = `[data-accent="${accent}"]`;
  if (mode === "light") {
    return {
      brand: varIn(base, "brand"),
      strong: varIn(base, "brand-strong"),
      weak: varIn(base, "brand-weak"),
    };
  }
  const darkAccent = `[data-theme="dark"][data-accent="${accent}"]`;
  return {
    brand: varIn(darkAccent, "brand"),
    // Dark mode does not override the button background; it keeps the light
    // one, which is exactly the value white was verified against.
    strong: varIn(base, "brand-strong"),
    weak: varIn(darkAccent, "brand-weak"),
  };
}

describe("accent palette contrast", () => {
  for (const accent of ACCENTS) {
    for (const mode of ["light", "dark"] as const) {
      it(`${accent} clears AA on every surface it is drawn on (${mode})`, () => {
        const { brand, strong, weak } = accentVars(accent, mode);
        const { page, surface } = SURFACES[mode];

        // --brand is accent text and icons.
        expect(contrast(brand, page), `${accent}/${mode} brand on page`).toBeGreaterThanOrEqual(AA);
        expect(contrast(brand, surface), `${accent}/${mode} brand on surface`).toBeGreaterThanOrEqual(AA);
        // Accent text is also drawn on the accent wash (badges, banners).
        expect(contrast(brand, weak), `${accent}/${mode} brand on weak`).toBeGreaterThanOrEqual(AA);
        // --brand-strong is a solid button background with white text.
        expect(contrast(WHITE, strong), `${accent}/${mode} white on strong`).toBeGreaterThanOrEqual(AA);
      });
    }
  }

  it("keeps the original violet untouched so published pages do not change", () => {
    // violet is the base palette and must NOT be redefined as an accent block,
    // otherwise every page published before accents existed would shift.
    expect(css).not.toMatch(/\[data-accent="violet"\]/);
    expect(varIn(LIGHT, "brand")).toBe("#6d40e6");
    expect(varIn(DARK, "brand")).toBe("#a998f8");
  });

  it("clears AA for the default violet too", () => {
    for (const mode of ["light", "dark"] as const) {
      const root = mode === "light" ? LIGHT : DARK;
      const { page, surface } = SURFACES[mode];
      const brand = varIn(root, "brand");
      expect(contrast(brand, page)).toBeGreaterThanOrEqual(AA);
      expect(contrast(brand, surface)).toBeGreaterThanOrEqual(AA);
      expect(contrast(brand, varIn(root, "brand-weak"))).toBeGreaterThanOrEqual(AA);
      expect(contrast(WHITE, varIn(root, "brand-strong"))).toBeGreaterThanOrEqual(AA);
    }
  });

  /**
   * The social card cannot read CSS variables, so its palette is restated in
   * the route. Restated values drift; this pins them together.
   */
  it("keeps the social card's copy of the palette in step with the stylesheet", () => {
    for (const accent of ACCENTS) {
      for (const mode of ["light", "dark"] as const) {
        const { brand, weak } = accentVars(accent, mode);
        const entry = new RegExp(
          // [\s\S]*? rather than [^}]*: the accent entry nests its own braces.
          `${accent}:\\s*\\{[\\s\\S]*?${mode}:\\s*\\{\\s*brand:\\s*"([^"]+)",\\s*brandWeak:\\s*"([^"]+)"`
        ).exec(ogSource);
        expect(entry, `${accent}/${mode} missing from OG_ACCENTS`).not.toBeNull();
        expect(entry![1].toLowerCase(), `${accent}/${mode} brand`).toBe(brand.toLowerCase());
        expect(entry![2].toLowerCase(), `${accent}/${mode} brandWeak`).toBe(weak.toLowerCase());
      }
    }
  });
});
