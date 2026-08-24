import { useCallback, useEffect, useState } from 'react'
import * as api from './lib/api'
import { friendlyError } from './lib/errors'
import { clearShareParams, readSharedUrl } from './lib/share'
import { getAdminToken, isConfigured } from './lib/supabase'
import type { Library, Shelf } from './lib/types'
import { AddTab } from './components/AddTab'
import { DevicesPanel } from './components/DevicesPanel'
import { LibraryTab } from './components/LibraryTab'
import { PairScreen } from './components/PairScreen'
import { PlayBadge } from './components/PlayBadge'
import { ShelvesTab } from './components/ShelvesTab'
import { ToastHost, toast } from './components/Toast'
import { Spinner } from './components/ui'

type Tab = 'add' | 'library' | 'shelves' | 'devices'

const TABS: { id: Tab; label: string }[] = [
  { id: 'add', label: 'Thêm video' },
  { id: 'library', label: 'Kho video' },
  { id: 'shelves', label: 'Hàng trên TV' },
  { id: 'devices', label: 'TV đã ghép' },
]

type Phase =
  | { kind: 'checking' }
  | { kind: 'pairing' }
  | { kind: 'ready'; library: Library }
  | { kind: 'error'; message: string }

export default function App() {
  const [phase, setPhase] = useState<Phase>({ kind: 'checking' })
  // null = khong o man nhap ma. true = dang ghep them TV moi vao kho hien tai.
  const [addingDevice, setAddingDevice] = useState(false)
  const [tab, setTab] = useState<Tab>('add')
  // Link do app YouTube chia se sang (Android). Doc mot lan luc mo trang.
  const [sharedUrl, setSharedUrl] = useState<string | null>(() =>
    readSharedUrl(window.location.search),
  )
  const [shelves, setShelves] = useState<Shelf[]>([])
  const [videos, setVideos] = useState<api.VideoWithShelves[] | null>(null)

  const loadContent = useCallback(async () => {
    try {
      const [s, v] = await Promise.all([api.getShelves(), api.getVideos()])
      setShelves(s)
      setVideos(v)
    } catch (err) {
      toast(friendlyError(err), 'err')
    }
  }, [])

  /** Kiem tra token con hieu luc; token co the da bi ngat tu mot may khac. */
  const openLibrary = useCallback(async () => {
    if (!getAdminToken()) {
      setPhase({ kind: 'pairing' })
      return
    }
    try {
      const library = await api.libraryInfo()
      setPhase({ kind: 'ready', library })
      await loadContent()
    } catch (err) {
      // Nhan dien bang KIEU loi, khong do chu trong thong bao: thong bao la van
      // ban cho nguoi doc, doi cau chu mot cai la logic vo hieu ngay.
      if (err instanceof api.TokenRejected) {
        setPhase({ kind: 'pairing' })
      } else {
        setPhase({ kind: 'error', message: friendlyError(err) })
      }
    }
  }, [loadContent])

  useEffect(() => {
    openLibrary()
  }, [openLibrary])

  // Server tu choi token (bi thu hoi, hoac kho khong con) -> ve man nhap ma
  // thay vi de nguoi dung ket lai voi mot toast loi.
  useEffect(
    () =>
      api.onTokenRejected(() => {
        setVideos(null)
        setShelves([])
        setPhase({ kind: 'pairing' })
      }),
    [],
  )

  // Co link chia se thi nhay san sang tab Them video
  useEffect(() => {
    if (sharedUrl) setTab('add')
  }, [sharedUrl])

  if (!isConfigured) return <NeedsConfig />

  if (phase.kind === 'checking') return <Spinner />

  if (phase.kind === 'error') {
    return (
      <div className="mx-auto max-w-lg px-6 py-24 text-center">
        <h1 className="mb-3 text-xl font-semibold">Không kết nối được Supabase</h1>
        <p className="mb-2 text-sm leading-relaxed text-yt-dim">{phase.message}</p>
        <p className="text-sm leading-relaxed text-yt-dim">
          Kiểm tra <code className="text-zinc-400">VITE_SUPABASE_URL</code> và{' '}
          <code className="text-zinc-400">VITE_SUPABASE_ANON_KEY</code> trong{' '}
          <code className="text-zinc-400">admin/.env</code>, và đã chạy 3 file SQL trong{' '}
          <code className="text-zinc-400">supabase/</code> chưa.
        </p>
      </div>
    )
  }

  if (phase.kind === 'pairing') {
    return <PairScreen onPaired={openLibrary} />
  }

  if (addingDevice) {
    return (
      <PairScreen
        mode="add"
        onPaired={() => {
          setAddingDevice(false)
          setTab('devices')
          openLibrary()
        }}
        onCancel={() => setAddingDevice(false)}
      />
    )
  }

  if (videos === null) return <Spinner />

  const { library } = phase

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-40 flex flex-wrap items-center gap-5 border-b border-yt-border bg-yt-bg/95 px-6 py-3 backdrop-blur">
        <div className="flex items-center gap-2">
          <PlayBadge />
          <span className="text-lg font-bold tracking-tight">YouTube</span>
          <span className="text-sm text-yt-dim">Admin</span>
        </div>

        <nav className="flex gap-1">
          {TABS.map((t) => (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              className={`rounded-full px-3.5 py-1.5 text-sm transition-colors ${
                tab === t.id ? 'bg-yt-text text-black' : 'text-yt-dim hover:bg-yt-hover'
              }`}
            >
              {t.label}
              {t.id === 'devices' && ` (${library.devices.length})`}
            </button>
          ))}
        </nav>

        <span className="ml-auto text-xs text-yt-dim">
          {library.library_name} · {videos.length} video · {shelves.length} hàng
        </span>
      </header>

      <main className="mx-auto max-w-7xl px-6 py-8">
        {tab === 'add' && (
          <AddTab
            shelves={shelves}
            onChanged={loadContent}
            sharedUrl={sharedUrl}
            onSharedConsumed={() => {
              setSharedUrl(null)
              clearShareParams()
            }}
          />
        )}
        {tab === 'library' && (
          <LibraryTab videos={videos} shelves={shelves} onChanged={loadContent} />
        )}
        {tab === 'shelves' && <ShelvesTab shelves={shelves} onChanged={loadContent} />}
        {tab === 'devices' && (
          <DevicesPanel
            library={library}
            onChanged={openLibrary}
            onAddDevice={() => setAddingDevice(true)}
            onForget={() => {
              api.forgetLibrary()
              setVideos(null)
              setShelves([])
              setPhase({ kind: 'pairing' })
            }}
          />
        )}
      </main>

      <ToastHost />
    </div>
  )
}

/**
 * Thieu VITE_SUPABASE_URL / VITE_SUPABASE_ANON_KEY. Hay gap nhat khi vua deploy
 * ma chua khai bao bien moi truong o host — cac bien VITE_* duoc nhung vao
 * bundle luc BUILD, nen khai bao xong phai build lai.
 */
function NeedsConfig() {
  return (
    <div className="mx-auto max-w-xl px-6 py-20">
      <div className="mb-6 flex items-center gap-2">
        <PlayBadge height={24} />
        <span className="text-xl font-bold tracking-tight">YouTube</span>
        <span className="text-base text-yt-dim">Admin</span>
      </div>

      <h1 className="mb-3 text-xl font-semibold">Chưa cấu hình Supabase</h1>
      <p className="mb-5 text-sm leading-relaxed text-yt-dim">
        Trang này cần hai biến môi trường. Thiếu chúng thì không kết nối được vào đâu cả.
      </p>

      <pre className="mb-5 overflow-x-auto rounded-xl border border-yt-border bg-yt-panel px-4 py-3 text-xs leading-relaxed">
        <code>{'VITE_SUPABASE_URL=https://xxxxxxxxxxxx.supabase.co\nVITE_SUPABASE_ANON_KEY=eyJhbGciOi...'}</code>
      </pre>

      <p className="mb-2 text-sm leading-relaxed text-yt-dim">
        Lấy hai giá trị đó ở Supabase → <b className="text-yt-text">Project Settings → API</b>.
      </p>
      <ul className="mb-5 list-disc space-y-1 pl-5 text-sm leading-relaxed text-yt-dim">
        <li>
          Chạy ở máy: điền vào <code className="text-zinc-400">admin/.env</code> rồi khởi động lại{' '}
          <code className="text-zinc-400">npm run dev</code>.
        </li>
        <li>
          Trên Vercel/Netlify: khai ở phần Environment Variables rồi{' '}
          <b className="text-yt-text">deploy lại</b> — biến <code className="text-zinc-400">VITE_*</code>{' '}
          được nhúng vào lúc build, không đọc lúc chạy.
        </li>
      </ul>

      <p className="text-sm leading-relaxed text-yt-dim">
        Chi tiết: <code className="text-zinc-400">docs/SETUP.md</code> mục 1 và 2.
      </p>
    </div>
  )
}
