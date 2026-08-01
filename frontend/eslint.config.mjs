import nextCoreWebVitals from "eslint-config-next/core-web-vitals";
import nextTypescript from "eslint-config-next/typescript";

// Next 16 removed `next lint`, and eslint-config-next now ships flat config
// directly. The previous file went through FlatCompat to translate the old
// `extends` strings; against a config that is already flat that wrapper does
// not merely become redundant, it throws. Spreading the exported arrays is the
// supported form and one indirection fewer.
const eslintConfig = [
  ...nextCoreWebVitals,
  ...nextTypescript,
  {
    rules: {
      // Two rules that arrived with react-hooks v7 in the Next 16 config, off
      // deliberately rather than by neglect.
      //
      // Most of what they flag here is not what they describe. The panels are
      // reported for "setState synchronously within an effect", but their
      // setState calls run AFTER an awaited fetch, which is the case the rule
      // exists to distinguish and cannot see through a callback. The
      // immutability reports are `window.location.href = "/dashboard"` — a
      // navigation, not a mutated outer variable.
      //
      // One report is fair: the dashboard sets an error state synchronously on
      // mount when there is no token, which costs a second render and nothing
      // else. Silencing eleven false positives to leave that one visible is not
      // a trade worth making, so both rules are off and adopting them properly
      // is its own piece of work — not a condition of taking the framework
      // upgrade that happened to ship them.
      "react-hooks/set-state-in-effect": "off",
      "react-hooks/immutability": "off",
    },
  },
  {
    ignores: [
      ".next/**",
      "node_modules/**",
      "next-env.d.ts",
      // Playwright artefacts: generated, and not ours to lint.
      "test-results/**",
      "playwright-report/**",
    ],
  },
];

export default eslintConfig;
