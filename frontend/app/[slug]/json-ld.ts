/**
 * Serialises structured data for an inline <script type="application/ld+json">.
 *
 * JSON.stringify is NOT safe to drop into a script element on its own. It
 * escapes what JSON requires and nothing more, so a "<" survives verbatim — and
 * every string in this payload is content the kit's owner typed: the display
 * name, the headline, the avatar URL. A headline containing a closing script tag
 * therefore ends the block early and whatever follows is parsed as markup, on a
 * page that is sent to brands and cached at the edge. The CSP allows inline
 * script (there is no nonce, because the page must stay force-static), so it
 * would run.
 *
 * Escaping the angle brackets to their \\u003c / \\u003e form is enough to make
 * that impossible: inside a JSON string those denote the same characters, so the
 * structured data a crawler reads is unchanged, but the HTML parser can no
 * longer find a tag to close. The ampersand and the two Unicode line separators
 * are escaped alongside them — the same short list of sequences an HTML or
 * script parser treats specially inside this element.
 */
export function serializeJsonLd(data: unknown): string {
  return JSON.stringify(data).replace(
    /[<>&\u2028\u2029]/g,
    (char) => "\\u" + char.charCodeAt(0).toString(16).padStart(4, "0")
  );
}
