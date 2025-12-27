# Vitest

ATM uses the following in `package.json`.

```
    "vitest": "shadow-cljs compile vitest && vitest --browser=chromium",
    "vitest:watch": "shadow-cljs watch vitest",
```

In general:
- Add tests to `./test/vitest/*`.
- Namespace must match the regexp in `:ns-regexp` found within `shadow-cljs.edn` for `:vitest`.
- `include` in `vitest.config.ts` must match the equivalent name pattern in the output `js` files.

## Other notes:

- Uses the browser to run the tests.
- Might require installing playwright `npx playwright install`.
- Docs for vitest start around here: https://main.vitest.dev/guide/browser/#examples
- Inspiration to avoid JSDOM: 
    * https://www.epicweb.dev/why-i-won-t-use-jsdom
    * https://github.com/jsdom/jsdom/wiki/Don't-stuff-jsdom-globals-onto-the-Node-global
