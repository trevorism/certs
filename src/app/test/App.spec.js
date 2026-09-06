import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createVuestic } from 'vuestic-ui'
import VueClickAway from 'vue3-click-away'
import App from '../src/App.vue'
import { MenuBar } from '@trevorism/ui-header-bar'

vi.mock('axios', () => ({
  default: { get: vi.fn(() => Promise.resolve({ data: [] })), post: vi.fn() }
}))

vi.mock('@trevorism/ui-auth', async () => {
  const { computed } = await import('vue')
  return {
    ensureBootstrapped: vi.fn(),
    useAuth: () => ({
      user: computed(() => ({ username: 'tester' })),
      isAuthenticated: computed(() => true),
      isAdmin: computed(() => false),
      loading: computed(() => false),
      ready: Promise.resolve(),
      login: vi.fn(),
      logout: vi.fn()
    })
  }
})

function mountApp() {
  return mount(App, {
    global: {
      plugins: [createVuestic(), VueClickAway],
      stubs: { CertificateTable: true }
    }
  })
}

describe('App', () => {
  it('resolves the named header bar export', () => {
    expect(MenuBar).toBeDefined()
    expect(typeof MenuBar).toBe('object')
  })

  it('renders the header bar alongside the certificate table', () => {
    const wrapper = mountApp()

    expect(wrapper.findComponent(MenuBar).exists()).toBe(true)
    expect(wrapper.find('certificate-table-stub').exists()).toBe(true)
  })

  it('shows the signed in user in the bar rather than a login link', () => {
    const wrapper = mountApp()

    expect(wrapper.text()).toContain('tester')
    expect(wrapper.text()).not.toContain('Login')
  })
})
