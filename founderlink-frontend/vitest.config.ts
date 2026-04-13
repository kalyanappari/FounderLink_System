import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      reportsDirectory: './coverage/founderlink-frontend',
      exclude: [
        'node_modules/',
        'src/test.ts',
        '**/*.spec.ts',
        '**/*.config.ts',
        'src/environments/**',
        'src/main.ts',
        'src/assets/**'
      ]
    }
  }
});
