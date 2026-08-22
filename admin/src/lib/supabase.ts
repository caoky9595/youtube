import { createClient, type SupabaseClient } from '@supabase/supabase-js'

const url = import.meta.env.VITE_SUPABASE_URL
const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY

/**
 * Thieu cau hinh thi KHONG throw o day. Throw luc load module lam ca trang
 * trang tron, nguoi dung khong biet chuyen gi — App.tsx doc co nay va hien mot
 * man hinh huong dan thay the.
 */
export const isConfigured = Boolean(url && anonKey)

const TOKEN_KEY = 'youtube.adminToken'

/**
 * Quyen truy cap di bang header X-YouTube-Token, khong phai tai khoan. RLS ben
 * Postgres doc header nay de biet duoc phep doc/ghi kho nao.
 *
 * supabase-js chot global.headers ngay luc tao client, nen doi token thi phai
 * dung client moi — do la ly do co setAdminToken() ben duoi thay vi mot bien
 * header doc luc goi.
 */
function build(token: string | null): SupabaseClient {
  return createClient(url ?? 'http://unconfigured.invalid', anonKey ?? 'unconfigured', {
    auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
    global: { headers: token ? { 'X-YouTube-Token': token } : {} },
  })
}

let adminToken: string | null = localStorage.getItem(TOKEN_KEY)
let client = build(adminToken)

export function sb(): SupabaseClient {
  return client
}

export function getAdminToken(): string | null {
  return adminToken
}

export function setAdminToken(next: string | null) {
  adminToken = next
  if (next) localStorage.setItem(TOKEN_KEY, next)
  else localStorage.removeItem(TOKEN_KEY)
  client = build(adminToken)
}
