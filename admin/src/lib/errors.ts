/**
 * Doi thong bao loi cua trinh duyet thanh cau nguoi dung hieu duoc.
 *
 * Khi fetch that bai vi mang, Safari nem "Load failed" con Chrome nem
 * "Failed to fetch" — ca hai deu khong noi len van de. Nguyen nhan hay gap nhat
 * la VITE_SUPABASE_URL tro vao "localhost": tren dien thoai thi localhost la
 * chinh cai dien thoai, khong phai may dang chay server.
 */
const NETWORK_ERRORS = /load failed|failed to fetch|networkerror|network request failed/i

/**
 * Loi tra ve tu supabase-js (PostgrestError) khong phai instanceof Error —
 * chi la mot object thuong co truong message. "err instanceof Error" luon
 * false voi loai nay nen roi vao String(err), ra dung chu "[object Object]"
 * thay vi thong bao tieng Viet — vi du nhap sai ma se hien "[object Object]"
 * thay vi "Mã không đúng".
 */
function messageOf(err: unknown): string {
  if (typeof err === 'object' && err !== null && typeof (err as { message?: unknown }).message === 'string') {
    return (err as { message: string }).message
  }
  return String(err)
}

export function friendlyError(err: unknown): string {
  const raw = messageOf(err)
  if (!NETWORK_ERRORS.test(raw)) return raw

  const url = import.meta.env.VITE_SUPABASE_URL ?? '(chưa cấu hình)'
  const isLocalhost = /^https?:\/\/(localhost|127\.0\.0\.1)/i.test(url)

  return isLocalhost
    ? `Không gọi được ${url}. Địa chỉ này là "localhost" nên chỉ chạy được trên ` +
        `chính máy dựng server — trên điện thoại phải đổi VITE_SUPABASE_URL sang ` +
        `địa chỉ IP của máy đó (hoặc tên miền thật) rồi khởi động lại.`
    : `Không gọi được ${url}. Kiểm tra máy này có mạng, và địa chỉ đó có đúng không.`
}
