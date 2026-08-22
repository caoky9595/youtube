/**
 * Service worker toi gian.
 *
 * Ly do ton tai: Chrome doi trang phai co service worker moi coi la "cai duoc"
 * (installable), va phai cai vao man hinh chinh thi Android moi cho trang xuat
 * hien trong menu Chia se cua app YouTube (Web Share Target).
 *
 * Khong cache gi ca: danh sach video phai luon lay moi tu Supabase, cache o day
 * chi gay ra chuyen hien du lieu cu.
 */
self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()))
self.addEventListener('fetch', () => {
  // Khong goi respondWith -> trinh duyet xu ly request nhu binh thuong
})
