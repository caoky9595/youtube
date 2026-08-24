import { useEffect, useMemo, useState } from 'react'
import * as api from '../lib/api'
import type { Shelf } from '../lib/types'
import { friendlyError } from '../lib/errors'
import { toast } from './Toast'
import { VideoCard } from './VideoCard'
import { Button, Empty, Spinner } from './ui'

type Props = {
  videos: api.VideoWithShelves[] | null
  shelves: Shelf[]
  onChanged: () => void
}

export function LibraryTab({ videos, shelves, onChanged }: Props) {
  const [filter, setFilter] = useState('')
  const [shelfFilter, setShelfFilter] = useState<string>('all')
  const [picked, setPicked] = useState<Set<string>>(new Set())
  const [busy, setBusy] = useState(false)
  const shelfById = useMemo(() => new Map(shelves.map((s) => [s.id, s.title])), [shelves])

  useEffect(() => {
    if (!videos) return
    // Sau khi xoa/tai lai, bo khoi danh sach chon nhung id khong con
    setPicked((cur) => {
      const alive = new Set(videos.map((v) => v.id))
      const next = new Set([...cur].filter((id) => alive.has(id)))
      return next.size === cur.size ? cur : next
    })
  }, [videos])

  const shown = useMemo(() => {
    if (!videos) return null
    const q = filter.trim().toLowerCase()
    return videos.filter((v) => {
      if (shelfFilter === 'none' && v.shelfIds.length > 0) return false
      if (shelfFilter !== 'all' && shelfFilter !== 'none' && !v.shelfIds.includes(shelfFilter))
        return false
      if (!q) return true
      return (
        v.title.toLowerCase().includes(q) ||
        (v.channel_title ?? '').toLowerCase().includes(q) ||
        v.youtube_id.toLowerCase().includes(q)
      )
    })
  }, [videos, filter, shelfFilter])

  async function run(fn: () => Promise<unknown>, okMsg: string) {
    try {
      await fn()
      toast(okMsg)
      onChanged()
    } catch (err) {
      toast(friendlyError(err), 'err')
    }
  }

  function toggle(id: string) {
    setPicked((cur) => {
      const next = new Set(cur)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  async function deletePicked() {
    const ids = [...picked]
    if (
      !confirm(
        `Xoá hẳn ${ids.length} video khỏi kho? Video vẫn còn trên YouTube, chỉ mất khỏi TV của bạn.`,
      )
    ) {
      return
    }
    setBusy(true)
    try {
      await api.deleteVideos(ids)
      setPicked(new Set())
      toast(`Đã xoá ${ids.length} video`)
      onChanged()
    } catch (err) {
      toast(friendlyError(err), 'err')
    } finally {
      setBusy(false)
    }
  }

  if (!videos) return <Spinner />

  const shownIds = shown?.map((v) => v.id) ?? []
  const allShownPicked = shownIds.length > 0 && shownIds.every((id) => picked.has(id))

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-center gap-3">
        <input
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          placeholder="Lọc theo tên, kênh, ID…"
          className="min-w-60 flex-1 rounded-full border border-yt-border bg-yt-panel px-4 py-2 text-sm outline-none focus:border-zinc-500"
        />
        <select
          value={shelfFilter}
          onChange={(e) => setShelfFilter(e.target.value)}
          className="rounded-full border border-yt-border bg-yt-panel px-3 py-2 text-sm outline-none focus:border-zinc-500"
        >
          <option value="all">Tất cả hàng</option>
          <option value="none">Chưa gán hàng nào</option>
          {shelves.map((s) => (
            <option key={s.id} value={s.id}>
              {s.title}
            </option>
          ))}
        </select>
        <span className="text-sm text-yt-dim">
          {shown?.length ?? 0}/{videos.length} video
        </span>
        {shownIds.length > 0 && (
          <Button
            size="sm"
            onClick={() =>
              setPicked((cur) => {
                const next = new Set(cur)
                if (allShownPicked) shownIds.forEach((id) => next.delete(id))
                else shownIds.forEach((id) => next.add(id))
                return next
              })
            }
          >
            {allShownPicked ? 'Bỏ chọn hết' : `Chọn cả ${shownIds.length}`}
          </Button>
        )}
      </div>

      {picked.size > 0 && (
        <div className="sticky top-16 z-30 mb-5 flex flex-wrap items-center gap-3 rounded-xl border border-yt-border bg-yt-panel/95 px-4 py-3 backdrop-blur">
          <span className="text-sm font-medium">Đã chọn {picked.size} video</span>
          <Button size="sm" variant="danger" disabled={busy} onClick={deletePicked}>
            {busy ? 'Đang xoá…' : `Xoá ${picked.size} video`}
          </Button>
          <Button size="sm" onClick={() => setPicked(new Set())}>
            Bỏ chọn
          </Button>
        </div>
      )}

      {shown && shown.length === 0 ? (
        <Empty>
          {videos.length === 0
            ? 'Kho còn trống — sang tab “Thêm video” để bắt đầu.'
            : 'Không có video nào khớp bộ lọc.'}
        </Empty>
      ) : (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(230px,1fr))] gap-x-4 gap-y-7">
          {shown?.map((v) => (
            <VideoCard
              key={v.id}
              youtubeId={v.youtube_id}
              title={v.title}
              channelTitle={v.channel_title}
              thumbnailUrl={v.thumbnail_url}
              durationSeconds={v.duration_seconds}
              dimmed={!v.is_visible}
              selected={picked.has(v.id)}
              onToggleSelect={() => toggle(v.id)}
              badge={
                !v.is_visible ? (
                  <span className="rounded bg-black/80 px-1.5 py-0.5 text-xs">Đang ẩn</span>
                ) : null
              }
              actions={
                <>
                  {v.shelfIds.map((id) => (
                    <span
                      key={id}
                      className="rounded-full bg-yt-hover px-2 py-0.5 text-xs text-yt-dim"
                    >
                      {shelfById.get(id) ?? '?'}
                    </span>
                  ))}
                  <select
                    value=""
                    onChange={(e) => {
                      const id = e.target.value
                      if (!id) return
                      const name = shelfById.get(id)
                      run(() => api.addToShelf(id, v.id), `Đã thêm vào ${name}`)
                    }}
                    className="rounded-full bg-yt-hover px-2 py-0.5 text-xs outline-none"
                  >
                    <option value="">+ hàng</option>
                    {shelves
                      .filter((s) => !v.shelfIds.includes(s.id))
                      .map((s) => (
                        <option key={s.id} value={s.id}>
                          {s.title}
                        </option>
                      ))}
                  </select>
                  <Button
                    size="sm"
                    onClick={() =>
                      run(
                        () => api.setVideoVisible(v.id, !v.is_visible),
                        v.is_visible ? 'Đã ẩn khỏi TV' : 'Đã hiện lại',
                      )
                    }
                  >
                    {v.is_visible ? 'Ẩn' : 'Hiện'}
                  </Button>
                  <Button
                    size="sm"
                    variant="danger"
                    onClick={() => {
                      if (!confirm(`Xoá hẳn “${v.title}” khỏi YouTube?`)) return
                      run(() => api.deleteVideo(v.id), 'Đã xoá')
                    }}
                  >
                    Xoá
                  </Button>
                </>
              }
            />
          ))}
        </div>
      )}
    </div>
  )
}
