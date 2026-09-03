import { describe, it, expect } from 'vitest'
import {
  daysRemaining,
  expiryColor,
  expiryText,
  edgeColor,
  edgeText,
  outcomeColor,
  sortByUrgency
} from '../src/utils/certStatus.js'

const NOW = Date.parse('2026-09-02T00:00:00Z')

describe('daysRemaining', () => {
  it('counts whole days until expiry', () => {
    expect(daysRemaining('2026-09-12T00:00:00Z', NOW)).toBe(10)
  })

  it('goes negative once expired', () => {
    expect(daysRemaining('2026-09-01T00:00:00Z', NOW)).toBe(-1)
  })

  it('is null when there is no expiry', () => {
    expect(daysRemaining(null, NOW)).toBeNull()
    expect(daysRemaining(undefined, NOW)).toBeNull()
  })

  it('is null when the expiry cannot be parsed', () => {
    expect(daysRemaining('not-a-date', NOW)).toBeNull()
  })
})

describe('expiryColor', () => {
  it('is danger inside two weeks', () => {
    expect(expiryColor(13)).toBe('danger')
    expect(expiryColor(0)).toBe('danger')
    expect(expiryColor(-5)).toBe('danger')
  })

  it('is warning inside the renewal window', () => {
    expect(expiryColor(14)).toBe('warning')
    expect(expiryColor(29)).toBe('warning')
  })

  it('is success outside the renewal window', () => {
    expect(expiryColor(30)).toBe('success')
    expect(expiryColor(90)).toBe('success')
  })

  it('is secondary when unknown', () => {
    expect(expiryColor(null)).toBe('secondary')
  })
})

describe('expiryText', () => {
  it('reads as expired once past', () => {
    expect(expiryText(-1)).toBe('expired')
  })

  it('reads as days otherwise', () => {
    expect(expiryText(23)).toBe('23 days')
  })

  it('reads as unknown without an expiry', () => {
    expect(expiryText(null)).toBe('unknown')
  })
})

describe('edge status', () => {
  it('is checking until the verification arrives', () => {
    expect(edgeText(undefined)).toBe('checking')
    expect(edgeColor(undefined)).toBe('secondary')
  })

  it('is serving when the edge matches', () => {
    const verification = { matches: true, expectedSerial: 'abc' }
    expect(edgeText(verification)).toBe('serving')
    expect(edgeColor(verification)).toBe('success')
  })

  it('distinguishes never rotated from lagging', () => {
    expect(edgeText({ matches: false })).toBe('never rotated')
    expect(edgeColor({ matches: false })).toBe('secondary')
    expect(edgeText({ matches: false, expectedSerial: 'abc' })).toBe('lagging')
    expect(edgeColor({ matches: false, expectedSerial: 'abc' })).toBe('warning')
  })
})

describe('outcomeColor', () => {
  it('maps the rotation outcomes', () => {
    expect(outcomeColor('COMPLETED')).toBe('success')
    expect(outcomeColor('FAILED')).toBe('danger')
    expect(outcomeColor('SKIPPED')).toBe('info')
    expect(outcomeColor(undefined)).toBe('secondary')
  })
})

describe('sortByUrgency', () => {
  it('puts the soonest expiry first', () => {
    const sorted = sortByUrgency(
      [
        { category: 'draw', notAfter: '2026-12-01T00:00:00Z' },
        { category: 'trade', notAfter: '2026-09-25T00:00:00Z' }
      ],
      NOW
    )
    expect(sorted.map((c) => c.category)).toEqual(['trade', 'draw'])
  })

  it('puts never-rotated certificates first', () => {
    const sorted = sortByUrgency(
      [
        { category: 'draw', notAfter: '2026-12-01T00:00:00Z' },
        { category: 'testing', notAfter: null }
      ],
      NOW
    )
    expect(sorted.map((c) => c.category)).toEqual(['testing', 'draw'])
  })

  it('does not mutate the input', () => {
    const input = [
      { category: 'draw', notAfter: '2026-12-01T00:00:00Z' },
      { category: 'trade', notAfter: '2026-09-25T00:00:00Z' }
    ]
    sortByUrgency(input, NOW)
    expect(input.map((c) => c.category)).toEqual(['draw', 'trade'])
  })
})
