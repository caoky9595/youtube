import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)

// Chrome doi co service worker moi coi trang la "cai duoc", va phai cai vao man
// hinh chinh thi Android moi cho trang xuat hien trong menu Chia se cua app
// YouTube. SW nay khong cache gi, xem public/sw.js.
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      // Khong dang ky duoc thi trang van chay binh thuong, chi mat Share Target
    })
  })
}
