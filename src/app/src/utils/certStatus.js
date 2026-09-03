const MILLIS_PER_DAY = 86400000

export function daysRemaining(notAfter, now = Date.now()) {
  if (!notAfter) return null
  const expiry = Date.parse(notAfter)
  if (Number.isNaN(expiry)) return null
  return Math.floor((expiry - now) / MILLIS_PER_DAY)
}

export function expiryColor(days) {
  if (days === null) return 'secondary'
  if (days < 14) return 'danger'
  if (days < 30) return 'warning'
  return 'success'
}

export function expiryText(days) {
  if (days === null) return 'unknown'
  if (days < 0) return 'expired'
  return `${days} days`
}

export function edgeText(verification) {
  if (!verification) return 'checking'
  if (verification.matches) return 'serving'
  if (!verification.expectedSerial) return 'never rotated'
  return 'lagging'
}

export function edgeColor(verification) {
  if (!verification) return 'secondary'
  if (verification.matches) return 'success'
  if (!verification.expectedSerial) return 'secondary'
  return 'warning'
}

export function outcomeColor(outcome) {
  if (outcome === 'COMPLETED') return 'success'
  if (outcome === 'FAILED') return 'danger'
  if (outcome === 'SKIPPED') return 'info'
  return 'secondary'
}

export function sortByUrgency(certificates, now = Date.now()) {
  return [...certificates].sort((left, right) => {
    const a = daysRemaining(left.notAfter, now)
    const b = daysRemaining(right.notAfter, now)
    if (a === b) return left.category.localeCompare(right.category)
    if (a === null) return -1
    if (b === null) return 1
    return a - b
  })
}
