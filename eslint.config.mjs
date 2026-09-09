import js from "@eslint/js";
import ts from "typescript-eslint";
import vue from "eslint-plugin-vue";

/**
 * One flat config for every TypeScript and Vue file in the repo: the shared
 * `src/`, the desktop client and the web client.
 *
 * Deliberately not type-aware. The type-aware rules need a program per app and
 * roughly triple the run time, and both apps already run `vue-tsc` in CI — so
 * the types are checked there, and this catches what the compiler does not:
 * unused code, shadowed bindings, floating promises' cheaper cousins, and Vue
 * template mistakes.
 *
 * Rules that would flag existing, deliberate code are set to `warn` rather than
 * turned off, so `--max-warnings` can be tightened over time instead of the
 * problem being made invisible.
 */
export default ts.config(
  {
    ignores: [
      "**/dist/**",
      "**/node_modules/**",
      "**/target/**",
      "**/build/**",
      "docs-site/**",
      // Machine-written; regenerate it rather than lint it.
      "src/api/schema.d.ts",
    ],
  },

  js.configs.recommended,
  ...ts.configs.recommended,
  ...vue.configs["flat/recommended"],

  {
    files: ["**/*.{ts,mts,vue}"],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: "module",
      parserOptions: {
        parser: ts.parser,
        extraFileExtensions: [".vue"],
      },
    },
    rules: {
      // TypeScript resolves every identifier already, including the DOM lib and
      // Vite's compile-time defines. Leaving this on means maintaining a globals
      // list that duplicates tsconfig's `lib` and only ever goes stale.
      "no-undef": "off",

      // An unused parameter is often deliberate (a signature the caller
      // dictates); an unused *variable* almost never is.
      "@typescript-eslint/no-unused-vars": [
        "error",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_", caughtErrors: "none" },
      ],
      // `any` defeats the generated API types, which is the whole point of the
      // contract — but there are a couple of justified ones already.
      "@typescript-eslint/no-explicit-any": "warn",
      "no-console": ["warn", { allow: ["warn", "error"] }],
      eqeqeq: ["error", "always", { null: "ignore" }],
      "prefer-const": "error",
      "no-var": "error",
      // Multi-word names are a Vue convention this codebase does not follow
      // (Sidebar, Toast, MageIcon) and renaming them is not worth the churn.
      "vue/multi-word-component-names": "off",
      // The app's templates are formatted by hand and read fine; these are
      // stylistic and would rewrite nearly every file.
      "vue/max-attributes-per-line": "off",
      "vue/singleline-html-element-content-newline": "off",
      "vue/html-self-closing": "off",
      "vue/html-indent": "off",
      "vue/html-closing-bracket-newline": "off",
      "vue/attributes-order": "off",
      "vue/multiline-html-element-content-newline": "off",
      // TypeScript already says which props are optional, and `undefined` is
      // often the meaningful value ("no accent override" rather than a colour).
      "vue/require-default-prop": "off",
    },
  },

  {
    // The two icon components render SVG with v-html because that IS their job.
    // The markup comes from feather-icons (a build-time dependency) and from
    // SVGs vendored in web-client/src/assets/mage-icons — never from user input
    // or from the server. A template-level disable comment would give these
    // components a second root node, which changes how they mount.
    files: ["src/components/FeatherIcon.vue", "web-client/src/components/MageIcon.vue"],
    rules: { "vue/no-v-html": "off" },
  },

  {
    // The codegen scripts are Node programs, not browser code.
    files: ["scripts/**/*.mjs", "*.config.mjs", "*.config.mts"],
    languageOptions: {
      globals: { process: "readonly", console: "readonly" },
    },
  },

  {
    // Tests reach for stubs and fixtures that the app code should not.
    files: ["**/*.test.ts", "scripts/**"],
    rules: {
      "no-console": "off",
      "@typescript-eslint/no-explicit-any": "off",
    },
  },
);
