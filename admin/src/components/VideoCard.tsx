import type { ReactNode } from 'react'
import { formatDuration } from '../lib/youtube'

type Props = {
  youtubeId: string
  title: string
  channelTitle?: string | null
  thumbnailUrl?: string | null
  durationSeconds?: number | null
  dimmed?: boolean
  badge?: ReactNode
  actions?: ReactNode
}

export function VideoCard({
  youtubeId,
  title,
  channelTitle,
  thumbnailUrl,
  durationSeconds,
  dimmed,
  badge,
  actions,
}: Props) {
  const thumb = thumbnailUrl ?? `https://i.ytimg.com/vi/${youtubeId}/hqdefault.jpg`
  const duration = formatDuration(durationSeconds)

  return (
    <div className={`group flex flex-col ${dimmed ? 'opacity-45' : ''}`}>
      <div className="relative aspect-video overflow-hidden rounded-xl bg-yt-panel">
        <img src={thumb} alt="" loading="lazy" className="h-full w-full object-cover" />
        {duration && (
          <span className="absolute bottom-1.5 right-1.5 rounded bg-black/80 px-1.5 py-0.5 text-xs font-medium tabular-nums">
            {duration}
          </span>
        )}
        {badge && <div className="absolute left-1.5 top-1.5">{badge}</div>}
      </div>

      <div className="mt-2 flex flex-col gap-1">
        <a
          href={`https://www.youtube.com/watch?v=${youtubeId}`}
          target="_blank"
          rel="noreferrer"
          title={title}
          className="line-clamp-2 text-sm font-medium leading-snug hover:text-white"
        >
          {title}
        </a>
        {channelTitle && <span className="truncate text-xs text-yt-dim">{channelTitle}</span>}
        {actions && <div className="mt-1 flex flex-wrap items-center gap-1.5">{actions}</div>}
      </div>
    </div>
  )
}
