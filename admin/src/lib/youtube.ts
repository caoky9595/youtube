import type { YtResult } from './types'

/**
 * Rut youtube_id tu moi dang duong dan nguoi dung co the dan vao:
 *   https://www.youtube.com/watch?v=ID&list=...
 *   https://youtu.be/ID?t=30
 *   https://www.youtube.com/shorts/ID
 *   https://www.youtube.com/embed/ID
 *   https://m.youtube.com/watch?v=ID
 *   ID (11 ky tu)
 * Tra ve null neu khong nhan ra.
 */
export function parseVideoId(input: string): string | null {
  const raw = input.trim()
  if (!raw) return null

  // Da la ID tran
  if (/^[A-Za-z0-9_-]{11}$/.test(raw)) return raw

  let u: URL
  try {
    u = new URL(raw.startsWith('http') ? raw : `https://${raw}`)
  } catch {
    return null
  }

  const host = u.hostname.replace(/^www\.|^m\./, '')

  if (host === 'youtu.be') {
    const id = u.pathname.slice(1).split('/')[0]
    return /^[A-Za-z0-9_-]{11}$/.test(id) ? id : null
  }

  if (host === 'youtube.com' || host === 'youtube-nocookie.com') {
    const v = u.searchParams.get('v')
    if (v && /^[A-Za-z0-9_-]{11}$/.test(v)) return v

    const m = u.pathname.match(/^\/(?:shorts|embed|live|v)\/([A-Za-z0-9_-]{11})/)
    if (m) return m[1]
  }

  return null
}

/**
 * Lay metadata cua mot video qua endpoint oEmbed cong khai cua YouTube.
 * Khong can khoa API, va co CORS nen goi thang tu trinh duyet duoc.
 *
 * oEmbed khong tra ve thoi luong. App TV se tu bao thoi luong ve server lan dau
 * phat video do (xem report_duration trong supabase/03_rpc.sql), nen the video
 * co badge thoi luong tu lan xem thu hai.
 *
 * Tra ve null khi YouTube tu choi (thuong la 400): video khong ton tai, da bi
 * xoa, hoac chu kenh tat cho phep nhung — nghia la them vao cung khong phat
 * duoc, nen bao ngay con hon.
 */
export async function fetchViaOembed(id: string): Promise<YtResult | null> {
  const target = encodeURIComponent(`https://www.youtube.com/watch?v=${id}`)
  const res = await fetch(`https://www.youtube.com/oembed?url=${target}&format=json`)
  if (!res.ok) return null

  const data = (await res.json()) as {
    title?: string
    author_name?: string
    thumbnail_url?: string
  }
  return {
    youtubeId: id,
    title: data.title ?? id,
    channelTitle: data.author_name ?? '',
    thumbnailUrl: data.thumbnail_url ?? `https://i.ytimg.com/vi/${id}/hqdefault.jpg`,
  }
}

/** Metadata cho mot loat ID, chay song song. Tach rieng nhung ID bi tu choi. */
export async function fetchMetadata(
  ids: string[],
): Promise<{ items: YtResult[]; failed: string[] }> {
  if (ids.length === 0) return { items: [], failed: [] }

  const results = await Promise.all(ids.map((id) => fetchViaOembed(id).catch(() => null)))
  return {
    items: results.filter((r): r is YtResult => r !== null),
    failed: ids.filter((_, i) => results[i] === null),
  }
}

export function formatDuration(seconds: number | null | undefined): string {
  if (seconds == null) return ''
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`
}

/**
 * Dia chi trang ket qua tim kiem cua YouTube.
 *
 * Trang admin khong the tu doc ket qua tim kiem: youtube.com khong cho doc
 * cross-origin (khong co CORS), khong cho nhung vao iframe
 * (x-frame-options: SAMEORIGIN), va popup thi same-origin policy chan doc DOM.
 * Muon co luoi ket qua ngay trong trang thi buoc phai co khoa API cua YouTube.
 *
 * Nen o day chi mo YouTube ra tab moi: nguoi dung chon video, copy duong dan,
 * roi dan lai vao o them. Khong can khoa nao.
 */
export function youtubeSearchUrl(query: string): string {
  return `https://www.youtube.com/results?search_query=${encodeURIComponent(query.trim())}`
}
