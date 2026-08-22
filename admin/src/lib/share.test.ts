import { describe, expect, it } from 'vitest'
import { readSharedUrl } from './share'

const ID = 'dQw4w9WgXcQ'
const LINK = `https://youtu.be/${ID}`

describe('readSharedUrl', () => {
  it('đọc từ tham số url', () => {
    expect(readSharedUrl(`?url=${encodeURIComponent(LINK)}`)).toBe(LINK)
  })

  // App YouTube tren Android thuong nhet ca tieu de lan link vao "text"
  it('bóc link ra khỏi text kèm tiêu đề', () => {
    const text = encodeURIComponent(`Bài Hát Thiếu Nhi\n${LINK}`)
    expect(readSharedUrl(`?text=${text}`)).toBe(LINK)
  })

  it('đọc từ title nếu link nằm ở đó', () => {
    expect(readSharedUrl(`?title=${encodeURIComponent(LINK)}`)).toBe(LINK)
  })

  it('ưu tiên url hơn text', () => {
    const other = 'https://www.youtube.com/watch?v=9bZkp7q19f0'
    const qs = `?url=${encodeURIComponent(LINK)}&text=${encodeURIComponent(other)}`
    expect(readSharedUrl(qs)).toBe(LINK)
  })

  it('nhận link watch có kèm timestamp và playlist', () => {
    const messy = `https://www.youtube.com/watch?v=${ID}&list=PLxx&t=42`
    expect(readSharedUrl(`?text=${encodeURIComponent(messy)}`)).toBe(messy)
  })

  it.each([
    ['không có tham số', ''],
    ['tham số rỗng', '?url=&text='],
    ['chia sẻ văn bản thường', '?text=xin%20chao'],
    ['link không phải YouTube', '?url=https%3A%2F%2Fvimeo.com%2F12345678'],
  ])('trả về null với %s', (_label, qs) => {
    expect(readSharedUrl(qs)).toBeNull()
  })
})
