-- =============================================================================
-- XOÁ SẠCH DỮ LIỆU — bắt đầu lại từ đầu
--
-- ⚠️  Mất toàn bộ: kho, video, hàng, thiết bị đã ghép, tiến độ xem.
--     Cấu trúc bảng và các hàm KHÔNG bị ảnh hưởng.
--
-- Sau khi chạy file này: mọi TV đã ghép sẽ hiện lại màn hình nhập mã, và mọi
-- trình duyệt đang quản trị sẽ tự về màn nhập mã (token cũ không còn hợp lệ).
--
-- Chạy `all.sql` TRƯỚC nếu bạn vừa cập nhật code (để có bản hàm mới nhất),
-- rồi mới chạy file này.
-- =============================================================================

-- Mot cau TRUNCATE cho tat ca bang de khong vuong khoa ngoai giua chung.
truncate table
  public.watch_progress,
  public.shelf_videos,
  public.videos,
  public.shelves,
  public.pairing_codes,
  public.devices,
  public.libraries;

-- Kiem lai: tat ca phai bang 0
select 'libraries' as bang, count(*) as con_lai from public.libraries
union all select 'devices',        count(*) from public.devices
union all select 'pairing_codes',  count(*) from public.pairing_codes
union all select 'shelves',        count(*) from public.shelves
union all select 'videos',         count(*) from public.videos
union all select 'shelf_videos',   count(*) from public.shelf_videos
union all select 'watch_progress', count(*) from public.watch_progress
order by bang;
