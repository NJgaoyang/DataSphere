import { describe, expect, it } from 'vitest'
import { formatDateTime, formatPercent, statusLabel } from './display'

describe('display helpers', () => {
  it('formats platform timestamps consistently', () => {
    expect(formatDateTime('2026-09-19T17:25:30.123+08:00')).toBe('2026-09-19 17:25:30')
    expect(formatDateTime()).toBe('—')
  })

  it('formats percentages without noisy decimals', () => {
    expect(formatPercent(100)).toBe('100%')
    expect(formatPercent(98.26, 1)).toBe('98.3%')
    expect(formatPercent(null)).toBe('—')
  })

  it('normalizes runtime status labels', () => {
    expect(statusLabel('success')).toBe('成功')
    expect(statusLabel('CANCELED')).toBe('已停止')
    expect(statusLabel('custom_state')).toBe('CUSTOM_STATE')
  })
})
