import { fileURLToPath } from 'node:url'
import { mergeConfig } from 'vite'
import { configDefaults, defineConfig } from 'vitest/config'
import TrevorismTestResultReporter from '@trevorism/vitest-test-result-events'
import viteConfig from './vite.config'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      exclude: [...configDefaults.exclude, 'e2e/*'],
      server: { deps: { inline: ['@trevorism/ui-header-bar'] } },
      root: fileURLToPath(new URL('./', import.meta.url)),
      reporters: ['default', new TrevorismTestResultReporter('certs')]
    }
  })
)
