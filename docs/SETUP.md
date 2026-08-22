# Cài đặt từ đầu

Trình tự: Supabase → deploy trang quản trị → build APK → cài lên TV → ghép hai
bên bằng mã.

Không có bước tạo tài khoản: quyền truy cập đi bằng token, cấp qua việc ghép mã.

---

## 1. Supabase

1. Tạo project mới ở [supabase.com](https://supabase.com) (bản miễn phí là đủ).
2. Vào **SQL Editor**, chạy **lần lượt** ba file, đúng thứ tự:

   | Thứ tự | File                     | Tạo ra                                     |
   |--------|--------------------------|--------------------------------------------|
   | 1      | `supabase/01_schema.sql` | Bảng, index, extension, hàm sinh token/mã  |
   | 2      | `supabase/02_rls.sql`    | Hàm đọc token từ header + RLS + GRANT      |
   | 3      | `supabase/03_rpc.sql`    | RPC ghép máy, đọc dữ liệu, sửa dữ liệu     |

   Thứ tự quan trọng: `02` cấp quyền trên bảng của `01`, và `03` chứa GRANT cho
   các hàm nên phải chạy sau cùng.

3. Vào **Project Settings → API**, ghi lại hai giá trị:
   - **Project URL** → `https://xxxxxxxxxxxx.supabase.co`
   - **anon public key** → chuỗi `eyJ...` dài

Khoá `anon` là khoá công khai và **tự nó không mở được gì**: mọi truy vấn còn
phải mang header `X-YouTube-Token` mới thấy dữ liệu. Nên nhúng nó vào APK và vào
bundle trang admin là an toàn.

Không cần bật/tắt gì trong **Authentication** — phần đó không dùng.

---

## 2. Trang quản trị

### Cấu hình

```bash
cd admin
cp .env.example .env
npm install
```

`.env` chỉ cần hai dòng:

```
VITE_SUPABASE_URL=https://xxxxxxxxxxxx.supabase.co
VITE_SUPABASE_ANON_KEY=eyJhbGciOi...
```

Không cần khoá YouTube API — tiêu đề, tên kênh, thumbnail lấy qua endpoint oEmbed
công khai; thời lượng thì app TV tự điền sau lần phát đầu tiên.

### Chạy thử ở máy

```bash
npm run dev          # http://localhost:5173
npm run dev -- --host   # thêm địa chỉ LAN để mở từ điện thoại
```

> Mở từ điện thoại thì `VITE_SUPABASE_URL` **không được là** `localhost` — trên
> điện thoại `localhost` là chính cái điện thoại. Dùng URL Supabase thật, hoặc IP
> LAN nếu đang test server ở máy.

### Deploy lên host miễn phí

Đây là site tĩnh nên host miễn phí nào cũng chạy. **Nên deploy thật thay vì chạy
LAN**, vì hai lý do:

- HTTPS là điều kiện bắt buộc để service worker đăng ký được, mà không có service
  worker thì Android không cho trang xuất hiện trong menu **Chia sẻ** của app
  YouTube. Chạy `http://192.168.x.x` là mất tính năng đó.
- Điện thoại dùng được cả khi ra khỏi nhà.

Các biến `VITE_*` được nhúng vào bundle **lúc build**, nên cách gọn nhất là build
ở máy rồi đẩy `dist/` lên — không phải khai báo biến môi trường ở host.

```bash
cd admin && npm run build      # -> dist/
```

**Cách 1 — Netlify Drop (đơn giản nhất: không tài khoản, không CLI)**

Mở [app.netlify.com/drop](https://app.netlify.com/drop) rồi **kéo thả thư mục
`dist/`** vào. Xong ngay, có HTTPS và một địa chỉ `*.netlify.app`. Đăng nhập sau
nếu muốn giữ site lâu dài và đặt tên đẹp hơn.

**Cách 2 — Cloudflare Pages**

```bash
npx wrangler pages deploy dist --project-name youtube-admin
```

Lần đầu nó mở trình duyệt để đăng nhập. Ra địa chỉ `https://youtube-admin.pages.dev`.

**Cách 3 — Vercel**

```bash
npx vercel --prod dist
```

Repo có sẵn `netlify.toml`, `vercel.json` và `public/_headers` để cả ba host đặt
đúng `Content-Type` cho manifest và APK, và không cache `sw.js`.

Deploy lại sau khi sửa: `npm run build` rồi lặp lại bước trên (Netlify Drop thì
kéo thả `dist/` lần nữa vào cùng site).

### Cài vào điện thoại để nhận Chia sẻ từ app YouTube

Trang quản trị là một PWA. Trên **Android**, mở địa chỉ đã deploy bằng Chrome rồi
chọn **Thêm vào Màn hình chính**. Sau đó nó xuất hiện trong menu **Chia sẻ** của
app YouTube: đang xem video nào thì bấm Chia sẻ → chọn *YT Admin* → link tự vào ô
thêm, chỉ cần bấm Thêm.

Trên **iPhone**, iOS Safari không hỗ trợ Web Share Target nên trang không hiện
trong menu Chia sẻ. Thay vào đó: trong app YouTube bấm **Chia sẻ → Sao chép liên
kết**, mở trang quản trị rồi bấm **Dán từ clipboard**.

Trên **máy tính**: dán link, hoặc dùng ô **Tìm trên YouTube** (nó mở YouTube ở
tab mới để bạn chọn video rồi copy link về).

---

## 3. Build app Android TV

### Cấu hình

Mở `tv/local.properties` (file này không được commit) và thêm:

```properties
SUPABASE_URL=https://xxxxxxxxxxxx.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOi...

# Không bắt buộc. Điền thì màn hình Kết nối trên TV nhắc đúng địa chỉ này,
# đỡ phải đọc thuộc. Dùng địa chỉ đã deploy ở bước 2.
ADMIN_URL=youtube-admin.pages.dev
```

### Tạo keystore

Bản release chạy mượt hơn bản debug rõ rệt (R8 tối ưu, Compose bỏ chế độ debug)
và nhỏ hơn nhiều — khoảng 1,6 MB so với 14 MB. Nó cần chữ ký:

```bash
keytool -genkeypair -v \
  -keystore ~/youtube-release.jks \
  -alias youtube -keyalg RSA -keysize 2048 -validity 10000
```

Rồi thêm vào `tv/local.properties`:

```properties
YOUTUBE_KEYSTORE=/Users/<ban>/youtube-release.jks
YOUTUBE_KEYSTORE_PASSWORD=<mat khau store>
YOUTUBE_KEY_ALIAS=youtube
YOUTUBE_KEY_PASSWORD=<mat khau key>
```

Giữ file `.jks` này lại. Mất nó thì lần sau build ra APK khác chữ ký, muốn cài
phải xoá app cũ trên TV trước — và xoá app là mất `tv_token`, phải ghép lại.

### Build

```bash
cd tv
./gradlew :app:assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

Chưa khai keystore thì task vẫn chạy nhưng APK không được ký nên không cài được.
Lúc đó dùng `./gradlew :app:assembleDebug` để thử nhanh.

---

## 4. Cài app lên TV

### Cách đơn giản nhất: để TV tự tải APK về

Không cần dây, không cần USB, không cần ADB.

**Bước 1 — cho APK vào chỗ TV tải được.** Copy APK vào `admin/public/` rồi build
và deploy lại trang admin:

```bash
cp tv/app/build/outputs/apk/release/app-release.apk admin/public/youtube-tv.apk
cd admin && npm run build      # rồi deploy lại như mục 2
```

APK giờ tải được ở `https://<domain-cua-ban>/youtube-tv.apk`, khoảng 1,6 MB nên
TV tải vài giây.

**Bước 2 — trên TV, cài app "Downloader".** Mở Google Play trên TV, tìm
**Downloader** (biểu tượng quả cầu màu cam, của AFTVnews), cài vào.

**Bước 3 — cho phép cài từ nguồn khác.**
**Cài đặt → Bảo mật và hạn chế → Nguồn không xác định** → bật cho *Downloader*.

**Bước 4 — tải và cài.** Mở Downloader, nhập địa chỉ APK ở bước 1 rồi bấm **Go**.
Tải xong nó tự hỏi cài — bấm **Install**.

> Để APK công khai như vậy có sao không: bên trong chỉ có URL Supabase và khoá
> `anon`, mà khoá đó tự nó không mở được gì (phải có token — xem mục Bảo mật
> trong [README](../README.md)). Ai tải về cũng chỉ được một app chưa ghép,
> trắng trơn. Muốn kín hơn thì đặt tên file khó đoán.

### Cách khác: USB

Copy APK vào thẻ USB, cắm vào TV, dùng một app quản lý tệp trên TV (ví dụ
*X-plore*, *Solid Explorer*) mở file đó rồi cài. Vẫn cần bật **Nguồn không xác
định** cho app quản lý tệp đó.

### Cách khác: ADB qua mạng

Cần máy tính cùng mạng Wi-Fi với TV.

1. Trên TV: **Cài đặt → Tuỳ chọn thiết bị → Giới thiệu** → bấm **Bản dựng** 7
   lần để mở Tuỳ chọn nhà phát triển.
2. **Tuỳ chọn nhà phát triển** → bật **Gỡ lỗi qua mạng** (hoặc *Gỡ lỗi USB*).
3. Xem IP của TV ở **Cài đặt → Mạng → Trạng thái mạng**.

```bash
adb connect <IP_CUA_TV>:5555     # TV sẽ hỏi cho phép, bấm đồng ý trên TV
adb install -r tv/app/build/outputs/apk/release/app-release.apk
```

App xuất hiện ở hàng ứng dụng trên màn hình chính của TV, nhờ banner và
`LEANBACK_LAUNCHER` trong manifest.

---

## 5. Ghép TV với trang quản trị

1. Mở app trên TV. Trang chủ hiện "Chưa kết nối" — đó là bình thường.
2. Chọn **Kết nối** ở menu bên trái (mục có dấu đỏ). Màn hình hiện mã 6 ký tự.
3. Mở trang quản trị, nhập mã đó.
4. TV tự chuyển sang trang chủ.

Mã có hiệu lực 15 phút và **tự đổi mã mới khi hết hạn**, nên cứ để màn hình Kết
nối mở, không cần bấm lại.

### Thêm TV thứ hai vào cùng kho

Trên trang quản trị, tab **TV đã ghép** → **+ Ghép thêm TV** → nhập mã của TV
mới. TV đó dùng chung kho video, nhưng tiến độ xem ("Xem tiếp") vẫn tính riêng
từng máy.

### Quản trị từ máy thứ hai, hoặc sau khi xoá dữ liệu trình duyệt

`admin_token` nằm trong `localStorage` của từng trình duyệt, nên máy mới chưa có
quyền. Lấy quyền như sau:

1. Trên TV, chọn **Kết nối**. Vì TV đã ghép rồi nên nó hiện *Đã kết nối* kèm nút
   **Lấy mã để thêm máy quản trị**.
2. Bấm nút đó → TV hiện mã 6 ký tự.
3. Mở trang quản trị trên máy mới, nhập mã.

Máy mới nhận đúng token của kho đang dùng — không tạo kho mới, không mất video
nào.

### Ngắt một TV

Tab **TV đã ghép** → **Ngắt** ở dòng thiết bị. TV đó hiện lại màn hình nhập mã
ngay lần mở app sau. Video trong kho không bị xoá.

---

## 6. Điều khiển bằng remote

| Phím                | Tác dụng                                          |
|---------------------|---------------------------------------------------|
| D-pad ◀ ▲ ▼ ▶       | Di chuyển giữa các thẻ và các hàng                |
| OK                  | Mở video / tạm dừng / phát tiếp                   |
| ◀ ▶ (khi đang phát) | Tua lùi / tua tiến 10 giây                        |
| ▲ ▼ (khi đang phát) | Hiện lại thanh điều khiển                         |
| Quay lại            | Thoát trình phát, hoặc từ mục khác về Trang chủ   |
| Esc (bàn phím rời)  | Giống Quay lại                                    |

Ở màn Tìm kiếm, phím ◀ trả con trỏ về thanh điều hướng bên trái (ô nhập chữ không
giữ phím này để di chuyển con trỏ chữ — trên TV không ai sửa chữ bằng D-pad). Ở
Trang chủ, Quay lại thoát app như mọi app TV khác.

Hết video sẽ tự phát video tiếp theo trong cùng hàng.

---

## Xử lý sự cố

**Trang quản trị báo "Không gọi được http://localhost:8088"** — `localhost` chỉ
đúng trên chính máy dựng server. Đổi `VITE_SUPABASE_URL` sang URL Supabase thật
(hoặc IP LAN nếu đang test), rồi **khởi động lại** `npm run dev` — Vite chỉ đọc
`.env` lúc khởi động.

**Android không thấy trang trong menu Chia sẻ** — cần ba điều: trang chạy trên
**HTTPS**, đã **Thêm vào Màn hình chính**, và service worker đăng ký được. Chạy
qua `http://192.168.x.x` thì không đủ điều kiện.

**TV hiện "Chưa cấu hình Supabase"** — `local.properties` thiếu `SUPABASE_URL`
hoặc `SUPABASE_ANON_KEY`, hoặc đã thêm nhưng chưa build lại. Hai giá trị này
nhúng vào APK lúc build, không đọc lúc chạy.

**Trang quản trị báo "Mã không đúng"** — bộ ký tự sinh mã là
`23456789ABCDEFGHJKLMNPQRSTVWXYZ`: đã bỏ `0 1 I O U` để không nhìn lẫn, nên ký tự
tròn bạn thấy trên TV chỉ có thể là **Q**, **D** hoặc **G**. Gõ đúng mà vẫn sai
thì mã đã hết hạn — nhìn lại mã đang hiện trên TV.

**Mở trang quản trị trên máy mới thì nó bắt nhập mã** — đúng như thiết kế, mỗi
trình duyệt phải được cấp quyền một lần. Lấy mã ở TV: **Kết nối → Lấy mã để thêm
máy quản trị**.

**TV đã ghép rồi mà lại hiện "Chưa kết nối"** — thiết bị đã bị **Ngắt** từ trang
quản trị, hoặc app vừa bị xoá dữ liệu. Chọn Kết nối và ghép lại.

**Thẻ video trên TV không có badge thời lượng** — bình thường với video vừa thêm.
Phát nó một lần rồi quay lại trang chủ là có.

**Video có trong trang quản trị nhưng không thấy trên TV** — kiểm tra: video đang
bị *Ẩn*? Hàng chứa nó đang bị *Ẩn*? Hàng đó rỗng (hàng rỗng không hiện)? Hoặc app
TV đang giữ dữ liệu cũ — thoát app rồi mở lại.

**Một video báo "Chủ kênh không cho phép phát ngoài YouTube"** — video đó bị tắt
nhúng. Không có cách nào vượt; chọn video khác. Thường thì lúc thêm bằng link đã
bị báo trước rồi.

---

## Thử trên emulator (không cần TV)

```bash
sdkmanager "system-images;android-36;android-tv;arm64-v8a"
avdmanager create avd -n youtube_tv \
  -k "system-images;android-36;android-tv;arm64-v8a" -d tv_1080p
```

`avdmanager` tạo AVD với `hw.keyboard=no`, nghĩa là emulator **không** chuyển phím
từ bàn phím máy tính vào máy ảo — bấm mũi tên sẽ không có gì xảy ra. Sửa trong
`~/.android/avd/youtube_tv.avd/config.ini`:

```properties
hw.keyboard=yes
hw.initialOrientation=landscape
```

Rồi khởi động lại emulator. Sau đó ← ↑ ↓ → là D-pad, Enter là OK, Esc là Quay
lại. Cách chắc chắn hơn nếu bàn phím vẫn không ăn: bấm `⋯` (Extended controls) ở
thanh bên của emulator → **Directional pad**.

Emulator gọi máy host qua `10.0.2.2`, nên nếu chạy Supabase/proxy ở local thì đặt
`SUPABASE_URL=http://10.0.2.2:<cổng>`. Bản debug cho phép HTTP thường
(`app/src/debug/AndroidManifest.xml`), bản release thì chặn.
