import { execFileSync } from "node:child_process";
import { mkdirSync, readdirSync, copyFileSync, statSync } from "node:fs";
import { join } from "node:path";

/**
 * Turns the recorded .webm files into the .gif files a README can render.
 *
 * <p>GitHub does not play video inline in a README, so the demo has to be a GIF
 * or it is a link nobody clicks. GIFs are also enormous by default, and the
 * inline render gives up past roughly 10 MB — hence the two-pass palette: one
 * generated from the clip's own colours, then applied with dithering. A flat
 * scale-and-encode of the same clip comes out several times larger and looks
 * worse.
 *
 * <p>Both formats are kept. The .webm is the better artifact for anyone who
 * clicks through, and it is what the .gif is regenerated from.
 *
 * <p>Run after `pnpm run demo:record`.
 */

const OUT = "../docs/media";
const RESULTS = "test-results";

/** Playwright names the output directory after the test, not the spec file. */
const CLIPS = [
  { match: "localmediakit-demo", name: "demo" },
  { match: "immutable-snapshot", name: "snapshot" },
];

const FILTER = (fps, width) =>
  `fps=${fps},scale=${width}:-1:flags=lanczos,split[a][b];` +
  `[a]palettegen=max_colors=128:stats_mode=diff[p];` +
  `[b][p]paletteuse=dither=bayer:bayer_scale=3`;

mkdirSync(OUT, { recursive: true });

function findVideo(match) {
  const dir = readdirSync(RESULTS, { withFileTypes: true })
    .filter((e) => e.isDirectory() && e.name.includes(match))
    .map((e) => join(RESULTS, e.name))[0];
  if (!dir) return null;
  const video = readdirSync(dir).find((f) => f.endsWith(".webm"));
  return video ? join(dir, video) : null;
}

const megabytes = (path) => statSync(path).size / 1024 / 1024;

for (const clip of CLIPS) {
  const source = findVideo(clip.match);
  if (!source) {
    console.warn(`skipped ${clip.name}: no recording found under ${RESULTS}`);
    continue;
  }

  const webm = join(OUT, `${clip.name}.webm`);
  const gif = join(OUT, `${clip.name}.gif`);
  copyFileSync(source, webm);

  // Step down until it fits. Losing frames costs less than a demo that renders
  // as a broken image because it went over the limit.
  const attempts = [
    { fps: 12, width: 960 },
    { fps: 10, width: 900 },
    { fps: 10, width: 800 },
    { fps: 8, width: 720 },
  ];
  for (const [i, { fps, width }] of attempts.entries()) {
    execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-i", webm, "-vf", FILTER(fps, width), gif]);
    const size = megabytes(gif);
    if (size <= 9.5 || i === attempts.length - 1) {
      console.log(`${clip.name}.gif  ${size.toFixed(1)} MB  (${fps}fps ${width}px)`);
      break;
    }
  }
  console.log(`${clip.name}.webm ${megabytes(webm).toFixed(1)} MB`);
}
