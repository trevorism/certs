import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import CertificateTable from '../src/components/CertificateTable.vue'

const state = vi.hoisted(() => ({
  failList: false,
  certificates: [
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
}))

vi.mock('axios', () => ({
  default: {
    get: vi.fn((url) => {
      if (url === 'api/certificate') {
        return state.failList
          ? Promise.reject(new Error('unauthorized'))
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

describe('CertificateTable', () => {
  beforeEach(() => {
    state.failList = false
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
    const wrapper = await mountTable()
    expect(wrapper.find('.alert').text()).toContain('Unable to load certificates')
    expect(wrapper.findAll('tbody tr')).toHaveLength(0)
  })

  it('asks a signed out visitor to log in and never calls the api', async () => {
    signOut()
    const axios = (await import('axios')).default
    axios.get.mockClear()
    const wrapper = await mountTable()
    expect(wrapper.text()).toContain('Please log in')
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
})
