import { useState } from 'react'
import * as api from '../lib/api'
import type { Library } from '../lib/types'
import { friendlyError } from '../lib/errors'
import { toast } from './Toast'
import { Button } from './ui'

export function DevicesPanel({
  library,
  onChanged,
  onAddDevice,
  onForget,
}: {
  library: Library
  onChanged: () => void
  onAddDevice: () => void
  onForget: () => void
}) {
  const [name, setName] = useState(library.library_name)
  const [editing, setEditing] = useState(false)

  async function run(fn: () => Promise<unknown>, okMsg: string) {
    try {
      await fn()
      toast(okMsg)
      onChanged()
    } catch (err) {
      toast(friendlyError(err), 'err')
    }
  }

  return (
    <div className="rounded-xl border border-yt-border bg-yt-panel p-5">
      <div className="mb-4 flex flex-wrap items-center gap-3">
        {editing ? (
          <form
            onSubmit={(e) => {
              e.preventDefault()
              setEditing(false)
              if (name.trim() && name.trim() !== library.library_name) {
                run(() => api.renameLibrary(name.trim()), 'Đã đổi tên kho')
              }
            }}
            className="flex flex-1 gap-2"
          >
            <input
              autoFocus
              value={name}
              onChange={(e) => setName(e.target.value)}
              onBlur={() => setEditing(false)}
              className="flex-1 rounded-lg border border-yt-border bg-yt-bg px-3 py-1.5 text-sm outline-none focus:border-zinc-500"
            />
          </form>
        ) : (
          <>
            <h2 className="text-base font-semibold">{library.library_name}</h2>
            <Button size="sm" onClick={() => setEditing(true)}>
              Đổi tên
            </Button>
          </>
        )}
        <div className="ml-auto flex gap-2">
          <Button size="sm" onClick={onAddDevice}>
            + Ghép thêm TV
          </Button>
          <Button
            size="sm"
            variant="danger"
            onClick={() => {
              if (!confirm('Quên kho này trên máy tính? Video và các TV đã ghép không bị xoá — bạn chỉ cần nhập lại mã để quản trị tiếp.')) return
              onForget()
            }}
          >
            Quên kho trên máy này
          </Button>
        </div>
      </div>

      <ul className="flex flex-col gap-2">
        {library.devices.map((d) => (
          <li
            key={d.id}
            className="flex items-center gap-3 rounded-lg bg-yt-bg px-3 py-2 text-sm"
          >
            <span className="font-medium">{d.name}</span>
            <span className="text-xs text-yt-dim">
              hoạt động lần cuối {relative(d.last_seen_at)}
            </span>
            <Button
              size="sm"
              variant="danger"
              className="ml-auto"
              onClick={() => {
                if (!confirm(`Ngắt “${d.name}” khỏi kho? TV đó sẽ hiện lại màn hình nhập mã.`))
                  return
                run(() => api.unpairDevice(d.id), 'Đã ngắt thiết bị')
              }}
            >
              Ngắt
            </Button>
          </li>
        ))}
        {library.devices.length === 0 && (
          <li className="text-sm text-yt-dim">Chưa có TV nào trong kho này.</li>
        )}
      </ul>
    </div>
  )
}

function relative(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime()
  const mins = Math.round(diffMs / 60000)
  if (mins < 1) return 'vừa xong'
  if (mins < 60) return `${mins} phút trước`
  const hours = Math.round(mins / 60)
  if (hours < 24) return `${hours} giờ trước`
  return `${Math.round(hours / 24)} ngày trước`
}
