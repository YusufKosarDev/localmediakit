// TypeScript 6 refuses a side-effect import it has no declaration for, and
// `import "./globals.css"` in the root layout is exactly that: the stylesheet
// is handled by the bundler and contributes no types. This says so.
declare module "*.css";
