import { describe, it, expect } from "vitest";
import { serializeJsonLd } from "@/app/[slug]/json-ld";

/**
 * The public page injects its structured data with dangerouslySetInnerHTML, and
 * every string inside it is text the kit's owner typed. These assertions exist
 * so that stays safe: a plain JSON.stringify would pass the "is it valid JSON"
 * checks below and still let a headline close the script element.
 */
describe("serializeJsonLd", () => {
  const breakout = '</script><script>alert(1)</script>';

  it("leaves no sequence an HTML parser can read as a tag", () => {
    const html = serializeJsonLd({ name: breakout });

    expect(html).not.toContain("<");
    expect(html).not.toContain(">");
    expect(html).toContain("\\u003c");
  });

  it("preserves the value, so crawlers read exactly what was published", () => {
    const payload = { name: breakout, headline: "5 < 6 & 7 > 6" };

    expect(JSON.parse(serializeJsonLd(payload))).toEqual(payload);
  });

  it("escapes the line separators that terminate a script line", () => {
    // U+2028 and U+2029 are valid inside a JSON string but end a line for a
    // script parser, so they have to leave as escapes like the brackets do.
    const html = serializeJsonLd({ name: "a\u2028b\u2029c" });

    expect(html).not.toContain("\u2028");
    expect(html).not.toContain("\u2029");
    expect(JSON.parse(html)).toEqual({ name: "a\u2028b\u2029c" });
  });

  it("survives the shapes the page actually builds", () => {
    const jsonLd = {
      "@context": "https://schema.org",
      "@type": "ProfilePage",
      url: "https://localmediakit.vercel.app/kit",
      mainEntity: {
        "@type": "Person",
        name: breakout,
        interactionStatistic: [{ "@type": "InteractionCounter", userInteractionCount: 1000 }],
      },
    };

    const html = serializeJsonLd(jsonLd);

    expect(html).not.toContain("</script");
    expect(JSON.parse(html)).toEqual(jsonLd);
  });
});
