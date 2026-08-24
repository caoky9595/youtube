export type Video = {
  id: string
  youtube_id: string
  title: string
  channel_title: string | null
  thumbnail_url: string | null
  /** null khi vua thêm bằng đường dẫn; app TV điền vào sau lần phát đầu tiên. */
  duration_seconds: number | null
  is_visible: boolean
  added_at: string
  added_via: 'admin' | 'url'
}

export type Shelf = {
  id: string
  title: string
  position: number
  is_visible: boolean
  created_at: string
}

/** Mot app TV thuoc kho. paired=false la da bi Ngat nhung van giu lien ket kho. */
export type Device = {
  id: string
  name: string
  paired: boolean
  paired_at: string | null
  last_seen_at: string
}

export type Library = {
  library_id: string
  library_name: string
  devices: Device[]
}

/**
 * Metadata mot video lay tu oEmbed, chua nam trong DB.
 * Khong co thoi luong: oEmbed khong tra ve, va app TV moi la thu bao thoi luong
 * ve server (report_duration) sau lan phat dau tien.
 */
export type YtResult = {
  youtubeId: string
  title: string
  channelTitle: string
  thumbnailUrl: string
}
