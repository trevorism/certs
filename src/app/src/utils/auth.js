const LOGIN_URL = 'https://login.auth.trevorism.com'

export function getCookieValue(name) {
  const cookiePrefix = `${name}=`
  const cookies = document.cookie ? document.cookie.split('; ') : []
  for (const cookie of cookies) {
    if (cookie.startsWith(cookiePrefix)) {
      try {
        return decodeURIComponent(cookie.substring(cookiePrefix.length))
      } catch {
        return ''
      }
    }
  }
  return ''
}

export function getCurrentUserName() {
  return getCookieValue('user_name')
}

export function isLoggedIn() {
  return !!getCurrentUserName()?.trim()
}

export function looksLikeAdministrator() {
  return getCookieValue('admin')?.trim().toLowerCase() === 'true'
}

export function loginUrlFor(returnUrl) {
  return `${LOGIN_URL}?return_url=${encodeURIComponent(returnUrl)}`
}

let navigatingToLogin = false

export function redirectToLogin(returnUrl) {
  if (navigatingToLogin) return
  navigatingToLogin = true
  window.location.assign(loginUrlFor(returnUrl))
}

export function isUnauthorized(error) {
  return error?.response?.status === 401
}

export function isForbidden(error) {
  return error?.response?.status === 403
}
