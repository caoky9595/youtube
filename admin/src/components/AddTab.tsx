import { useEffect, useRef, useState } from 'react'
import * as api from '../lib/api'
import type { Shelf, YtResult } from '../lib/types'
import { fetchMetadata, parseVideoId, youtubeSearchUrl } from '../lib/youtube'
import { friendlyError } from '../lib/errors'
import { toast } from './Toast'
import { VideoCard } from './VideoCard'
import { Button, ShelfPicker } from './ui'

type Props = {
  shelves: Shelf[]
  onChanged: () => void
  /** Link do he thong chia se (Share tu app YouTube) day sang. */
  sharedUrl?: string | null
  onSharedConsumed?: () => void
}

/** Ten hang tu tao khi kho chua co hang nao. */
const DEFAULT_SHELF = 'Video của tôi'

export function AddTab({ shelves, onChanged, sharedUrl, onSharedConsumed }: Props) {
  const [shelfId, setShelfId] = useState<string | null>(shelves[0]?.id ?? null)
  const [newShelf, setNewShelf] = useState<string | null>(null)
  const [existing, setExisting] = useState<Set<string>>(new Set())

  const [input, setInput] = useState('')
  const [pasting, setPasting] = useState(false)
  const [fromShare, setFromShare] = useState(false)

  const [query, setQuery] = useState('')
  const [justAdded, setJustAdded] = useState<api.VideoWithShelves[] | null>(null)
  const pasteBox = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    api.getExistingYoutubeIds().then(setExisting).catch(() => {})
  }, [])

  // Hang mac dinh la hang dau tien. Video luon phai thuoc mot hang de no hien
  // duoi dung ten hang tren trang chu TV, chu khong chi nam trong "Mới thêm".
  useEffect(() => {
    setShelfId((cur) => (cur && shelves.some((s) => s.id === cur) ? cur : shelves[0]?.id ?? null))
  }, [shelves])

  // App YouTube tren Android chia se sang -> dien san vao o dan
  useEffect(() => {
    if (!sharedUrl) return
    setInput((cur) => (cur.includes(sharedUrl) ? cur : [cur, sharedUrl].filter(Boolean).join('\n')))
    setFromShare(true)
    pasteBox.current?.focus()
    onSharedConsumed?.()
  }, [sharedUrl, onSharedConsumed])

  const ids = [
    ...new Set(
      input
        .split(/[\s,]+/)
        .filter(Boolean)
        .map(parseVideoId)
        .filter((x): x is string => x !== null),
    ),
  ]
  const badLines = input.split(/[\s,]+/).filter(Boolean).length - ids.length
  const alreadyIn = ids.filter((id) => existing.has(id))

  async function commit(items: YtResult[], label: string) {
    // Kho chua co hang nao thi tao mot hang mac dinh, de video vua them hien
    // ngay duoi mot ten hang tu te tren trang chu TV.
    let target = shelfId
    let targetName = shelves.find((s) => s.id === shelfId)?.title
    let created = false
    if (!target) {
      const shelf = await api.createShelf(DEFAULT_SHELF)
      target = shelf.id
      targetName = shelf.title
      created = true
      setShelfId(shelf.id)
    }

    const added: api.VideoWithShelves[] = []
    for (const item of items) {
      const row = await api.addVideo(item, target)
      added.push({ ...row, shelfIds: [target] })
    }
    setExisting((s) => new Set([...s, ...items.map((i) => i.youtubeId)]))
    setJustAdded((prev) => [...added, ...(prev ?? [])].slice(0, 12))
    toast(
      `${label} vào ${targetName}` + (created ? ` (đã tạo hàng “${targetName}”)` : ''),
    )
    onChanged()
  }

  async function createAndSelect(title: string) {
    try {
      const shelf = await api.createShelf(title)
      setShelfId(shelf.id)
      setNewShelf(null)
      toast(`Đã tạo hàng “${shelf.title}”`)
      onChanged()
    } catch (err) {
      toast(friendlyError(err), 'err')
    }
  }

  /* ------------------------------ dan link ------------------------------ */

  async function pasteFromClipboard() {
    try {
      const text = await navigator.clipboard.readText()
      if (!text.trim()) {
        toast('Clipboard đang trống.', 'err')
        return
      }
      setInput((cur) => [cur, text.trim()].filter(Boolean).join('\n'))
      pasteBox.current?.focus()
    } catch {
      // iOS/Safari co the tu choi neu khong phai thao tac nguoi dung, hoac
      // nguoi dung khong cho quyen
      toast('Trình duyệt không cho đọc clipboard — dán tay vào ô bên dưới nhé.', 'err')
      pasteBox.current?.focus()
    }
  }

  async function submitUrls(e: React.FormEvent) {
    e.preventDefault()
    if (ids.length === 0) {
      toast('Không tìm thấy link YouTube hợp lệ nào.', 'err')
      return
    }
    setPasting(true)
    try {
      // Metadata qua oEmbed cong khai — khong can khoa API
      const { items, failed } = await fetchMetadata(ids)
      if (failed.length) {
        toast(
          `${failed.length} link không lấy được thông tin — video có thể đã bị xoá, hoặc chủ ` +
            `kênh tắt cho phép nhúng nên TV cũng không phát được. Đã bỏ qua.`,
          'err',
        )
      }
      if (items.length === 0) return
      await commit(
        items,
        `Đã thêm ${items.length} video${badLines > 0 ? ` (bỏ qua ${badLines} dòng không hợp lệ)` : ''}`,
      )
      setInput('')
      setFromShare(false)
    } catch (err) {
      toast(friendlyError(err), 'err')
    } finally {
      setPasting(false)
    }
  }

  return (
    <div className="flex flex-col gap-8">
      <div className="flex max-w-4xl flex-wrap items-center gap-3 rounded-xl bg-yt-panel px-4 py-3">
        <span className="text-sm text-yt-dim">Thêm vào hàng:</span>

        {newShelf === null ? (
          <>
            {shelves.length > 0 ? (
              <ShelfPicker
                shelves={shelves}
                value={shelfId}
                onChange={setShelfId}
                allowNone={false}
              />
            ) : (
              <span className="text-sm text-yt-text">
                {DEFAULT_SHELF}{' '}
                <span className="text-xs text-yt-dim">(sẽ tự tạo)</span>
              </span>
            )}
            <Button size="sm" onClick={() => setNewShelf('')}>
              + Hàng mới
            </Button>
          </>
        ) : (
          <form
            onSubmit={(e) => {
              e.preventDefault()
              if (newShelf.trim()) createAndSelect(newShelf.trim())
            }}
            className="flex flex-wrap items-center gap-2"
          >
            <input
              autoFocus
              value={newShelf}
              onChange={(e) => setNewShelf(e.target.value)}
              placeholder="Tên hàng, ví dụ: Thiếu nhi"
              className="rounded-full border border-yt-border bg-yt-bg px-3 py-1.5 text-sm outline-none focus:border-zinc-500"
            />
            <Button type="submit" size="sm" variant="primary" disabled={!newShelf.trim()}>
              Tạo
            </Button>
            <Button type="button" size="sm" onClick={() => setNewShelf(null)}>
              Huỷ
            </Button>
          </form>
        )}

        <span className="text-xs text-yt-dim">
          Hàng này hiện trên trang chủ TV. Video mới cũng luôn có trong “Mới thêm”.
        </span>
      </div>

      {/* ---------------------- dan link / nhan Share ---------------------- */}
      <section className="max-w-4xl">
        <h2 className="mb-1 text-base font-semibold">Dán đường dẫn</h2>
        <p className="mb-3 text-sm leading-relaxed text-yt-dim">
          Mỗi dòng một link. Nhận cả <code className="text-zinc-400">youtube.com/watch</code>,{' '}
          <code className="text-zinc-400">youtu.be</code>,{' '}
          <code className="text-zinc-400">/shorts</code> và ID 11 ký tự. Trên điện thoại: trong app
          YouTube bấm <span className="text-zinc-400">Chia sẻ</span> rồi chọn trang này, hoặc{' '}
          <span className="text-zinc-400">Sao chép liên kết</span> rồi bấm nút dán bên dưới.
        </p>

        {fromShare && (
          <p className="mb-3 rounded-lg bg-emerald-600/15 px-3 py-2 text-sm text-emerald-300">
            Đã nhận link chia sẻ từ YouTube — bấm Thêm để lưu vào kho.
          </p>
        )}

        <form onSubmit={submitUrls} className="flex flex-col gap-3">
          <textarea
            ref={pasteBox}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            rows={4}
            placeholder={'https://www.youtube.com/watch?v=…\nhttps://youtu.be/…'}
            className="w-full resize-y rounded-xl border border-yt-border bg-yt-panel px-4 py-3 font-mono text-sm leading-relaxed outline-none focus:border-zinc-500"
          />
          <div className="flex flex-wrap items-center gap-3">
            <Button type="submit" variant="primary" disabled={pasting || ids.length === 0}>
              {pasting ? 'Đang thêm…' : ids.length > 1 ? `Thêm ${ids.length} video` : 'Thêm'}
            </Button>
            <Button type="button" onClick={pasteFromClipboard}>
              Dán từ clipboard
            </Button>
            {input.trim() && (
              <Button type="button" onClick={() => { setInput(''); setFromShare(false) }}>
                Xoá ô
              </Button>
            )}
            {ids.length > 0 && (
              <span className="text-xs text-yt-dim">
                Nhận ra {ids.length} link
                {badLines > 0 && ` · ${badLines} dòng không hợp lệ`}
                {alreadyIn.length > 0 && ` · ${alreadyIn.length} đã có (sẽ cập nhật lại)`}
              </span>
            )}
          </div>
        </form>
      </section>

      {/* --------------------- tim tren YouTube (tab moi) ------------------ */}
      <section className="max-w-4xl">
        <h2 className="mb-1 text-base font-semibold">Tìm trên YouTube</h2>
        <p className="mb-3 text-sm leading-relaxed text-yt-dim">
          Mở YouTube ở tab mới để chọn video, rồi copy đường dẫn và dán vào ô trên. Trang này
          không đọc được kết quả tìm kiếm của YouTube — youtube.com không cho đọc từ trang khác,
          cũng không cho nhúng vào iframe.
        </p>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            if (!query.trim()) return
            window.open(youtubeSearchUrl(query), '_blank', 'noopener,noreferrer')
          }}
          className="flex max-w-2xl flex-wrap gap-2"
        >
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Tên video, tên kênh…"
            className="min-w-56 flex-1 rounded-full border border-yt-border bg-yt-panel px-4 py-2.5 text-sm outline-none focus:border-zinc-500"
          />
          <Button type="submit" disabled={!query.trim()}>
            Mở YouTube ↗
          </Button>
        </form>
      </section>

      {/* ------------------------------ vua them --------------------------- */}
      {justAdded && justAdded.length > 0 && (
        <section className="border-t border-yt-border pt-6">
          <h3 className="mb-4 text-sm font-medium text-yt-dim">Vừa thêm</h3>
          <div className="grid grid-cols-[repeat(auto-fill,minmax(200px,1fr))] gap-x-4 gap-y-6">
            {justAdded.map((v) => (
              <VideoCard
                key={v.id}
                youtubeId={v.youtube_id}
                title={v.title}
                channelTitle={v.channel_title}
                thumbnailUrl={v.thumbnail_url}
                durationSeconds={v.duration_seconds}
              />
            ))}
          </div>
          <p className="mt-4 text-xs leading-relaxed text-yt-dim">
            Video thêm bằng đường dẫn chưa có thời lượng là bình thường — app TV tự điền sau lần
            phát đầu tiên.
          </p>
        </section>
      )}
    </div>
  )
}
