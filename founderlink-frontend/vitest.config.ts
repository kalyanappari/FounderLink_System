import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    coverage: {
      enabled: true,                // 🔥 IMPORTANT
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'], // 🔥 MUST include lcov
      reportsDirectory: './coverage',
      all: true                     // optional but good
    },
  },
});