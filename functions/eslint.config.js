const {FlatCompat} = require("@eslint/eslintrc");
const js = require("@eslint/js");
const globals = require("globals");

const compat = new FlatCompat({
  baseDirectory: __dirname,
  recommendedConfig: js.configs.recommended,
});

module.exports = [
  {ignores: ["node_modules/**"]},
  ...compat.extends("eslint:recommended", "google"),
  {
    languageOptions: {
      ecmaVersion: 2020,
      sourceType: "commonjs",
      globals: {
        ...globals.node,
        ...globals.es6,
      },
    },
    rules: {
      // eslint-config-google predates ESLint's flat-config/v9+ era and
      // still references these two core rules, which were removed
      // upstream (superseded by JSDoc-specific lint plugins).
      "valid-jsdoc": "off",
      "require-jsdoc": "off",
      "no-restricted-globals": ["error", "name", "length"],
      "prefer-arrow-callback": "error",
      "quotes": ["error", "double", {"allowTemplateLiterals": true}],
    },
  },
  {
    files: ["**/*.spec.*"],
    languageOptions: {
      globals: {
        ...globals.mocha,
      },
    },
  },
];
