# YouTube

Một "YouTube riêng" cho TV: app Android TV giao diện giống app YouTube trên TV,
nhưng **chỉ hiện các video do bạn thêm vào**. Kèm một trang web quản trị.

TV và trang quản trị nối với nhau bằng **mã 6 ký tự**, không có tài khoản/mật
khẩu: app TV hiện mã, bạn nhập mã đó vào trang quản trị, thế là xong.

```
┌──────────────────┐                          ┌──────────────────┐
│  tv/             │   ① hiện mã "KGDR2P"     │  admin/          │
│  App Android TV  │ ───────────────────────► │  Web quản trị    │
│  Kotlin/Compose  │                          │  React + Vite    │
└────────┬─────────┘   ② nhập mã              └────────┬─────────┘
         │                                             │
    tv_token (chỉ đọc)                        admin_token (đọc+ghi)
         │                                             │
         └──────────► ┌──────────────────┐ ◄───────────┘
                      │  Supabase        │
                      │  Postgres + RLS  │  RLS đọc header X-YouTube-Token
                      └──────────────────┘  để biết kho nào, quyền gì
```

## Các phần

| Thư mục     | Là gì                                                                  |
|-------------|------------------------------------------------------------------------|
| `supabase/` | 3 file SQL: schema, token + RLS, và các RPC. Chạy trong SQL Editor.    |
| `admin/`    | Web quản trị (React 19 + Vite + Tailwind 4). Deploy đâu cũng được.     |
| `tv/`       | App Android TV (Kotlin, Compose for TV). Sideload APK lên Sony Bravia. |
| `docs/`     | Hướng dẫn cài đặt chi tiết → [docs/SETUP.md](docs/SETUP.md).           |

## Mô hình dữ liệu

Một **kho** (`libraries`) chứa toàn bộ video và các hàng. Mỗi app TV đã cài là
một **thiết bị** (`devices`) trỏ vào đúng một kho. Nhiều TV trong nhà ghép được
vào cùng một kho — chỉ cần trang admin đang quản trị kho đó rồi nhập mã của TV
mới.

## Cách ghép máy

1. Trên TV, chọn **Kết nối** ở menu bên trái. App hiện mã 6 ký tự, hiệu lực 15
   phút, **hết hạn thì tự đổi mã mới** nên cứ để màn hình đó bao lâu cũng được.
2. Mở trang quản trị, nhập mã. Trang admin nhận `admin_token`, TV nhận
   `tv_token`.
3. TV tự chuyển sang trang chủ, không cần bấm gì thêm.

Chưa ghép thì Trang chủ hiện gợi ý, không hiện mã — mã chỉ xuất hiện khi bạn
chủ động chọn **Kết nối**.

**Thêm máy quản trị thứ hai.** `admin_token` nằm trong `localStorage` của từng
trình duyệt, nên điện thoại thứ hai (hay trình duyệt vừa bị xoá dữ liệu) chưa có
quyền. Trên TV: **Kết nối → Lấy mã để thêm máy quản trị** → nhập mã đó ở máy
mới. Máy mới nhận token của **đúng kho đang dùng**, không tạo kho mới.

## Cách thêm video

Ba đường, tuỳ thiết bị:

| Thiết bị | Cách chọn video |
|---|---|
| Android | Trong app YouTube bấm **Chia sẻ** → chọn trang quản trị (PWA Share Target) |
| iPhone  | **Chia sẻ → Sao chép liên kết**, rồi **Dán từ clipboard** trong trang |
| PC      | Ô **Tìm trên YouTube** mở YouTube ở tab mới → copy link → dán vào |

Ô dán nhận nhiều link một lúc — cả `youtube.com/watch`, `youtu.be`, `/shorts`
và ID 11 ký tự.

Tiêu đề, tên kênh và thumbnail lấy tự động qua endpoint **oEmbed công khai** của
YouTube, **không cần khoá API nào**. Đổi lại là không có ô tìm kiếm YouTube ngay
trong trang admin: mở YouTube ở tab khác rồi copy link sang.

oEmbed không trả về thời luợng, nên thẻ vừa thêm chưa có badge thời lượng. App
TV biết chính xác lúc phát nên tự báo về server (`report_duration`) — từ lần xem
thứ hai là thẻ có badge và thanh tiến độ chạy đúng. RPC đó chỉ điền khi ô còn
trống và chỉ trong kho mà token được đọc, không ghi đè giá trị có sẵn.

Link nào YouTube từ chối trả thông tin (video đã xoá, hoặc chủ kênh tắt cho phép
nhúng) bị bỏ qua kèm thông báo ngay lúc thêm — đỡ phải lên TV mới biết không
phát được.

## Bảo mật

Quyền truy cập đi bằng header `X-YouTube-Token`, RLS trong Postgres đọc header
đó. Cụ thể:

| Token         | Đọc kho | Ghi kho | Ghi tiến độ xem |
|---------------|:-------:|:-------:|:---------------:|
| `admin_token` |    ✅   |    ✅   |        —        |
| `tv_token`    |    ✅   |    ❌   | ✅ (của chính nó)|
| không có      |    ❌   |    ❌   |        ❌        |

Nghĩa là khoá `anon` của Supabase (nằm trong bundle trang admin và trong APK)
tự nó **không mở được gì**. Bảng `libraries`/`devices` không mở cho `anon` chút
nào, nên không ai gom được token từ đó. Vì vậy deploy trang admin lên server
công khai vẫn an toàn: không có token thì không thấy kho nào.

## Đang chạy ở đâu

- Trang quản trị: **https://youtube-admin-kappa.vercel.app**
- Mã nguồn: **https://github.com/caoky9595/youtube**

## Chạy nhanh

```bash
# 1. Supabase: chạy lần lượt supabase/01_schema.sql, 02_rls.sql, 03_rpc.sql

# 2. Trang quản trị — build rồi kéo thả dist/ vào app.netlify.com/drop
cd admin && cp .env.example .env    # điền 2 biến Supabase
npm install && npm run build

# 3. App TV — điền SUPABASE_URL/ANON_KEY/ADMIN_URL vào tv/local.properties
cd ../tv && ./gradlew :app:assembleRelease      # -> ~1,6 MB

# 4. Cho APK vào chỗ TV tải được, rồi deploy lại trang admin
cp app/build/outputs/apk/release/app-release.apk ../admin/public/youtube-tv.apk
cd ../admin && npm run build
```

Trên TV: cài app **Downloader** từ Play Store, nhập
`https://<domain>/youtube-tv.apk`, bấm Go. Không cần dây, USB hay ADB.

Nên deploy lên HTTPS thật thay vì chạy LAN: service worker chỉ đăng ký được trên
secure context, mà không có nó thì Android không cho trang xuất hiện trong menu
**Chia sẻ** của app YouTube.

Chi tiết từng bước, kể cả tạo keystore và các cách cài khác:
[docs/SETUP.md](docs/SETUP.md).

## Giới hạn cần biết

- **Phải phát qua YouTube IFrame Player API.** Không tải/lưu video về — vừa
  trái điều khoản YouTube vừa không cần thiết. Nghĩa là TV phải có mạng khi
  xem, và video nào chủ kênh chặn nhúng thì không phát được (app báo lỗi rõ).
- **Không cần khoá API nào cả** — chỉ hai biến Supabase. Đổi lại là trang admin
  không hiện được lưới kết quả tìm kiếm của YouTube: youtube.com không cho đọc
  từ trang khác (không có CORS), không cho nhúng iframe
  (`x-frame-options: SAMEORIGIN`), và popup thì same-origin policy chặn đọc DOM.
  Nên ô "Tìm trên YouTube" mở YouTube ở tab mới, bạn chọn video rồi copy link về.
- **Tên và logo hiển thị là "YouTube"** theo yêu cầu; tên repo/package vẫn là
  `youtube` / `com.youtube.tv`. Cả tên lẫn logo đó là nhãn hiệu của Google: dùng
  riêng trong nhà thì không vấn đề gì, nhưng đừng đưa lên Play Store.
