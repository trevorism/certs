import { describe, it, expect, beforeEach, vi } from 'vitest'
import {
  getCookieValue,
  getCurrentUserName,
  isLoggedIn,
  looksLikeAdministrator,
  loginUrlFor,
  isUnauthorized,
  isForbidden
} from '../src/utils/auth.js'

function clearCookies() {
  document.cookie.split('; ').forEach((cookie) => {
    const name = cookie.split('=')[0]
    if (name) document.cookie = `${name}=; Max-Age=0`
  })
}

describe('auth', () => {
  beforeEach(clearCookies)

  it('reads a cookie value', () => {
    document.cookie = 'user_name=tbrooks'
    expect(getCookieValue('user_name')).toBe('tbrooks')
    expect(getCurrentUserName()).toBe('tbrooks')
  })

  it('decodes an encoded cookie value', () => {
    document.cookie = `user_name=${encodeURIComponent('trevor brooks')}`
    expect(getCurrentUserName()).toBe('trevor brooks')
  })

  it('returns empty for a cookie that is not set', () => {
    expect(getCookieValue('nothing_here')).toBe('')
  })

  it('is not logged in without a user name', () => {
    expect(isLoggedIn()).toBe(false)
  })

  it('is not logged in when the user name is only whitespace', () => {
    document.cookie = `user_name=${encodeURIComponent('   ')}`
    expect(isLoggedIn()).toBe(false)
  })

  it('is logged in with a user name', () => {
    document.cookie = 'user_name=tbrooks'
    expect(isLoggedIn()).toBe(true)
  })

  it('reads the administrator hint case insensitively', () => {
    document.cookie = 'admin=TRUE'
    expect(looksLikeAdministrator()).toBe(true)
  })

  it('is not an administrator when the hint is false or absent', () => {
    expect(looksLikeAdministrator()).toBe(false)
    document.cookie = 'admin=false'
    expect(looksLikeAdministrator()).toBe(false)
  })

  it('builds a login url that returns to the current page', () => {
    expect(loginUrlFor('https://certs.project.trevorism.com/')).toBe(
      'https://login.auth.trevorism.com?return_url=https%3A%2F%2Fcerts.project.trevorism.com%2F'
    )
  })

  it('recognizes an expired session but not a rejected role', () => {
    expect(isUnauthorized({ response: { status: 401 } })).toBe(true)
    expect(isUnauthorized({ response: { status: 403 } })).toBe(false)
    expect(isUnauthorized(new Error('network down'))).toBe(false)
  })

  it('recognizes a rejected role', () => {
    expect(isForbidden({ response: { status: 403 } })).toBe(true)
    expect(isForbidden({ response: { status: 500 } })).toBe(false)
    expect(isForbidden(undefined)).toBe(false)
  })
})

describe('redirectToLogin', () => {
  let assign

  beforeEach(() => {
    vi.resetModules()
    assign = vi.fn()
    Object.defineProperty(window, 'location', {
      value: { href: 'https://certs.project.trevorism.com/', assign },
      writable: true,
      configurable: true
    })
  })

  it('navigates to login with a return url', async () => {
    const { redirectToLogin } = await import('../src/utils/auth.js')
    redirectToLogin('https://certs.project.trevorism.com/')
    expect(assign).toHaveBeenCalledWith(
      'https://login.auth.trevorism.com?return_url=https%3A%2F%2Fcerts.project.trevorism.com%2F'
    )
  })

  it('navigates once even when several calls race', async () => {
    const { redirectToLogin } = await import('../src/utils/auth.js')
    redirectToLogin('https://certs.project.trevorism.com/')
    redirectToLogin('https://certs.project.trevorism.com/')
    expect(assign).toHaveBeenCalledTimes(1)
  })
})
