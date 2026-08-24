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
            className="flex flex-wrap items-center gap-3 rounded-lg bg-yt-bg px-3 py-2 text-sm"
          >
            <span className="font-medium">{d.name}</span>
            {d.paired ? (
              <span className="rounded-full bg-emerald-600/20 px-2 py-0.5 text-xs text-emerald-300">
                đang kết nối
              </span>
            ) : (
              <span className="rounded-full bg-yt-hover px-2 py-0.5 text-xs text-yt-dim">
                đã ngắt
              </span>
            )}
            <span className="text-xs text-yt-dim">
              hoạt động lần cuối {relative(d.last_seen_at)}
            </span>

            <span className="ml-auto flex gap-2">
              {d.paired && (
                <Button
                  size="sm"
                  onClick={() => {
                    if (
                      !confirm(
                        `Ngắt “${d.name}”? TV đó hiện lại màn hình nhập mã. Ghép lại là về đúng kho này cùng toàn bộ video.`,
                      )
                    )
                      return
                    run(() => api.unpairDevice(d.id), 'Đã ngắt thiết bị')
                  }}
                >
                  Ngắt
                </Button>
              )}
              <Button
                size="sm"
                variant="danger"
                onClick={() => {
                  if (
                    !confirm(
                      `Bỏ hẳn “${d.name}” khỏi kho? Khác với Ngắt: TV đó ghép lại sẽ tạo kho mới rỗng, không về kho này nữa. Video trong kho không bị xoá.`,
                    )
                  )
                    return
                  run(() => api.forgetDevice(d.id), 'Đã bỏ thiết bị khỏi kho')
                }}
              >
                Bỏ khỏi kho
              </Button>
            </span>
          </li>
        ))}
        {library.devices.length === 0 && (
          <li className="text-sm text-yt-dim">Chưa có TV nào trong kho này.</li>
        )}
      </ul>

      <p className="mt-4 text-xs leading-relaxed text-yt-dim">
        <b className="text-yt-text">Ngắt</b> chỉ thu hồi quyền đọc của TV — ghép lại là về đúng kho
        này cùng toàn bộ video, kể cả khi nhập mã từ một trình duyệt khác.{' '}
        <b className="text-yt-text">Bỏ khỏi kho</b> thì cắt hẳn liên kết: TV đó ghép lại sẽ tạo kho
        mới rỗng.
      </p>
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
