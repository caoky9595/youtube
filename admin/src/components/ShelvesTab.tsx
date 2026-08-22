import { useEffect, useState } from 'react'
import * as api from '../lib/api'
import type { Shelf, Video } from '../lib/types'
import { friendlyError } from '../lib/errors'
import { toast } from './Toast'
import { Button, Empty, Spinner } from './ui'
import { formatDuration } from '../lib/youtube'

type Props = { shelves: Shelf[]; onChanged: () => void }

export function ShelvesTab({ shelves, onChanged }: Props) {
  const [newTitle, setNewTitle] = useState('')
  const [openId, setOpenId] = useState<string | null>(null)

  async function run(fn: () => Promise<unknown>, okMsg: string) {
    try {
      await fn()
      toast(okMsg)
      onChanged()
    } catch (err) {
      toast(friendlyError(err), 'err')
    }
  }

  /** Doi cho shelf voi shelf ke ben roi ghi lai toan bo thu tu. */
  function moveShelf(index: number, delta: number) {
    const next = [...shelves]
    const target = index + delta
    if (target < 0 || target >= next.length) return
    ;[next[index], next[target]] = [next[target], next[index]]
    run(() => api.reorderShelves(next.map((s) => s.id)), 'Đã đổi thứ tự hàng')
  }

  return (
    <div className="flex flex-col gap-6">
      <form
        onSubmit={(e) => {
          e.preventDefault()
          const t = newTitle.trim()
          if (!t) return
          run(() => api.createShelf(t), `Đã tạo hàng “${t}”`)
          setNewTitle('')
        }}
        className="flex gap-2"
      >
        <input
          value={newTitle}
          onChange={(e) => setNewTitle(e.target.value)}
          placeholder="Tên hàng mới, ví dụ: Thiếu nhi, Nhạc, Hoạt hình…"
          className="flex-1 rounded-full border border-yt-border bg-yt-panel px-4 py-2.5 text-sm outline-none focus:border-zinc-500"
        />
        <Button type="submit" variant="primary" disabled={!newTitle.trim()}>
          Tạo hàng
        </Button>
      </form>

      {shelves.length === 0 ? (
        <Empty>
          Chưa có hàng nào. Trang chủ TV sẽ chỉ hiện hàng “Mới thêm”. Tạo hàng để nhóm video theo
          chủ đề.
        </Empty>
      ) : (
        <div className="flex flex-col gap-3">
          {shelves.map((shelf, i) => (
            <ShelfRow
              key={shelf.id}
              shelf={shelf}
              isFirst={i === 0}
              isLast={i === shelves.length - 1}
              open={openId === shelf.id}
              onToggle={() => setOpenId(openId === shelf.id ? null : shelf.id)}
              onMove={(d) => moveShelf(i, d)}
              onChanged={onChanged}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function ShelfRow({
  shelf,
  isFirst,
  isLast,
  open,
  onToggle,
  onMove,
  onChanged,
}: {
  shelf: Shelf
  isFirst: boolean
  isLast: boolean
  open: boolean
  onToggle: () => void
  onMove: (delta: number) => void
  onChanged: () => void
}) {
  const [videos, setVideos] = useState<Video[] | null>(null)
  const [editing, setEditing] = useState(false)
  const [title, setTitle] = useState(shelf.title)

  useEffect(() => {
    if (!open) return
    let alive = true
    api
      .getShelfVideos(shelf.id)
      .then((v) => alive && setVideos(v))
      .catch((e) => toast((e as Error).message, 'err'))
    return () => {
      alive = false
    }
  }, [open, shelf.id])

  async function run(fn: () => Promise<unknown>, okMsg: string, reloadLocal = false) {
    try {
      await fn()
      toast(okMsg)
      if (reloadLocal) setVideos(await api.getShelfVideos(shelf.id))
      onChanged()
    } catch (err) {
      toast(friendlyError(err), 'err')
    }
  }

  function moveVideo(index: number, delta: number) {
    if (!videos) return
    const next = [...videos]
    const target = index + delta
    if (target < 0 || target >= next.length) return
    ;[next[index], next[target]] = [next[target], next[index]]
    setVideos(next) // cap nhat lac quan cho muot
    run(() => api.reorderShelfVideos(shelf.id, next.map((v) => v.id)), 'Đã đổi thứ tự')
  }

  return (
    <div className="overflow-hidden rounded-xl border border-yt-border bg-yt-panel">
      <div className="flex items-center gap-2 px-4 py-3">
        <div className="flex flex-col">
          <button
            onClick={() => onMove(-1)}
            disabled={isFirst}
            title="Lên"
            className="text-xs leading-none text-yt-dim hover:text-white disabled:opacity-25"
          >
            ▲
          </button>
          <button
            onClick={() => onMove(1)}
            disabled={isLast}
            title="Xuống"
            className="text-xs leading-none text-yt-dim hover:text-white disabled:opacity-25"
          >
            ▼
          </button>
        </div>

        {editing ? (
          <form
            onSubmit={(e) => {
              e.preventDefault()
              const t = title.trim()
              if (!t || t === shelf.title) return setEditing(false)
              run(() => api.updateShelf(shelf.id, { title: t }), 'Đã đổi tên')
              setEditing(false)
            }}
            className="flex flex-1 gap-2"
          >
            <input
              autoFocus
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              onBlur={() => setEditing(false)}
              className="flex-1 rounded-lg border border-yt-border bg-yt-bg px-2 py-1 text-sm outline-none focus:border-zinc-500"
            />
          </form>
        ) : (
          <button onClick={onToggle} className="flex flex-1 items-center gap-2 text-left">
            <span className="font-medium">{shelf.title}</span>
            {!shelf.is_visible && (
              <span className="rounded bg-yt-hover px-1.5 py-0.5 text-xs text-yt-dim">Đang ẩn</span>
            )}
            <span className="text-xs text-yt-dim">{open ? '▲' : '▼'}</span>
          </button>
        )}

        <Button size="sm" onClick={() => setEditing(true)}>
          Đổi tên
        </Button>
        <Button
          size="sm"
          onClick={() =>
            run(
              () => api.updateShelf(shelf.id, { is_visible: !shelf.is_visible }),
              shelf.is_visible ? 'Đã ẩn hàng khỏi TV' : 'Đã hiện lại hàng',
            )
          }
        >
          {shelf.is_visible ? 'Ẩn' : 'Hiện'}
        </Button>
        <Button
          size="sm"
          variant="danger"
          onClick={() => {
            if (!confirm(`Xoá hàng “${shelf.title}”? Video vẫn còn trong kho.`)) return
            run(() => api.deleteShelf(shelf.id), 'Đã xoá hàng')
          }}
        >
          Xoá
        </Button>
      </div>

      {open && (
        <div className="border-t border-yt-border px-4 py-3">
          {videos === null ? (
            <Spinner />
          ) : videos.length === 0 ? (
            <p className="py-4 text-sm text-yt-dim">
              Hàng này chưa có video. Hàng rỗng sẽ không hiện trên TV.
            </p>
          ) : (
            <ol className="flex flex-col">
              {videos.map((v, i) => (
                <li
                  key={v.id}
                  className="flex items-center gap-3 border-b border-yt-border/60 py-2 last:border-0"
                >
                  <span className="w-6 text-right text-xs tabular-nums text-yt-dim">{i + 1}</span>
                  <div className="flex flex-col">
                    <button
                      onClick={() => moveVideo(i, -1)}
                      disabled={i === 0}
                      className="text-xs leading-none text-yt-dim hover:text-white disabled:opacity-25"
                    >
                      ▲
                    </button>
                    <button
                      onClick={() => moveVideo(i, 1)}
                      disabled={i === videos.length - 1}
                      className="text-xs leading-none text-yt-dim hover:text-white disabled:opacity-25"
                    >
                      ▼
                    </button>
                  </div>
                  <img
                    src={v.thumbnail_url ?? `https://i.ytimg.com/vi/${v.youtube_id}/default.jpg`}
                    alt=""
                    className="h-9 w-16 shrink-0 rounded object-cover"
                  />
                  <span className="flex-1 truncate text-sm" title={v.title}>
                    {v.title}
                  </span>
                  <span className="text-xs tabular-nums text-yt-dim">
                    {formatDuration(v.duration_seconds)}
                  </span>
                  <Button
                    size="sm"
                    onClick={() =>
                      run(
                        () => api.removeFromShelf(shelf.id, v.id),
                        'Đã bỏ khỏi hàng (video vẫn trong kho)',
                        true,
                      )
                    }
                  >
                    Bỏ khỏi hàng
                  </Button>
                </li>
              ))}
            </ol>
          )}
        </div>
      )}
    </div>
  )
}
