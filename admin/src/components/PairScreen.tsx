import { useRef, useState } from 'react'
import * as api from '../lib/api'
import { friendlyError } from '../lib/errors'
import { PlayBadge } from './PlayBadge'
import { Button } from './ui'

const CODE_LENGTH = 6

/**
 * first = lan dau, chua quan tri kho nao
 * add   = da co kho, ghep THEM mot TV nua vao kho do
 */
type Mode = 'first' | 'add'

type Props = {
  mode?: Mode
  onPaired: () => void
  onCancel?: () => void
}

const COPY: Record<Mode, { title: string; hint: string }> = {
  first: {
    title: 'Kết nối với TV',
    hint: 'Mở app YouTube trên TV, vào mục Kết nối ở menu bên trái — nó sẽ hiện một mã 6 ký tự. Nhập mã đó vào đây.',
  },
  add: {
    title: 'Ghép thêm một TV',
    hint: 'Mở app trên TV mới, vào mục Kết nối, nó sẽ hiện một mã 6 ký tự. Nhập mã đó vào đây để TV này dùng chung kho video.',
  },
}

export function PairScreen({ mode = 'first', onPaired, onCancel }: Props) {
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (code.length !== CODE_LENGTH) return
    setBusy(true)
    setError(null)
    try {
      await api.claimCode(code)
      onPaired()
    } catch (err) {
      setError(friendlyError(err))
      setCode('')
      inputRef.current?.focus()
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-6">
      <form onSubmit={submit} className="w-full max-w-md">
        <div className="mb-8 flex items-center gap-2">
          <PlayBadge height={26} />
          <span className="text-2xl font-bold tracking-tight">YouTube</span>
          <span className="text-lg text-yt-dim">Admin</span>
        </div>

        <h1 className="mb-2 text-xl font-semibold">{COPY[mode].title}</h1>
        <p className="mb-7 text-sm leading-relaxed text-yt-dim">{COPY[mode].hint}</p>

        <label className="mb-2 block text-xs text-yt-dim">Mã hiện trên TV</label>
        <input
          ref={inputRef}
          autoFocus
          value={code}
          onChange={(e) => {
            // Ma chi gom chu in va so, khong co O/0/I/1/U de khoi nhin lan
            const cleaned = e.target.value
              .toUpperCase()
              .replace(/[^0-9A-Z]/g, '')
              .slice(0, CODE_LENGTH)
            setCode(cleaned)
            setError(null)
          }}
          placeholder="AB3K7Z"
          spellCheck={false}
          autoComplete="off"
          className="mb-5 w-full rounded-xl border border-yt-border bg-yt-panel px-4 py-4 text-center font-mono text-3xl tracking-[0.4em] outline-none focus:border-zinc-500"
        />

        {error && <p className="mb-4 text-sm text-red-400">{error}</p>}

        <Button
          type="submit"
          variant="primary"
          disabled={busy || code.length !== CODE_LENGTH}
          className="w-full"
        >
          {busy ? 'Đang kết nối…' : 'Kết nối'}
        </Button>

        {onCancel && (
          <Button onClick={onCancel} className="mt-3 w-full">
            Huỷ
          </Button>
        )}

        <p className="mt-7 text-xs leading-relaxed text-yt-dim">
          Mã có hiệu lực 15 phút rồi TV tự đổi mã mới — cứ nhập đúng mã đang hiện trên
          màn hình. Một mã dùng được cho nhiều máy quản trị, nên nhập ở điện thoại rồi
          vẫn nhập được ở máy tính.
        </p>
      </form>
    </div>
  )
}
