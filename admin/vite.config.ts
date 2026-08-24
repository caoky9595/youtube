import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

/**
 * Ten bien co the gap, theo thu tu uu tien.
 *
 * Ly do phai co danh sach nay: integration Supabase tren Vercel Marketplace tu
 * khai bien vao project, nhung dat ten KHONG co tien to VITE_ — ma Vite chi dua
 * bien VITE_* vao bundle. Neu chi doc VITE_SUPABASE_URL thi dung integration se
 * ra site trong tron.
 */
const URL_NAMES = ['VITE_SUPABASE_URL', 'SUPABASE_URL', 'NEXT_PUBLIC_SUPABASE_URL']
const ANON_NAMES = [
  'VITE_SUPABASE_ANON_KEY',
  'SUPABASE_ANON_KEY',
  'NEXT_PUBLIC_SUPABASE_ANON_KEY',
  // Supabase dang chuyen sang cap khoa moi: publishable (cong khai) va secret.
  // Integration khai ca hai dang, nen doc them de project moi cung chay.
  'SUPABASE_PUBLISHABLE_KEY',
  'NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY',
]

/**
 * KHONG dung envPrefix: ['SUPABASE_'] cho tien.
 *
 * Integration cua Vercel khai ca SUPABASE_SERVICE_ROLE_KEY va SUPABASE_JWT_SECRET
 * canh anon key. Noi tien to se nhung ca hai cai do vao bundle JS cong khai —
 * service_role bo qua toan bo RLS, nghia la bat ky ai mo devtools cung xoa duoc
 * sach du lieu. Nen o day map tuong minh dung hai bien can dung.
 */

/** Doc payload cua JWT ma khong xac thuc chu ky — chi de biet claim "role". */
function jwtRole(token: string): string | null {
  const parts = token.split('.')
  if (parts.length !== 3) return null
  try {
    const pad = parts[1].length % 4 === 0 ? '' : '='.repeat(4 - (parts[1].length % 4))
    const json = Buffer.from(parts[1].replace(/-/g, '+').replace(/_/g, '/') + pad, 'base64')
    return (JSON.parse(json.toString('utf8')) as { role?: string }).role ?? null
  } catch {
    return null
  }
}

/**
 * Chan truong hop dan sai khoa. Khoa duoc nhung vao bundle cong khai, nen dan
 * service_role vao day la mo cua toan bo du lieu — tha build do con hon.
 */
function assertPublicKey(key: string, sourceName: string) {
  if (!key) return

  if (key.startsWith('sb_secret_')) {
    throw new Error(
      `${sourceName} dang la khoa BI MAT (sb_secret_...). Khoa nay se bi nhung vao ` +
        `bundle JS cong khai. Dung khoa "publishable"/"anon" thay vao.`,
    )
  }

  const role = jwtRole(key)
  if (role && role !== 'anon') {
    throw new Error(
      `${sourceName} la JWT co role="${role}", khong phai "anon". ` +
        `role=service_role bo qua toan bo RLS — khong duoc nhung vao bundle cong khai.`,
    )
  }
}

export default defineConfig(({ mode }) => {
  // Tien to '' de loadEnv doc ca file .env lan bien cua process (Vercel/CI)
  const env = loadEnv(mode, process.cwd(), '')
  const pick = (names: string[]) => {
    for (const name of names) {
      const value = env[name]?.trim()
      if (value) return { name, value }
    }
    return { name: names[0], value: '' }
  }

  const url = pick(URL_NAMES)
  const anon = pick(ANON_NAMES)
  assertPublicKey(anon.value, anon.name)

  if (url.value && url.name !== 'VITE_SUPABASE_URL') {
    console.log(`  [config] dung ${url.name} va ${anon.name} (Vercel/Supabase integration)`)
  }

  return {
    plugins: [react(), tailwindcss()],
    define: {
      'import.meta.env.VITE_SUPABASE_URL': JSON.stringify(url.value),
      'import.meta.env.VITE_SUPABASE_ANON_KEY': JSON.stringify(anon.value),
    },
  }
})
