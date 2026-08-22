-- YouTube — xac thuc bang token va Row Level Security
--
-- KHONG CO TAI KHOAN/MAT KHAU. Moi request mang theo header:
--
--     X-YouTube-Token: <admin_token hoac tv_token>
--
-- RLS doc header do de biet request thuoc kho nao va duoc lam gi:
--   - admin_token  -> doc VA ghi kho do
--   - tv_token     -> chi doc kho do (+ ghi tien do xem cua chinh thiet bi)
--   - khong token  -> khong thay gi ca
--
-- Nho vay khoa anon (nam trong bundle admin va trong APK) tu no khong mo duoc
-- gi. Deploy trang admin len server cong khai van an toan: khong co token thi
-- khong doc duoc kho nao.

set search_path = public, extensions;

-- ---------------------------------------------------------------------------
-- Doc token tu header cua request
-- ---------------------------------------------------------------------------

/** PostgREST dua header vao request.headers dang JSON, ten header viet thuong. */
create or replace function public.request_token()
returns text
language sql
stable
set search_path = public
as $$
  select nullif(
    coalesce(current_setting('request.headers', true)::json ->> 'x-youtube-token', ''),
    ''
  )
$$;

/**
 * Kho ma token hien tai duoc GHI. Chi admin_token thoa man.
 * SECURITY DEFINER de doc duoc bang libraries trong khi bang do khong mo cho anon.
 */
create or replace function public.writable_library()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select l.id from public.libraries l
   where l.admin_token = public.request_token()
$$;

/** Kho ma token hien tai duoc DOC: admin_token hoac tv_token. */
create or replace function public.readable_library()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(
    (select l.id from public.libraries l where l.admin_token = public.request_token()),
    (select d.library_id from public.devices d
      where d.tv_token = public.request_token() and d.library_id is not null)
  )
$$;

/** Thiet bi ung voi tv_token hien tai (dung cho watch_progress). */
create or replace function public.current_device()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select d.id from public.devices d where d.tv_token = public.request_token()
$$;

-- ---------------------------------------------------------------------------
-- Bat RLS. libraries / devices / pairing_codes khong mo policy nao cho anon:
-- moi thao tac voi chung di qua RPC SECURITY DEFINER o 03_rpc.sql.
-- ---------------------------------------------------------------------------
alter table public.libraries      enable row level security;
alter table public.devices        enable row level security;
alter table public.pairing_codes  enable row level security;
alter table public.shelves        enable row level security;
alter table public.videos         enable row level security;
alter table public.shelf_videos   enable row level security;
alter table public.watch_progress enable row level security;

-- --- shelves ---
drop policy if exists shelves_read   on public.shelves;
drop policy if exists shelves_insert on public.shelves;
drop policy if exists shelves_update on public.shelves;
drop policy if exists shelves_delete on public.shelves;

create policy shelves_read on public.shelves
  for select to anon, authenticated
  using (library_id = public.readable_library());

create policy shelves_insert on public.shelves
  for insert to anon, authenticated
  with check (library_id = public.writable_library());

create policy shelves_update on public.shelves
  for update to anon, authenticated
  using (library_id = public.writable_library())
  with check (library_id = public.writable_library());

create policy shelves_delete on public.shelves
  for delete to anon, authenticated
  using (library_id = public.writable_library());

-- --- videos ---
drop policy if exists videos_read   on public.videos;
drop policy if exists videos_insert on public.videos;
drop policy if exists videos_update on public.videos;
drop policy if exists videos_delete on public.videos;

create policy videos_read on public.videos
  for select to anon, authenticated
  using (library_id = public.readable_library());

create policy videos_insert on public.videos
  for insert to anon, authenticated
  with check (library_id = public.writable_library());

create policy videos_update on public.videos
  for update to anon, authenticated
  using (library_id = public.writable_library())
  with check (library_id = public.writable_library());

create policy videos_delete on public.videos
  for delete to anon, authenticated
  using (library_id = public.writable_library());

-- --- shelf_videos: khong co library_id rieng, suy ra qua shelf ---
drop policy if exists shelf_videos_read   on public.shelf_videos;
drop policy if exists shelf_videos_insert on public.shelf_videos;
drop policy if exists shelf_videos_update on public.shelf_videos;
drop policy if exists shelf_videos_delete on public.shelf_videos;

create policy shelf_videos_read on public.shelf_videos
  for select to anon, authenticated
  using (
    exists (
      select 1 from public.shelves s
       where s.id = shelf_id and s.library_id = public.readable_library()
    )
  );

create policy shelf_videos_insert on public.shelf_videos
  for insert to anon, authenticated
  with check (
    exists (
      select 1 from public.shelves s
       where s.id = shelf_id and s.library_id = public.writable_library()
    )
  );

create policy shelf_videos_update on public.shelf_videos
  for update to anon, authenticated
  using (
    exists (
      select 1 from public.shelves s
       where s.id = shelf_id and s.library_id = public.writable_library()
    )
  )
  with check (
    exists (
      select 1 from public.shelves s
       where s.id = shelf_id and s.library_id = public.writable_library()
    )
  );

create policy shelf_videos_delete on public.shelf_videos
  for delete to anon, authenticated
  using (
    exists (
      select 1 from public.shelves s
       where s.id = shelf_id and s.library_id = public.writable_library()
    )
  );

-- --- watch_progress: chi thiet bi cua chinh no ---
drop policy if exists watch_progress_own on public.watch_progress;
create policy watch_progress_own on public.watch_progress
  for all to anon, authenticated
  using (device_id = public.current_device())
  with check (device_id = public.current_device());

-- ---------------------------------------------------------------------------
-- GRANT. Chi mo cua o muc bang; RLS ben tren moi la thu quyet dinh.
-- libraries/devices/pairing_codes KHONG duoc grant: chi RPC cham vao chung.
-- ---------------------------------------------------------------------------
grant usage on schema public to anon, authenticated;

do $$
begin
  if exists (select 1 from pg_namespace where nspname = 'extensions') then
    grant usage on schema extensions to anon, authenticated;
  end if;
end $$;

grant select, insert, update, delete on public.shelves        to anon, authenticated;
grant select, insert, update, delete on public.videos         to anon, authenticated;
grant select, insert, update, delete on public.shelf_videos   to anon, authenticated;
grant select, insert, update, delete on public.watch_progress to anon, authenticated;

grant execute on function public.request_token()    to anon, authenticated;
grant execute on function public.readable_library() to anon, authenticated;
grant execute on function public.writable_library() to anon, authenticated;
grant execute on function public.current_device()   to anon, authenticated;
grant execute on function public.norm_text(text)              to anon, authenticated;
grant execute on function public.text_similarity(text, text)  to anon, authenticated;

-- GRANT cho cac RPC con lai nam o cuoi 03_rpc.sql, vi luc file nay chay thi
-- chung chua ton tai.
