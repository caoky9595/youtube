import { useState } from 'react'
import * as api from '../lib/api'
import { friendlyError } from '../lib/errors'
import type { Library } from '../lib/types'
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
  /** Máy này thôi không quản lý kho nữa. TV không bị ảnh hưởng gì. */
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
    <div className="flex max-w-3xl flex-col gap-5">
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
          <Button size="sm" className="ml-auto" onClick={onAddDevice}>
            + Ghép thêm TV
          </Button>
        </div>

        <ul className="flex flex-col gap-2">
          {library.devices.map((d) => (
            <li
              key={d.id}
              className="flex flex-wrap items-center gap-3 rounded-lg bg-yt-bg px-3 py-2 text-sm"
            >
              <span className="font-medium">{d.name}</span>
              <span className="text-xs text-yt-dim">
                hoạt động lần cuối {relative(d.last_seen_at)}
              </span>

              <span className="ml-auto flex gap-2">
                <Button
                  size="sm"
                  variant="danger"
                  onClick={() => {
                    if (
                      !confirm(
                        `Bỏ hẳn “${d.name}” khỏi kho?\n\nTV đó sẽ KHÔNG xem được video nữa và hiện lại mã kết nối mới. TOÀN BỘ VIDEO trong kho này sẽ bị XOÁ — không khôi phục được. Ghép lại sau đó sẽ là một kho trống.`,
                      )
                    )
                      return
                    run(() => api.forgetDevice(d.id), `Đã bỏ ${d.name} khỏi kho và xoá video`)
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
      </div>

      {/* Việc người ta hay muốn nhất, nên để riêng và nói rõ nó KHÔNG chạm tới TV */}
      <div className="rounded-xl border border-yt-border bg-yt-panel p-5">
        <h3 className="mb-1 text-sm font-semibold">Ngừng quản lý trên máy này</h3>
        <p className="mb-4 text-sm leading-relaxed text-yt-dim">
          Máy/trình duyệt này thôi không quản lý kho nữa.{' '}
          <b className="text-yt-text">TV không bị ảnh hưởng gì</b> — vẫn xem được đầy đủ video như
          cũ. Video và các hàng cũng không mất.
          <br />
          Muốn quản lý lại: trên TV chọn <b className="text-yt-text">Kết nối → Lấy mã để thêm máy
          quản trị</b>, rồi nhập mã đó vào đây.
        </p>
        <Button
          onClick={() => {
            if (
              !confirm(
                'Máy này thôi quản lý kho?\n\nTV vẫn xem được đầy đủ, không mất video nào. Muốn quản lý lại thì lấy mã trên TV rồi nhập vào đây.',
              )
            )
              return
            onForget()
          }}
        >
          Ngừng quản lý trên máy này
        </Button>
      </div>
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
