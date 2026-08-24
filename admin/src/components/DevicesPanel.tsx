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
  onReconnect,
  onForget,
}: {
  library: Library
  onChanged: () => void
  onAddDevice: () => void
  /** Mo man nhap ma de noi lai mot TV vua bi thu hoi quyen. */
  onReconnect: () => void
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
              {d.paired ? (
                <span className="rounded-full bg-emerald-600/20 px-2 py-0.5 text-xs text-emerald-300">
                  đang xem được
                </span>
              ) : (
                <span className="rounded-full bg-amber-500/15 px-2 py-0.5 text-xs text-amber-300">
                  đã bị thu hồi
                </span>
              )}
              <span className="text-xs text-yt-dim">
                hoạt động lần cuối {relative(d.last_seen_at)}
              </span>

              <span className="ml-auto flex gap-2">
                {!d.paired && (
                  <Button size="sm" variant="primary" onClick={onReconnect}>
                    Kết nối lại
                  </Button>
                )}
                {d.paired && (
                  <Button
                    size="sm"
                    variant="danger"
                    onClick={() => {
                      if (
                        !confirm(
                          `Thu hồi quyền của “${d.name}”?\n\nTV đó sẽ KHÔNG xem được video nữa. Video trong kho vẫn còn nguyên: muốn nối lại thì trên TV vào mục Kết nối lấy mã mới, rồi bấm “Kết nối lại” ở đây.\n\nChỉ muốn máy này thôi quản lý kho, TV vẫn xem bình thường? Dùng “Ngừng quản lý trên máy này” ở dưới.`,
                        )
                      )
                        return
                      run(() => api.unpairDevice(d.id), `Đã thu hồi quyền của ${d.name}`)
                    }}
                  >
                    Thu hồi quyền
                  </Button>
                )}
                <Button
                  size="sm"
                  variant="danger"
                  onClick={() => {
                    if (
                      !confirm(
                        `Bỏ hẳn “${d.name}” khỏi kho?\n\nMạnh hơn Thu hồi quyền: TV đó ghép lại sẽ tạo kho mới rỗng, không về kho này nữa. Video trong kho không bị xoá.`,
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
