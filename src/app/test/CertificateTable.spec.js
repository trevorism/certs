import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import CertificateTable from '../src/components/CertificateTable.vue'
import { redirectToLogin } from '../src/utils/auth.js'

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
  certificates: []
}))

vi.mock('../src/utils/auth.js', async (importOriginal) => ({
  ...(await importOriginal()),
  redirectToLogin: vi.fn()
}))

vi.mock('axios', () => ({
  default: {
    get: vi.fn((url) => {
      if (url === 'api/certificate') {
        return state.failList
          ? Promise.reject({ response: { status: state.failStatus } })
          : Promise.resolve({ data: state.certificates })
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
  document.cookie = 'user_name=tbrooks'
  document.cookie = `admin=${admin}`
}

function signOut() {
  document.cookie = 'user_name=; Max-Age=0'
  document.cookie = 'admin=; Max-Age=0'
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
    state.certificates = CERTIFICATES
    redirectToLogin.mockClear()
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
    expect(redirectToLogin).not.toHaveBeenCalled()
  })

  it('sends an expired session to login instead of showing the table', async () => {
    state.failList = true
    const wrapper = await mountTable()
    expect(redirectToLogin).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('Redirecting to sign in')
    expect(wrapper.find('.alert').exists()).toBe(false)
    expect(wrapper.findAll('tbody tr')).toHaveLength(0)
  })

  it('tells a rejected account it has no access rather than looping through login', async () => {
    state.failList = true
    state.failStatus = 403
    const wrapper = await mountTable()
    expect(redirectToLogin).not.toHaveBeenCalled()
    expect(wrapper.find('.alert').text()).toContain('does not have access')
  })

  it('sends a signed out visitor to login and never calls the api', async () => {
    signOut()
    const axios = (await import('axios')).default
    axios.get.mockClear()
    const wrapper = await mountTable()
    expect(redirectToLogin).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('Redirecting to sign in')
    expect(wrapper.findAll('tbody tr')).toHaveLength(0)
    expect(axios.get).not.toHaveBeenCalled()
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
})
