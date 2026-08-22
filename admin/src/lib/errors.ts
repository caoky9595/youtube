/**
 * Doi thong bao loi cua trinh duyet thanh cau nguoi dung hieu duoc.
 *
 * Khi fetch that bai vi mang, Safari nem "Load failed" con Chrome nem
 * "Failed to fetch" — ca hai deu khong noi len van de. Nguyen nhan hay gap nhat
 * la VITE_SUPABASE_URL tro vao "localhost": tren dien thoai thi localhost la
 * chinh cai dien thoai, khong phai may dang chay server.
 */
const NETWORK_ERRORS = /load failed|failed to fetch|networkerror|network request failed/i

export function friendlyError(err: unknown): string {
  const raw = err instanceof Error ? err.message : String(err)
  if (!NETWORK_ERRORS.test(raw)) return raw

  const url = import.meta.env.VITE_SUPABASE_URL ?? '(chưa cấu hình)'
  const isLocalhost = /^https?:\/\/(localhost|127\.0\.0\.1)/i.test(url)

  return isLocalhost
    ? `Không gọi được ${url}. Địa chỉ này là "localhost" nên chỉ chạy được trên ` +
        `chính máy dựng server — trên điện thoại phải đổi VITE_SUPABASE_URL sang ` +
        `địa chỉ IP của máy đó (hoặc tên miền thật) rồi khởi động lại.`
    : `Không gọi được ${url}. Kiểm tra máy này có mạng, và địa chỉ đó có đúng không.`
}
