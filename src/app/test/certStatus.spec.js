import { describe, it, expect } from 'vitest'
import {
  daysRemaining,
  expiryColor,
  expiryText,
  edgeColor,
  edgeSeverity,
  edgeText,
  outcomeColor,
  sortByUrgency,
  sortCertificates
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

  it('reports a failed probe as its own state rather than as never rotated', () => {
    const verification = { matches: false, probeFailed: true, expectedSerial: 'abc' }
    expect(edgeText(verification)).toBe('check failed')
    expect(edgeColor(verification)).toBe('danger')
  })

  it('does not claim never rotated when the probe failed before a serial was known', () => {
    expect(edgeText({ matches: false, probeFailed: true })).toBe('check failed')
  })

  it('sorts an unverifiable edge above a merely lagging one', () => {
    expect(edgeSeverity({ matches: false, probeFailed: true })).toBeGreaterThan(
      edgeSeverity({ matches: false, expectedSerial: 'abc' })
    )
    expect(edgeSeverity({ matches: true, expectedSerial: 'abc' })).toBe(0)
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

  it('survives a certificate with no category', () => {
    const sorted = sortByUrgency([{ notAfter: null }, { category: 'testing', notAfter: null }], NOW)
    expect(sorted).toHaveLength(2)
  })
})

describe('sortCertificates', () => {
  const certificates = [
    {
      id: '1',
      category: 'trade',
      wildcard: '*.trade.trevorism.com',
      gcpProject: 'trevorism-trade',
      notAfter: '2026-12-01T00:00:00Z',
      lastOutcome: 'FAILED'
    },
    {
      id: '2',
      category: 'draw',
      wildcard: '*.draw.trevorism.com',
      gcpProject: 'trevorism-draw',
      notAfter: '2026-09-25T00:00:00Z',
      lastOutcome: 'COMPLETED'
    },
    {
      id: '3',
      category: 'action',
      wildcard: '*.action.trevorism.com',
      gcpProject: 'trevorism-action',
      notAfter: null
    }
  ]

  const verifications = {
    '1': { matches: false, expectedSerial: 'abc' },
    '2': { matches: true, expectedSerial: 'abc' }
  }

  function categories(options) {
    return sortCertificates(certificates, { now: NOW, verifications, ...options }).map(
      (c) => c.category
    )
  }

  it('falls back to urgency when no column is chosen', () => {
    expect(categories({ key: null })).toEqual(['action', 'draw', 'trade'])
  })

  it('sorts text columns alphabetically and reverses on descending', () => {
    expect(categories({ key: 'category' })).toEqual(['action', 'draw', 'trade'])
    expect(categories({ key: 'category', direction: 'desc' })).toEqual(['trade', 'draw', 'action'])
    expect(categories({ key: 'gcpProject', direction: 'desc' })).toEqual([
      'trade',
      'draw',
      'action'
    ])
  })

  it('sorts expiry by days remaining and keeps unknown expiry most urgent', () => {
    expect(categories({ key: 'expiry' })).toEqual(['action', 'draw', 'trade'])
    expect(categories({ key: 'expiry', direction: 'desc' })).toEqual(['trade', 'draw', 'action'])
  })

  it('sorts the edge column from healthy to lagging', () => {
    expect(categories({ key: 'edge' })).toEqual(['draw', 'action', 'trade'])
    expect(categories({ key: 'edge', direction: 'desc' })).toEqual(['trade', 'action', 'draw'])
  })

  it('sorts last rotation with failures last ascending', () => {
    expect(categories({ key: 'outcome' })).toEqual(['draw', 'action', 'trade'])
  })

  it('breaks ties on category', () => {
    const tied = [
      { id: 'a', category: 'trade', gcpProject: 'shared', notAfter: null },
      { id: 'b', category: 'draw', gcpProject: 'shared', notAfter: null }
    ]
    const sorted = sortCertificates(tied, { key: 'gcpProject', now: NOW })
    expect(sorted.map((c) => c.category)).toEqual(['draw', 'trade'])
  })

  it('does not mutate the input', () => {
    const input = [...certificates]
    sortCertificates(input, { key: 'category', direction: 'desc', now: NOW })
    expect(input.map((c) => c.category)).toEqual(['trade', 'draw', 'action'])
  })
})
