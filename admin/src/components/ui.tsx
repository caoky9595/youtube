import type { ButtonHTMLAttributes, ReactNode } from 'react'

type BtnProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'ghost' | 'danger'
  size?: 'sm' | 'md'
}

export function Button({ variant = 'ghost', size = 'md', className = '', ...rest }: BtnProps) {
  const base =
    'inline-flex items-center justify-center gap-1.5 rounded-full font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-40'
  const sizes = { sm: 'px-2.5 py-1 text-xs', md: 'px-4 py-2 text-sm' }
  const variants = {
    primary: 'bg-white text-black hover:bg-zinc-200',
    ghost: 'bg-yt-hover text-yt-text hover:bg-yt-border',
    danger: 'bg-yt-red/15 text-red-400 hover:bg-yt-red/25',
  }
  return <button className={`${base} ${sizes[size]} ${variants[variant]} ${className}`} {...rest} />
}

export function Spinner({ label }: { label?: string }) {
  return (
    <div className="flex items-center gap-3 py-10 text-sm text-yt-dim">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-yt-border border-t-white" />
      {label ?? 'Đang tải…'}
    </div>
  )
}

export function Empty({ children }: { children: ReactNode }) {
  return (
    <div className="rounded-xl border border-dashed border-yt-border px-6 py-12 text-center text-sm text-yt-dim">
      {children}
    </div>
  )
}

export function ShelfPicker({
  shelves,
  value,
  onChange,
  allowNone = true,
}: {
  shelves: { id: string; title: string }[]
  value: string | null
  onChange: (id: string | null) => void
  allowNone?: boolean
}) {
  return (
    <select
      value={value ?? ''}
      onChange={(e) => onChange(e.target.value || null)}
      className="rounded-full border border-yt-border bg-yt-panel px-3 py-2 text-sm outline-none focus:border-zinc-500"
    >
      {allowNone && <option value="">Không gán hàng nào</option>}
      {shelves.map((s) => (
        <option key={s.id} value={s.id}>
          {s.title}
        </option>
      ))}
    </select>
  )
}
