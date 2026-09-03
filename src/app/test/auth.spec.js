import { describe, it, expect, beforeEach } from 'vitest'
import {
  getCookieValue,
  getCurrentUserName,
  isLoggedIn,
  looksLikeAdministrator,
  loginUrlFor
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
})
