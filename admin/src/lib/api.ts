import { getAdminToken, sb, setAdminToken } from './supabase'
import type { Library, Shelf, Video, YtResult } from './types'

/**
 * Server tu choi token quan tri: token da bi thu hoi, hoac tro vao mot kho khong
 * con ton tai (vi du sau khi doi Supabase project). Truoc day cac cho goi chi
 * hien mot toast roi trang ket o do — nguoi dung khong biet phai lam gi.
 */
export class TokenRejected extends Error {}

const TOKEN_ERRORS = /token quản trị|thiếu hoặc sai token|token.*không hợp lệ/i

type Listener = () => void
const tokenListeners = new Set<Listener>()

/** App dang ky de biet khi nao phai quay ve man nhap ma. */
export function onTokenRejected(fn: Listener): () => void {
  tokenListeners.add(fn)
  return () => {
    tokenListeners.delete(fn)
  }
}

/**
 * Moi loi tu Supabase di qua day. Loi token thi xoa token va bao cho App biet
 * de dua nguoi dung ve man nhap ma; cac loi khac nem nguyen.
 */
function rethrow(error: unknown): never {
  const message = (error as { message?: string } | null)?.message ?? String(error)
  if (TOKEN_ERRORS.test(message)) {
    setAdminToken(null)
    tokenListeners.forEach((fn) => fn())
    throw new TokenRejected(
      'Máy này không còn quyền quản trị kho đó. Lấy mã trên TV rồi nhập lại để kết nối.',
    )
  }
  throw error
}

/* ------------------------------- ghep ma -------------------------------- */

/**
 * Nhap ma hien tren TV. Neu trang nay dang quan tri mot kho roi thi TV moi se
 * duoc gan vao chinh kho do; chua co kho nao thi tao kho moi.
 */
export async function claimCode(code: string): Promise<Library> {
  const { data, error } = await sb().rpc('pair_claim', {
    p_code: code.trim().toUpperCase(),
    p_admin_token: getAdminToken(),
  })
  if (error) rethrow(error)

  const claimed = data as { admin_token: string }
  setAdminToken(claimed.admin_token)
  return libraryInfo()
}

export async function libraryInfo(): Promise<Library> {
  const token = getAdminToken()
  if (!token) throw new Error('Chưa ghép với TV nào')
  const { data, error } = await sb().rpc('library_info', { p_admin_token: token })
  if (error) rethrow(error)
  return data as Library
}

export async function renameLibrary(name: string) {
  const { error } = await sb().rpc('rename_library', {
    p_admin_token: getAdminToken(),
    p_name: name,
  })
  if (error) rethrow(error)
}

/**
 * Thu hoi quyen doc cua mot TV. Van giu lien ket voi kho, nen TV do ghep lai la
 * tro ve dung kho nay cung toan bo video.
 */
/**
 * Bo han TV khoi kho VA xoa sach video cua kho do. Day la duong DUY NHAT de
 * cat mot TV khoi kho.
 */
export async function forgetDevice(deviceId: string) {
  const { error } = await sb().rpc('forget_device', {
    p_admin_token: getAdminToken(),
    p_device_id: deviceId,
  })
  if (error) rethrow(error)
}

export function forgetLibrary() {
  setAdminToken(null)
}

/* ------------------------------- shelves -------------------------------- */

export async function getShelves(): Promise<Shelf[]> {
  const { data, error } = await sb().from('shelves').select('*').order('position')
  if (error) rethrow(error)
  return data ?? []
}

export async function createShelf(title: string): Promise<Shelf> {
  // Qua RPC de kho duoc suy ra tu token, khong phai gui library_id tu client
  const { data, error } = await sb().rpc('create_shelf', { p_title: title })
  if (error) rethrow(error)
  return data as Shelf
}

export async function updateShelf(id: string, patch: Partial<Pick<Shelf, 'title' | 'is_visible'>>) {
  const { error } = await sb().from('shelves').update(patch).eq('id', id)
  if (error) rethrow(error)
}

export async function deleteShelf(id: string) {
  // shelf_videos co ON DELETE CASCADE nen video khong bi xoa, chi mat lien ket
  const { error } = await sb().from('shelves').delete().eq('id', id)
  if (error) rethrow(error)
}

export async function reorderShelves(ids: string[]) {
  const { error } = await sb().rpc('reorder_shelves', { p_shelf_ids: ids })
  if (error) rethrow(error)
}

/* -------------------------------- videos -------------------------------- */

export type VideoWithShelves = Video & { shelfIds: string[] }

export async function getVideos(): Promise<VideoWithShelves[]> {
  const { data, error } = await sb()
    .from('videos')
    .select('*, shelf_videos(shelf_id)')
    .order('added_at', { ascending: false })
  if (error) rethrow(error)

  return (data ?? []).map((row) => {
    const { shelf_videos, ...video } = row as Video & {
      shelf_videos: { shelf_id: string }[] | null
    }
    return { ...video, shelfIds: (shelf_videos ?? []).map((s) => s.shelf_id) }
  })
}

/** Cac youtube_id da co trong kho — dung de hien "Da them" tren ket qua tim kiem. */
export async function getExistingYoutubeIds(): Promise<Set<string>> {
  const { data, error } = await sb().from('videos').select('youtube_id')
  if (error) rethrow(error)
  return new Set((data ?? []).map((r) => r.youtube_id))
}

export async function getShelfVideos(shelfId: string): Promise<Video[]> {
  const { data, error } = await sb()
    .from('shelf_videos')
    .select('position, videos(*)')
    .eq('shelf_id', shelfId)
    .order('position')
  if (error) rethrow(error)
  return (data ?? [])
    .map((r) => (r as unknown as { videos: Video | null }).videos)
    .filter((v): v is Video => v !== null)
}

export async function addVideo(yt: YtResult, shelfId: string | null): Promise<Video> {
  const { data, error } = await sb().rpc('add_video', {
    p_youtube_id: yt.youtubeId,
    p_title: yt.title,
    p_channel_title: yt.channelTitle || null,
    p_thumbnail_url: yt.thumbnailUrl || null,
    p_shelf_id: shelfId,
  })
  if (error) rethrow(error)
  return data as Video
}

export async function deleteVideo(id: string) {
  const { error } = await sb().from('videos').delete().eq('id', id)
  if (error) rethrow(error)
}

/**
 * Xoa nhieu video trong MOT request (`id=in.(...)`) thay vi N request.
 * RLS van xet tung dong, nen chi xoa duoc video thuoc kho cua token nay.
 */
export async function deleteVideos(ids: string[]) {
  if (ids.length === 0) return
  const { error } = await sb().from('videos').delete().in('id', ids)
  if (error) rethrow(error)
}

export async function setVideoVisible(id: string, is_visible: boolean) {
  const { error } = await sb().from('videos').update({ is_visible }).eq('id', id)
  if (error) rethrow(error)
}

export async function addToShelf(shelfId: string, videoId: string) {
  const { data: last } = await sb()
    .from('shelf_videos')
    .select('position')
    .eq('shelf_id', shelfId)
    .order('position', { ascending: false })
    .limit(1)
  const position = (last?.[0]?.position ?? -1) + 1

  const { error } = await sb()
    .from('shelf_videos')
    .upsert({ shelf_id: shelfId, video_id: videoId, position })
  if (error) rethrow(error)
}

export async function removeFromShelf(shelfId: string, videoId: string) {
  const { error } = await sb()
    .from('shelf_videos')
    .delete()
    .eq('shelf_id', shelfId)
    .eq('video_id', videoId)
  if (error) rethrow(error)
}

export async function reorderShelfVideos(shelfId: string, videoIds: string[]) {
  const { error } = await sb().rpc('reorder_shelf_videos', {
    p_shelf_id: shelfId,
    p_video_ids: videoIds,
  })
  if (error) rethrow(error)
}
