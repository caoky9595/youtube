import { useEffect, useState } from 'react'
import * as api from '../lib/api'
import { friendlyError } from '../lib/errors'
import type { Shelf, Video } from '../lib/types'
import { formatDuration } from '../lib/youtube'
import { SortableList, SortableRow } from './Sortable'
import { toast } from './Toast'
import { Button, Empty, Spinner } from './ui'

type Props = { shelves: Shelf[]; onChanged: () => void }

export function ShelvesTab({ shelves, onChanged }: Props) {
  const [newTitle, setNewTitle] = useState('')
  const [openId, setOpenId] = useState<string | null>(null)
  /** Thứ tự đang hiển thị, cập nhật ngay khi kéo cho mượt rồi mới ghi lên server. */
  const [order, setOrder] = useState<Shelf[]>(shelves)

  useEffect(() => setOrder(shelves), [shelves])

  async function run(fn: () => Promise<unknown>, okMsg: string) {
    try {
      await fn()
      toast(okMsg)
      onChanged()
    } catch (err) {
      toast(friendlyError(err), 'err')
    }
  }

  function reorder(ids: string[]) {
    // Cập nhật lạc quan để kéo xong là thấy ngay, không chờ mạng
    setOrder((cur) => ids.map((id) => cur.find((s) => s.id === id)!).filter(Boolean))
    run(() => api.reorderShelves(ids), 'Đã đổi thứ tự hàng')
  }

  return (
    <div className="flex max-w-4xl flex-col gap-6">
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

      {order.length === 0 ? (
        <Empty>
          Chưa có hàng nào. Trang chủ TV sẽ chỉ hiện hàng “Mới thêm”. Tạo hàng để nhóm video theo
          chủ đề.
        </Empty>
      ) : (
        <>
          <p className="text-xs text-yt-dim">
            Kéo <span className="text-zinc-400">⠿</span> để đổi thứ tự. Thứ tự này là thứ tự các
            hàng trên trang chủ TV.
          </p>
          <SortableList ids={order.map((s) => s.id)} onReorder={reorder}>
            <div className="flex flex-col gap-3">
              {order.map((shelf, i) => (
                <SortableRow key={shelf.id} id={shelf.id}>
                  {(handle) => (
                    <ShelfRow
                      shelf={shelf}
                      handle={handle}
                      isFirst={i === 0}
                      open={openId === shelf.id}
                      onToggle={() => setOpenId(openId === shelf.id ? null : shelf.id)}
                      onMoveTop={() =>
                        reorder([shelf.id, ...order.filter((s) => s.id !== shelf.id).map((s) => s.id)])
                      }
                      onChanged={onChanged}
                    />
                  )}
                </SortableRow>
              ))}
            </div>
          </SortableList>
        </>
      )}
    </div>
  )
}

function ShelfRow({
  shelf,
  handle,
  isFirst,
  open,
  onToggle,
  onMoveTop,
  onChanged,
}: {
  shelf: Shelf
  handle: React.ReactNode
  isFirst: boolean
  open: boolean
  onToggle: () => void
  onMoveTop: () => void
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
      .catch((e) => toast(friendlyError(e), 'err'))
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

  function reorderVideos(ids: string[]) {
    setVideos((cur) => (cur ? ids.map((id) => cur.find((v) => v.id === id)!).filter(Boolean) : cur))
    run(() => api.reorderShelfVideos(shelf.id, ids), 'Đã đổi thứ tự')
  }

  return (
    <div className="overflow-hidden rounded-xl border border-yt-border bg-yt-panel">
      <div className="flex items-center gap-2 px-3 py-3">
        {handle}

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

        {!isFirst && (
          <Button size="sm" onClick={onMoveTop}>
            Lên đầu
          </Button>
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
        <div className="border-t border-yt-border px-3 py-3">
          {videos === null ? (
            <Spinner />
          ) : videos.length === 0 ? (
            <p className="py-4 text-sm text-yt-dim">
              Hàng này chưa có video. Hàng rỗng sẽ không hiện trên TV.
            </p>
          ) : (
            <SortableList ids={videos.map((v) => v.id)} onReorder={reorderVideos}>
              <ol className="flex flex-col">
                {videos.map((v, i) => (
                  <SortableRow key={v.id} id={v.id}>
                    {(vHandle) => (
                      <li className="flex items-center gap-3 border-b border-yt-border/60 py-2 last:border-0">
                        {vHandle}
                        <span className="w-5 text-right text-xs tabular-nums text-yt-dim">
                          {i + 1}
                        </span>
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
                        {i > 0 && (
                          <Button
                            size="sm"
                            onClick={() =>
                              reorderVideos([
                                v.id,
                                ...videos.filter((x) => x.id !== v.id).map((x) => x.id),
                              ])
                            }
                          >
                            Lên đầu
                          </Button>
                        )}
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
                    )}
                  </SortableRow>
                ))}
              </ol>
            </SortableList>
          )}
        </div>
      )}
    </div>
  )
}
