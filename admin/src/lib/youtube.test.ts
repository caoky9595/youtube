import { afterEach, describe, expect, it } from 'vitest'
import { fetchMetadata, fetchViaOembed, formatDuration, parseVideoId } from './youtube'

describe('parseVideoId', () => {
  const ID = 'dQw4w9WgXcQ'
  it.each([
    ['ID tran', ID],
    ['watch', `https://www.youtube.com/watch?v=${ID}`],
    ['watch + playlist', `https://www.youtube.com/watch?v=${ID}&list=PLxx&index=2`],
    ['khong co www', `https://youtube.com/watch?v=${ID}`],
    ['mobile', `https://m.youtube.com/watch?v=${ID}`],
    ['youtu.be', `https://youtu.be/${ID}`],
    ['youtu.be + timestamp', `https://youtu.be/${ID}?t=42`],
    ['shorts', `https://www.youtube.com/shorts/${ID}`],
    ['embed', `https://www.youtube.com/embed/${ID}`],
    ['live', `https://www.youtube.com/live/${ID}`],
    ['nocookie', `https://www.youtube-nocookie.com/embed/${ID}`],
    ['thieu scheme', `youtube.com/watch?v=${ID}`],
    ['co khoang trang', `  https://youtu.be/${ID}  `],
  ])('nhan ra %s', (_label, input) => {
    expect(parseVideoId(input)).toBe(ID)
  })

  it.each([
    ['rong', ''],
    ['chi khoang trang', '   '],
    ['van ban thuong', 'xin chao'],
    ['ID qua ngan', 'abc'],
    ['host khac', 'https://vimeo.com/12345678'],
    ['youtube nhung khong co video', 'https://www.youtube.com/feed/subscriptions'],
    ['kenh', 'https://www.youtube.com/@sometchannel'],
    ['v= sai do dai', 'https://www.youtube.com/watch?v=tooshort'],
  ])('tra ve null voi %s', (_label, input) => {
    expect(parseVideoId(input)).toBeNull()
  })
})

describe('formatDuration', () => {
  it.each([
    [213, '3:33'],
    [3723, '1:02:03'],
    [59, '0:59'],
    [60, '1:00'],
    [3600, '1:00:00'],
  ])('%i -> %s', (secs, want) => {
    expect(formatDuration(secs)).toBe(want)
  })

  it('tra ve rong khi khong biet thoi luong', () => {
    expect(formatDuration(null)).toBe('')
  })
})

describe('fetchViaOembed', () => {
  const ID = 'dQw4w9WgXcQ'
  const original = globalThis.fetch

  afterEach(() => {
    globalThis.fetch = original
  })

  function mockFetch(response: { ok: boolean; body?: unknown }) {
    globalThis.fetch = (async () => ({
      ok: response.ok,
      json: async () => response.body,
    })) as unknown as typeof fetch
  }

  it('lấy được tiêu đề, kênh và thumbnail mà không cần khoá API', async () => {
    mockFetch({
      ok: true,
      body: {
        title: 'Bài Hát Thiếu Nhi',
        author_name: 'Kênh Thiếu Nhi',
        thumbnail_url: 'https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg',
      },
    })
    const got = await fetchViaOembed(ID)
    expect(got).toEqual({
      youtubeId: ID,
      title: 'Bài Hát Thiếu Nhi',
      channelTitle: 'Kênh Thiếu Nhi',
      thumbnailUrl: 'https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg',
    })
  })

  it('suy thumbnail từ ID nếu oEmbed không trả về', async () => {
    mockFetch({ ok: true, body: { title: 'X', author_name: 'Y' } })
    const got = await fetchViaOembed(ID)
    expect(got?.thumbnailUrl).toBe(`https://i.ytimg.com/vi/${ID}/hqdefault.jpg`)
  })

  it('dùng ID làm tiêu đề nếu oEmbed thiếu title', async () => {
    mockFetch({ ok: true, body: {} })
    expect((await fetchViaOembed(ID))?.title).toBe(ID)
  })

  // YouTube tra 400 khi video khong ton tai hoac chu kenh tat cho phep nhung
  it('trả về null khi YouTube từ chối', async () => {
    mockFetch({ ok: false })
    expect(await fetchViaOembed(ID)).toBeNull()
  })
})

describe('fetchMetadata', () => {
  const original = globalThis.fetch
  afterEach(() => {
    globalThis.fetch = original
  })

  it('tách được video lấy được và video bị từ chối', async () => {
    globalThis.fetch = (async (url: string) => {
      const ok = !url.includes('bad')
      return { ok, json: async () => ({ title: 'T', author_name: 'C' }) }
    }) as unknown as typeof fetch

    const { items, failed } = await fetchMetadata(['goodID11111', 'badID111111'])
    expect(items.map((i) => i.youtubeId)).toEqual(['goodID11111'])
    expect(failed).toEqual(['badID111111'])
  })

  it('mảng rỗng thì không gọi mạng', async () => {
    globalThis.fetch = (() => {
      throw new Error('không được gọi')
    }) as unknown as typeof fetch
    expect(await fetchMetadata([])).toEqual({ items: [], failed: [] })
  })
})
