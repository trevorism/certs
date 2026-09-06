import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import CertificateTable from '../src/components/CertificateTable.vue'


const CERTIFICATES = [
  {
    id: '1',
    category: 'draw',
    wildcard: '*.draw.trevorism.com',
    gcpProject: 'trevorism-draw',
    notAfter: '2099-12-01T00:00:00Z',
    lastOutcome: 'COMPLETED'
  },
  {
    id: '2',
    category: 'testing',
    wildcard: '*.testing.trevorism.com',
    gcpProject: 'trevorism-testing'
  }
]

const state = vi.hoisted(() => ({
  failList: false,
  failStatus: 401,
  failVerify: false,
  deferVerify: false,
  pendingVerify: [],
  certificates: []
}))

const auth = vi.hoisted(() => ({ session: null, login: vi.fn() }))

vi.mock('@trevorism/ui-auth', async () => {
  const { reactive, computed } = await import('vue')
  auth.session = reactive({ admin: true, authenticated: true })
  return {
    useAuth: () => ({
      isAdmin: computed(() => auth.session.admin),
      isAuthenticated: computed(() => auth.session.authenticated),
      ready: Promise.resolve(),
      login: auth.login
    })
  }
})

vi.mock('axios', () => ({
  default: {
    get: vi.fn((url) => {
      if (url === 'api/certificate') {
        return state.failList
          ? Promise.reject({ response: { status: state.failStatus } })
          : Promise.resolve({ data: state.certificates })
      }
      if (state.deferVerify) {
        return new Promise((resolve, reject) => state.pendingVerify.push({ resolve, reject }))
      }
      if (state.failVerify) {
        return Promise.reject({ response: { status: 500 } })
      }
      return Promise.resolve({ data: { matches: true, expectedSerial: 'abc' } })
    }),
    post: vi.fn(() =>
      Promise.resolve({ data: { outcome: 'COMPLETED', wildcard: '*.draw.trevorism.com' } })
    )
  }
}))

const stubs = {
  'va-badge': { props: ['text', 'color'], template: '<span class="badge">{{ text }}</span>' },
  'va-button': { template: '<button><slot /></button>' },
  'va-alert': { template: '<div class="alert"><slot /></div>' },
  'va-modal': { template: '<div><slot /></div>' },
  'va-inner-loading': { template: '<div><slot /></div>' }
}

function signIn({ admin = true } = {}) {
  auth.session.authenticated = true
  auth.session.admin = admin
}

function signOut() {
  auth.session.authenticated = false
  auth.session.admin = false
}

async function mountTable() {
  const wrapper = mount(CertificateTable, { global: { stubs } })
  await flushPromises()
  return wrapper
}

function headerFor(wrapper, label) {
  return wrapper
    .findAll('thead button')
    .find((button) => button.text().startsWith(label))
}

function categoryOrder(wrapper) {
  return wrapper.findAll('tbody tr').map((row) => row.findAll('td')[0].text())
}

describe('CertificateTable', () => {
  beforeEach(() => {
    state.failList = false
    state.failStatus = 401
    state.failVerify = false
    state.deferVerify = false
    state.pendingVerify = []
    state.certificates = CERTIFICATES
    auth.login.mockClear()
    signIn()
  })

  it('renders a row for every certificate', async () => {
    const wrapper = await mountTable()
    expect(wrapper.findAll('tbody tr')).toHaveLength(2)
  })

  it('shows each wildcard and its project', async () => {
    const wrapper = await mountTable()
    expect(wrapper.text()).toContain('*.draw.trevorism.com')
    expect(wrapper.text()).toContain('trevorism-draw')
  })

  it('puts the never-rotated certificate first', async () => {
    const wrapper = await mountTable()
    expect(wrapper.findAll('tbody tr')[0].text()).toContain('testing')
  })

  it('shows unknown expiry for a certificate that has never rotated', async () => {
    const wrapper = await mountTable()
    expect(wrapper.text()).toContain('unknown')
  })

  it('reports the edge as serving once verification returns', async () => {
    const wrapper = await mountTable()
    expect(wrapper.text()).toContain('serving')
  })

  it('shows never for a certificate with no recorded outcome', async () => {
    const wrapper = await mountTable()
    expect(wrapper.text()).toContain('never')
  })

  it('surfaces an error when the list cannot be loaded', async () => {
    state.failList = true
    state.failStatus = 500
    const wrapper = await mountTable()
    expect(wrapper.find('.alert').text()).toContain('Unable to load certificates')
    expect(wrapper.findAll('tbody tr')).toHaveLength(0)
    expect(wrapper.text()).not.toContain('Sign in to see')
  })

  it('offers a way back in when the session could not be refreshed', async () => {
    state.failList = true
    const wrapper = await mountTable()
    expect(wrapper.text()).toContain('Sign in to see')
    expect(wrapper.find('.alert').exists()).toBe(false)
    expect(wrapper.findAll('tbody tr')).toHaveLength(0)
  })

  it('tells a rejected account it has no access rather than looping through login', async () => {
    state.failList = true
    state.failStatus = 403
    const wrapper = await mountTable()
    expect(wrapper.text()).not.toContain('Sign in to see')
    expect(wrapper.find('.alert').text()).toContain('does not have access')
  })

  it('shows a signed out visitor the sign in page and never calls the api', async () => {
    signOut()
    const axios = (await import('axios')).default
    axios.get.mockClear()
    const wrapper = await mountTable()
    expect(wrapper.text()).toContain('Sign in to see')
    expect(wrapper.findAll('tbody tr')).toHaveLength(0)
    expect(axios.get).not.toHaveBeenCalled()
    expect(auth.login).not.toHaveBeenCalled()
  })

  it('starts the login handoff when the visitor clicks sign in', async () => {
    signOut()
    const wrapper = await mountTable()

    await wrapper.findAll('button').find((button) => button.text() === 'Sign in').trigger('click')

    expect(auth.login).toHaveBeenCalledTimes(1)
  })

  it('reveals the rotate button when the session becomes an administrator', async () => {
    signIn({ admin: false })
    const wrapper = await mountTable()
    expect(wrapper.text()).not.toContain('Rotate')

    auth.session.admin = true
    await flushPromises()

    expect(wrapper.text()).toContain('Rotate')
  })

  it('hides the rotate button from a non administrator', async () => {
    signIn({ admin: false })
    const wrapper = await mountTable()
    expect(wrapper.findAll('tbody tr')).toHaveLength(2)
    expect(wrapper.text()).not.toContain('Rotate')
  })

  it('offers rotation to an administrator', async () => {
    const wrapper = await mountTable()
    expect(wrapper.text()).toContain('Rotate')
  })

  it('sorts by a column when its header is clicked', async () => {
    const wrapper = await mountTable()
    await headerFor(wrapper, 'Category').trigger('click')
    expect(categoryOrder(wrapper)).toEqual(['draw', 'testing'])
    expect(wrapper.text()).toContain('Sorted by category, ascending')
  })

  it('reverses the sort on a second click', async () => {
    const wrapper = await mountTable()
    await headerFor(wrapper, 'Category').trigger('click')
    await headerFor(wrapper, 'Category').trigger('click')
    expect(categoryOrder(wrapper)).toEqual(['testing', 'draw'])
    expect(wrapper.text()).toContain('Sorted by category, descending')
  })

  it('returns to the urgency order on a third click', async () => {
    const wrapper = await mountTable()
    const header = headerFor(wrapper, 'Category')
    await header.trigger('click')
    await header.trigger('click')
    await header.trigger('click')
    expect(categoryOrder(wrapper)).toEqual(['testing', 'draw'])
    expect(wrapper.text()).toContain('Sorted by urgency')
  })

  it('switching columns starts a fresh ascending sort', async () => {
    const wrapper = await mountTable()
    await headerFor(wrapper, 'Category').trigger('click')
    await headerFor(wrapper, 'Category').trigger('click')
    await headerFor(wrapper, 'Wildcard').trigger('click')
    expect(categoryOrder(wrapper)).toEqual(['draw', 'testing'])
    expect(wrapper.text()).toContain('Sorted by wildcard, ascending')
  })

  it('marks the sorted column for assistive technology', async () => {
    const wrapper = await mountTable()
    expect(wrapper.findAll('thead th[aria-sort="none"]')).toHaveLength(6)
    await headerFor(wrapper, 'Category').trigger('click')
    expect(wrapper.findAll('thead th').at(0).attributes('aria-sort')).toBe('ascending')
  })

  it('tells an administrator when nothing is tracked', async () => {
    state.certificates = []
    const wrapper = await mountTable()
    expect(wrapper.findAll('tbody tr')).toHaveLength(0)
    expect(wrapper.text()).toContain('No certificates are being tracked yet')
  })

  it('shows a failed verification as check failed rather than never rotated', async () => {
    state.failVerify = true
    const wrapper = await mountTable()
    expect(wrapper.text()).toContain('check failed')
    expect(wrapper.text()).not.toContain('never rotated')
  })

  it('does not let a stale verification batch overwrite a newer one', async () => {
    state.deferVerify = true
    const wrapper = await mountTable()
    const staleBatch = state.pendingVerify.splice(0)
    expect(staleBatch).toHaveLength(2)

    const refresh = wrapper.findAll('button').find((button) => button.text() === 'Refresh')
    await refresh.trigger('click')
    await flushPromises()
    const freshBatch = state.pendingVerify.splice(0)
    expect(freshBatch).toHaveLength(2)

    freshBatch.forEach((call) => call.resolve({ data: { matches: true, expectedSerial: 'new' } }))
    await flushPromises()
    staleBatch.forEach((call) => call.resolve({ data: { matches: false, probeFailed: true } }))
    await flushPromises()

    expect(wrapper.text()).not.toContain('check failed')
    expect(wrapper.text()).toContain('serving')
  })
})
