import { describe, expect, it } from 'vitest'
import { friendlyError } from './errors'

describe('friendlyError', () => {
  // .env khi chay test dang tro vao IP LAN, khong phai localhost
  it.each([
    ['Load failed'],            // Safari
    ['Failed to fetch'],        // Chrome
    ['NetworkError when attempting to fetch resource.'], // Firefox
    ['Network request failed'],
  ])('giải thích lỗi mạng: %s', (raw) => {
    const out = friendlyError(new Error(raw))
    expect(out).not.toBe(raw)
    expect(out).toContain(import.meta.env.VITE_SUPABASE_URL)
  })

  it('giữ nguyên lỗi từ server', () => {
    expect(friendlyError(new Error('Mã đã hết hạn, bấm tạo mã mới trên TV'))).toBe(
      'Mã đã hết hạn, bấm tạo mã mới trên TV',
    )
  })

  it('nhận cả giá trị không phải Error', () => {
    expect(friendlyError('Mã không đúng')).toBe('Mã không đúng')
  })
})
