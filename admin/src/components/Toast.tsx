import { useEffect, useState } from 'react'

export type ToastKind = 'ok' | 'err'
export type ToastMsg = { id: number; kind: ToastKind; text: string }

let nextId = 1
const listeners = new Set<(m: ToastMsg) => void>()

export function toast(text: string, kind: ToastKind = 'ok') {
  const msg = { id: nextId++, kind, text }
  listeners.forEach((l) => l(msg))
}

export function ToastHost() {
  const [msgs, setMsgs] = useState<ToastMsg[]>([])

  useEffect(() => {
    const add = (m: ToastMsg) => {
      setMsgs((cur) => [...cur, m])
      setTimeout(() => setMsgs((cur) => cur.filter((x) => x.id !== m.id)), 4000)
    }
    listeners.add(add)
    return () => {
      listeners.delete(add)
    }
  }, [])

  return (
    <div className="fixed bottom-6 right-6 z-50 flex flex-col gap-2">
      {msgs.map((m) => (
        <div
          key={m.id}
          className={`rounded-lg px-4 py-3 text-sm shadow-lg ${
            m.kind === 'ok' ? 'bg-emerald-600 text-white' : 'bg-yt-red text-white'
          }`}
        >
          {m.text}
        </div>
      ))}
    </div>
  )
}
