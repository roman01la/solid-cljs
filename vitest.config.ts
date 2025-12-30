import { defineConfig } from 'vitest/config'
import { playwright } from '@vitest/browser-playwright'
import solidPlugin from 'vite-plugin-solid'

export default defineConfig({
  plugins: [solidPlugin()],
  test: {
    include: [
      // relies on the config at shadow-cljs.edn, which outputs these files
      './out/vitest/vitest.*.js',
    ],
    // testTimeout default is between 5 to 15 seconds, but keeping it low might be productive:
    testTimeout: 1_000, // https://vitest.dev/config/testtimeout.html
    browser: {
      provider: playwright(),
      enabled: true,
      // at least one instance is required
      instances: [
        { browser: 'chromium' },
      ],
    },

  }
})
