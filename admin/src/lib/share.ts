import { parseVideoId } from './youtube'

/**
 * Doc link video tu tham so chia se (Web Share Target).
 *
 * Android gui theo manifest: title / text / url. App YouTube thuong nhet link
 * vao "text" duoi dang "Tieu de\nhttps://youtu.be/ID", co luc lai dat o "url".
 * Nen phai do ca ba, va boc link ra khoi doan van ban.
 */
export function readSharedUrl(search: string): string | null {
  const params = new URLSearchParams(search)
  for (const key of ['url', 'text', 'title']) {
    const raw = params.get(key)
    if (!raw) continue

    const inText = raw.match(/https?:\/\/\S+/)?.[0]
    for (const candidate of [inText, raw].filter(Boolean) as string[]) {
      if (parseVideoId(candidate)) return candidate
    }
  }
  return null
}

/**
 * Xoa tham so chia se khoi thanh dia chi sau khi da dung.
 * Khong xoa thi tai lai trang la them video do lan nua.
 */
export function clearShareParams() {
  if (!window.location.search) return
  window.history.replaceState({}, '', window.location.pathname)
}
