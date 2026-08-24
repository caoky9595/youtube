-- =============================================================================
-- YouTube cho TV — TOAN BO schema trong mot file, de dan mot lan vao
-- Supabase Dashboard > SQL Editor > Run.
--
-- File nay la 01_schema.sql + 02_rls.sql + 03_rpc.sql noi lai. Neu sua ba file
-- kia thi sinh lai bang:
--     cd supabase && ./build-all.sh
-- =============================================================================


-- ═══════════════════════════════════════════════════════════════════════
-- 01_schema.sql
-- ═══════════════════════════════════════════════════════════════════════

-- YouTube — schema
-- Chay trong Supabase Dashboard > SQL Editor, theo thu tu 01 -> 02 -> 03.
--
-- Mo hinh: mot "kho" (library) chua toan bo video va cac hang. Moi app TV da
-- cai la mot "thiet bi" (device) tro vao dung mot kho. Khong co tai khoan/mat
-- khau: quyen truy cap di bang token, cap qua viec ghep ma OTP.

-- Supabase cai extension vao schema "extensions". DDL ben duoi phai thay duoc
-- operator class (gin_trgm_ops) va ham (unaccent) cua chung, nen search_path
-- phai bao gom ca "extensions". Postgres thuan khong co schema nay -> bo qua.
set search_path = public, extensions;

do $$
begin
  if exists (select 1 from pg_namespace where nspname = 'extensions') then
    create extension if not exists pgcrypto with schema extensions;
    create extension if not exists pg_trgm  with schema extensions;
    create extension if not exists unaccent with schema extensions;
  else
    create extension if not exists pgcrypto;
    create extension if not exists pg_trgm;
    create extension if not exists unaccent;
  end if;
end $$;

-- ---------------------------------------------------------------------------
-- Sinh token va ma ghep
-- ---------------------------------------------------------------------------

/** Token bi mat 32 byte, dang base64url — dung cho admin_token va tv_token. */
create or replace function public.new_token()
returns text
language sql
volatile
set search_path = public, extensions
as $$
  select replace(replace(encode(gen_random_bytes(32), 'base64'), '+', '-'), '/', '_')
$$;

/**
 * Ma ghep 6 ky tu. Bo O/0/I/1/U de nguoi doc tren TV khong nhin lan, va de
 * khong tinh cờ tao ra tu ngu khong hay.
 */
create or replace function public.new_pair_code()
returns text
language plpgsql
volatile
set search_path = public, extensions
as $$
declare
  alphabet constant text := '23456789ABCDEFGHJKLMNPQRSTVWXYZ';
  out text := '';
begin
  for _ in 1..6 loop
    out := out || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
  end loop;
  return out;
end;
$$;

-- ---------------------------------------------------------------------------
-- libraries: mot kho video. admin_token la chia khoa quan tri kho nay.
-- ---------------------------------------------------------------------------
create table if not exists public.libraries (
  id          uuid primary key default gen_random_uuid(),
  name        text not null default 'Kho video',
  admin_token text not null unique default public.new_token(),
  created_at  timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- devices: moi app TV da cai. install_id do app tu sinh va luu cuc bo; app
-- dung no de xin ma ghep va de hoi xem da duoc ghep chua.
-- library_id null = da xin ma nhung chua ai nhap ma do ben admin.
-- ---------------------------------------------------------------------------
create table if not exists public.devices (
  id           uuid primary key default gen_random_uuid(),
  install_id   text not null unique,
  name         text not null default 'Android TV',
  library_id   uuid references public.libraries(id) on delete cascade,
  tv_token     text unique,
  paired_at    timestamptz,
  last_seen_at timestamptz not null default now(),
  created_at   timestamptz not null default now()
);

create index if not exists devices_library_idx on public.devices (library_id);

-- ---------------------------------------------------------------------------
-- pairing_codes: ma OTP song ngan, tro ve mot device.
-- ---------------------------------------------------------------------------
create table if not exists public.pairing_codes (
  code       text primary key,
  device_id  uuid not null references public.devices(id) on delete cascade,
  expires_at timestamptz not null,
  claimed_at timestamptz,
  created_at timestamptz not null default now()
);

create index if not exists pairing_codes_device_idx on public.pairing_codes (device_id);
create index if not exists pairing_codes_expiry_idx on public.pairing_codes (expires_at);

-- ---------------------------------------------------------------------------
-- Chuan hoa chu de tim kiem khong dau
-- ---------------------------------------------------------------------------

-- SECURITY DEFINER co y: ham chay duoi quyen chu so huu nen khong phu thuoc
-- vao viec role goi co USAGE tren schema "extensions" hay khong. Ham thuan xu
-- ly chuoi, khong doc bang nao. search_path ghim cung de khong bi chiem quyen.
create or replace function public.norm_text(t text)
returns text
language sql
immutable
parallel safe
security definer
set search_path = public, extensions
as $$ select lower(unaccent('unaccent', coalesce(t, ''))) $$;

-- Boc similarity() cua pg_trgm cung ly do nhu tren.
create or replace function public.text_similarity(a text, b text)
returns real
language sql
immutable
parallel safe
security definer
set search_path = public, extensions
as $$ select similarity(coalesce(a, ''), coalesce(b, '')) $$;

-- ---------------------------------------------------------------------------
-- shelves: cac "hang" ngang tren trang chu TV
-- ---------------------------------------------------------------------------
create table if not exists public.shelves (
  id         uuid primary key default gen_random_uuid(),
  library_id uuid not null references public.libraries(id) on delete cascade,
  title      text not null,
  position   integer not null default 0,
  is_visible boolean not null default true,
  created_at timestamptz not null default now()
);

create index if not exists shelves_library_position_idx
  on public.shelves (library_id, position);

-- ---------------------------------------------------------------------------
-- videos: kho video da duyet. Trung youtube_id chi bi chan trong cung mot kho,
-- hai kho khac nhau van duoc phep chua cung mot video.
-- ---------------------------------------------------------------------------
create table if not exists public.videos (
  id               uuid primary key default gen_random_uuid(),
  library_id       uuid not null references public.libraries(id) on delete cascade,
  youtube_id       text not null,
  title            text not null,
  channel_title    text,
  thumbnail_url    text,
  -- NULL khi vua them bang duong dan: oEmbed khong tra ve thoi luong. App TV
  -- dien vao qua report_duration sau lan phat dau tien.
  duration_seconds integer,
  is_visible       boolean not null default true,
  added_at         timestamptz not null default now(),
  unique (library_id, youtube_id)
);

create index if not exists videos_library_added_idx
  on public.videos (library_id, added_at desc);
create index if not exists videos_title_trgm_idx
  on public.videos using gin (public.norm_text(title) gin_trgm_ops);
create index if not exists videos_channel_trgm_idx
  on public.videos using gin (public.norm_text(channel_title) gin_trgm_ops);

-- ---------------------------------------------------------------------------
-- shelf_videos: 1 video co the nam trong nhieu hang, thu tu rieng tung hang
-- ---------------------------------------------------------------------------
create table if not exists public.shelf_videos (
  shelf_id uuid not null references public.shelves(id) on delete cascade,
  video_id uuid not null references public.videos(id)  on delete cascade,
  position integer not null default 0,
  primary key (shelf_id, video_id)
);

create index if not exists shelf_videos_order_idx on public.shelf_videos (shelf_id, position);

-- ---------------------------------------------------------------------------
-- watch_progress: vi tri dang xem, theo tung thiet bi
-- ---------------------------------------------------------------------------
create table if not exists public.watch_progress (
  device_id        uuid not null references public.devices(id) on delete cascade,
  video_id         uuid not null references public.videos(id)  on delete cascade,
  position_seconds integer not null default 0,
  duration_seconds integer,
  updated_at       timestamptz not null default now(),
  primary key (device_id, video_id)
);

create index if not exists watch_progress_recent_idx
  on public.watch_progress (device_id, updated_at desc);

-- ═══════════════════════════════════════════════════════════════════════
-- 02_rls.sql
-- ═══════════════════════════════════════════════════════════════════════

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

-- ═══════════════════════════════════════════════════════════════════════
-- 03_rpc.sql
-- ═══════════════════════════════════════════════════════════════════════

-- YouTube — RPC
--
-- Nhom 1: ghep thiet bi (khong can token, day chinh la cach LAY token)
-- Nhom 2: doc du lieu cho app TV
-- Nhom 3: sua du lieu tu trang admin

set search_path = public, extensions;

-- ===========================================================================
-- NHOM 1 — GHEP THIET BI
-- ===========================================================================

/**
 * App TV goi khi chua duoc ghep. Tra ve ma 6 ky tu de hien tren man hinh.
 * Goi lai nhieu lan thi tra ve ma cu neu con hieu luc, khong sinh ma moi —
 * nguoi dung dang doc ma tren TV, doi ma giua duong la kho chiu.
 *
 * SECURITY DEFINER: phai ghi vao devices/pairing_codes, hai bang do khong mo
 * cho anon. Dau vao duy nhat la install_id do app tu sinh.
 */
create or replace function public.pair_request(
  p_install_id  text,
  p_device_name text default 'Android TV',
  -- Da ghep roi van xin ma: dung khi muon them mot may quan tri nua (dien
  -- thoai thu hai, hoac trinh duyet vua bi xoa du lieu). Ma do khi nhap se tra
  -- ve token cua DUNG kho dang dung, khong tao kho moi.
  p_force_code  boolean default false
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = public
as $$
declare
  v_device public.devices;
  v_code   public.pairing_codes;
begin
  if coalesce(trim(p_install_id), '') = '' then
    raise exception 'install_id không được để trống';
  end if;

  insert into public.devices (install_id, name)
  values (p_install_id, coalesce(nullif(trim(p_device_name), ''), 'Android TV'))
  on conflict (install_id) do update
    set name = coalesce(nullif(trim(excluded.name), ''), public.devices.name),
        last_seen_at = now()
  returning * into v_device;

  -- Da ghep va khong ai doi ma thi tra ve luon trang thai
  if v_device.library_id is not null and not p_force_code then
    return jsonb_build_object(
      'paired', true,
      'tv_token', v_device.tv_token,
      'library_id', v_device.library_id
    );
  end if;

  delete from public.pairing_codes
   where device_id = v_device.id and (expires_at <= now() or claimed_at is not null);

  select * into v_code from public.pairing_codes
   where device_id = v_device.id and expires_at > now() and claimed_at is null
   order by created_at desc limit 1;

  if v_code.code is null then
    -- Vong lap phong truong hop ma sinh ra trung voi ma dang song
    for _ in 1..10 loop
      begin
        insert into public.pairing_codes (code, device_id, expires_at)
        values (public.new_pair_code(), v_device.id, now() + interval '15 minutes')
        returning * into v_code;
        exit;
      exception when unique_violation then
        null;
      end;
    end loop;
    if v_code.code is null then
      raise exception 'Không sinh được mã ghép, thử lại';
    end if;
  end if;

  return jsonb_build_object(
    'paired', v_device.library_id is not null,
    'code', v_code.code,
    'expires_at', v_code.expires_at,
    -- So giay con lai, tinh o server. App TV phai dung so nay chu khong tu lay
    -- expires_at tru cho gio cua chinh no: dong ho TV lech la chuyen thuong
    -- (chua set mui gio, khong co mang luc boot). Dong ho cham thi dem nguoc
    -- sai, dong ho nhanh thi tuong ma het han va xin ma moi lien tuc.
    'expires_in', greatest(0, extract(epoch from (v_code.expires_at - now()))::int)
  );
end;
$$;

/**
 * App TV goi lien tuc trong luc hien ma, cho tra ve paired = true.
 */
create or replace function public.pair_poll(p_install_id text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = public
as $$
declare
  v_device public.devices;
begin
  update public.devices
     set last_seen_at = now()
   where install_id = p_install_id
  returning * into v_device;

  if v_device.id is null then
    return jsonb_build_object('paired', false, 'unknown_device', true);
  end if;

  if v_device.library_id is null then
    return jsonb_build_object('paired', false);
  end if;

  return jsonb_build_object(
    'paired', true,
    'tv_token', v_device.tv_token,
    'library_id', v_device.library_id
  );
end;
$$;

/**
 * Trang thai cua dung mot ma. App TV dung khi dang hien ma cho viec THEM may
 * quan tri (luc do thiet bi da ghep roi, nen khong the dua vao truong "paired"
 * de biet da xong hay chua).
 *
 * Khong doan theo thoi gian: hoi thang ve chinh ma dang hien tren man hinh.
 */
create or replace function public.pair_code_status(p_code text)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(
    (
      select jsonb_build_object(
               'exists', true,
               'claimed', c.claimed_at is not null,
               'expired', c.expires_at <= now()
             )
        from public.pairing_codes c
       where c.code = upper(trim(coalesce(p_code, '')))
    ),
    jsonb_build_object('exists', false, 'claimed', false, 'expired', true)
  )
$$;

/**
 * Trang admin goi khi nguoi dung nhap ma.
 *
 * Chua co admin_token  -> tao kho moi, tra ve admin_token cua kho do.
 * Da co admin_token    -> gan thiet bi vao dung kho dang quan tri, de mot kho
 *                         dung cho nhieu TV trong nha.
 */
create or replace function public.pair_claim(
  p_code        text,
  p_admin_token text default null
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = public
as $$
declare
  v_code    public.pairing_codes;
  v_device  public.devices;
  v_library public.libraries;
begin
  select * into v_code from public.pairing_codes
   where code = upper(trim(coalesce(p_code, '')));

  if v_code.code is null then
    raise exception 'Mã không đúng';
  end if;
  if v_code.claimed_at is not null then
    raise exception 'Mã này đã được dùng rồi';
  end if;
  if v_code.expires_at <= now() then
    raise exception 'Mã đã hết hạn, bấm tạo mã mới trên TV';
  end if;

  select * into v_device from public.devices where id = v_code.device_id;
  if v_device.id is null then
    raise exception 'Không tìm thấy thiết bị của mã này';
  end if;

  if coalesce(trim(p_admin_token), '') <> '' then
    -- Trang admin dang quan tri mot kho -> gan thiet bi vao kho do
    select * into v_library from public.libraries where admin_token = trim(p_admin_token);
    if v_library.id is null then
      raise exception 'Token quản trị không hợp lệ';
    end if;
  elsif v_device.library_id is not null then
    -- Thiet bi DA thuoc mot kho: tra ve token cua chinh kho do. Day la duong
    -- de them may quan tri thu hai, hoac lay lai quyen sau khi xoa du lieu
    -- trinh duyet. Tao kho moi o day se lam mat lien ket voi toan bo video cu.
    select * into v_library from public.libraries where id = v_device.library_id;
  else
    insert into public.libraries (name)
    values ('Kho của ' || v_device.name)
    returning * into v_library;
  end if;

  update public.devices
     set library_id = v_library.id,
         tv_token   = coalesce(tv_token, public.new_token()),
         paired_at  = now()
   where id = v_device.id
  returning * into v_device;

  update public.pairing_codes set claimed_at = now() where code = v_code.code;

  return jsonb_build_object(
    'admin_token',  v_library.admin_token,
    'library_id',   v_library.id,
    'library_name', v_library.name,
    'device_name',  v_device.name
  );
end;
$$;

/**
 * Trang admin goi de biet dang quan tri kho nao va co nhung TV nao trong do.
 * Cung dung de kiem tra token con hieu luc khi mo lai trang.
 */
create or replace function public.library_info(p_admin_token text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_library public.libraries;
begin
  select * into v_library from public.libraries
   where admin_token = trim(coalesce(p_admin_token, ''));
  if v_library.id is null then
    raise exception 'Token quản trị không hợp lệ';
  end if;

  return jsonb_build_object(
    'library_id', v_library.id,
    'library_name', v_library.name,
    'devices', coalesce(
      (
        select jsonb_agg(
                 jsonb_build_object(
                   'id', d.id, 'name', d.name,
                   'paired_at', d.paired_at, 'last_seen_at', d.last_seen_at
                 ) order by d.paired_at
               )
          from public.devices d where d.library_id = v_library.id
      ),
      '[]'::jsonb
    )
  );
end;
$$;

/** Doi ten kho. */
create or replace function public.rename_library(p_admin_token text, p_name text)
returns void
language plpgsql
volatile
security definer
set search_path = public
as $$
begin
  update public.libraries
     set name = coalesce(nullif(trim(p_name), ''), name)
   where admin_token = trim(coalesce(p_admin_token, ''));
  if not found then
    raise exception 'Token quản trị không hợp lệ';
  end if;
end;
$$;

/** Ngat mot TV khoi kho. TV do se hien lai man hinh nhap ma. */
create or replace function public.unpair_device(p_admin_token text, p_device_id uuid)
returns void
language plpgsql
volatile
security definer
set search_path = public
as $$
declare
  v_library_id uuid;
begin
  select id into v_library_id from public.libraries
   where admin_token = trim(coalesce(p_admin_token, ''));
  if v_library_id is null then
    raise exception 'Token quản trị không hợp lệ';
  end if;

  update public.devices
     set library_id = null, tv_token = null, paired_at = null
   where id = p_device_id and library_id = v_library_id;
  if not found then
    raise exception 'Thiết bị không thuộc kho này';
  end if;
end;
$$;

-- ===========================================================================
-- NHOM 2 — DU LIEU CHO APP TV
-- Cac ham nay la SECURITY INVOKER, nen RLS o 02_rls.sql tu loc theo token.
-- ===========================================================================

/**
 * Trang chu app TV: mot lan goi duy nhat. Tra ve cac hang theo thu tu, moi
 * hang kem video theo thu tu. Hang "Mới thêm" duoc sinh tu dong, dat dau tien.
 * Hang rong bi bo.
 */
create or replace function public.tv_home(p_recent_limit integer default 20)
returns jsonb
language sql
stable
security invoker
set search_path = public, extensions
as $$
  with recent as (
    select jsonb_build_object(
             'id', 'recent',
             'title', 'Mới thêm',
             'videos', coalesce(jsonb_agg(to_jsonb(v) order by v.added_at desc), '[]'::jsonb)
           ) as shelf
      from (
        select * from public.videos
         where is_visible
         order by added_at desc
         limit p_recent_limit
      ) v
  ),
  curated as (
    select coalesce(
             jsonb_agg(
               jsonb_build_object('id', s.id, 'title', s.title, 'videos', s.videos)
               order by s.position
             ),
             '[]'::jsonb
           ) as shelves
      from (
        select sh.id, sh.title, sh.position,
               coalesce(
                 jsonb_agg(to_jsonb(v) order by sv.position)
                   filter (where v.id is not null),
                 '[]'::jsonb
               ) as videos
          from public.shelves sh
          left join public.shelf_videos sv on sv.shelf_id = sh.id
          left join public.videos v on v.id = sv.video_id and v.is_visible
         where sh.is_visible
         group by sh.id, sh.title, sh.position
      ) s
     where jsonb_array_length(s.videos) > 0
  )
  select jsonb_build_object(
           'shelves',
           case when jsonb_array_length((select shelf->'videos' from recent)) > 0
                then jsonb_build_array((select shelf from recent))
                else '[]'::jsonb
           end || (select shelves from curated)
         );
$$;

/**
 * Tim kiem tren app TV. Bo dau va bo phan biet hoa thuong, nen "bai hat" khop
 * duoc "Bài Hát" va nguoc lai.
 */
create or replace function public.search_videos(p_query text, p_limit integer default 50)
returns setof public.videos
language sql
stable
security invoker
set search_path = public, extensions
as $$
  with q as (select public.norm_text(p_query) as n)
  select v.* from public.videos v, q
   where v.is_visible
     and q.n <> ''
     and (public.norm_text(v.title) like '%' || q.n || '%'
          or public.norm_text(v.channel_title) like '%' || q.n || '%')
   order by public.text_similarity(public.norm_text(v.title), q.n) desc, v.added_at desc
   limit p_limit;
$$;

/** App TV ghi vi tri dang xem. Thiet bi duoc suy ra tu tv_token. */
create or replace function public.save_progress(
  p_video_id         uuid,
  p_position_seconds integer,
  p_duration_seconds integer default null
)
returns void
language plpgsql
volatile
security invoker
set search_path = public
as $$
declare
  v_device uuid := public.current_device();
begin
  if v_device is null then
    raise exception 'Thiết bị chưa được ghép';
  end if;

  insert into public.watch_progress
    (device_id, video_id, position_seconds, duration_seconds, updated_at)
  values (v_device, p_video_id, p_position_seconds, p_duration_seconds, now())
  on conflict (device_id, video_id) do update
    set position_seconds = excluded.position_seconds,
        duration_seconds = coalesce(excluded.duration_seconds,
                                    public.watch_progress.duration_seconds),
        updated_at = now();
end;
$$;

-- ===========================================================================
-- NHOM 3 — SUA DU LIEU TU TRANG ADMIN
-- SECURITY INVOKER: RLS chi cho qua khi header mang admin_token.
-- ===========================================================================

/**
 * Upsert mot video vao kho dang quan tri, roi (tuy chon) gan vao cuoi mot hang.
 * Kho duoc suy ra tu token trong header, khong nhan tu tham so — de trang admin
 * khong the ghi lan sang kho khac du co truyen sai.
 */
create or replace function public.add_video(
  p_youtube_id    text,
  p_title         text,
  p_channel_title text default null,
  p_thumbnail_url text default null,
  p_shelf_id      uuid default null
)
returns public.videos
language plpgsql
volatile
security invoker
set search_path = public
as $$
declare
  v_library uuid := public.writable_library();
  v_video   public.videos;
  v_next    integer;
begin
  if v_library is null then
    raise exception 'Thiếu hoặc sai token quản trị';
  end if;

  insert into public.videos as v (
    library_id, youtube_id, title, channel_title, thumbnail_url
  ) values (
    v_library, p_youtube_id, p_title, p_channel_title, p_thumbnail_url
  )
  on conflict (library_id, youtube_id) do update set
    -- Them lai video da co: cap nhat metadata, giu nguyen added_at.
    -- duration_seconds KHONG nam trong danh sach nay: chi report_duration (app
    -- TV goi sau khi phat) duoc quyen dien cot do, nen them lai cung mot link
    -- khong the xoa mat thoi luong da biet.
    title         = excluded.title,
    channel_title = coalesce(excluded.channel_title, v.channel_title),
    thumbnail_url = coalesce(excluded.thumbnail_url, v.thumbnail_url),
    is_visible    = true
  returning * into v_video;

  if p_shelf_id is not null then
    select coalesce(max(position), -1) + 1 into v_next
      from public.shelf_videos where shelf_id = p_shelf_id;

    insert into public.shelf_videos (shelf_id, video_id, position)
    values (p_shelf_id, v_video.id, v_next)
    on conflict (shelf_id, video_id) do nothing;
  end if;

  return v_video;
end;
$$;

/** Tao hang moi o cuoi danh sach, trong kho dang quan tri. */
create or replace function public.create_shelf(p_title text)
returns public.shelves
language plpgsql
volatile
security invoker
set search_path = public
as $$
declare
  v_library uuid := public.writable_library();
  v_shelf   public.shelves;
begin
  if v_library is null then
    raise exception 'Thiếu hoặc sai token quản trị';
  end if;

  insert into public.shelves (library_id, title, position)
  select v_library,
         coalesce(nullif(trim(p_title), ''), 'Hàng mới'),
         coalesce(max(position), -1) + 1
    from public.shelves where library_id = v_library
  returning * into v_shelf;

  return v_shelf;
end;
$$;

/** Nhan mang video_id theo thu tu moi, ghi lai position trong hang. */
create or replace function public.reorder_shelf_videos(p_shelf_id uuid, p_video_ids uuid[])
returns void
language plpgsql
volatile
security invoker
set search_path = public
as $$
begin
  update public.shelf_videos sv
     set position = idx.ord - 1
    from unnest(p_video_ids) with ordinality as idx(video_id, ord)
   where sv.shelf_id = p_shelf_id
     and sv.video_id = idx.video_id;
end;
$$;

/** Nhan mang shelf_id theo thu tu moi. */
create or replace function public.reorder_shelves(p_shelf_ids uuid[])
returns void
language plpgsql
volatile
security invoker
set search_path = public
as $$
begin
  update public.shelves s
     set position = idx.ord - 1
    from unnest(p_shelf_ids) with ordinality as idx(shelf_id, ord)
   where s.id = idx.shelf_id;
end;
$$;

/**
 * App TV bao lai thoi luong that cua video sau khi phat.
 *
 * Ly do ton tai: them video bang cach dan duong dan thi metadata lay qua oEmbed
 * (khong can khoa API), ma oEmbed khong tra ve thoi luong. Trinh phat IFrame
 * thi biet chinh xac, nen de no dien vao. Sau lan xem dau tien la the video co
 * badge thoi luong va thanh tien do chay dung.
 *
 * SECURITY DEFINER vi tv_token khong co quyen UPDATE bang videos (policy
 * videos_update doi admin_token). Bu lai ham nay bi rang buoc chat:
 *   - chi sua video nam trong dung kho ma token duoc doc
 *   - chi dien khi duration_seconds con NULL, khong ghi de gia tri co san
 * Nen thiet hai toi da neu token bi lam dung la mot con so thoi luong sai o
 * mot video chua co thoi luong.
 */
create or replace function public.report_duration(
  p_video_id         uuid,
  p_duration_seconds integer
)
returns void
language plpgsql
volatile
security definer
set search_path = public
as $$
declare
  v_library uuid := public.readable_library();
begin
  if v_library is null or coalesce(p_duration_seconds, 0) <= 0 then
    return;
  end if;

  update public.videos
     set duration_seconds = p_duration_seconds
   where id = p_video_id
     and library_id = v_library
     and duration_seconds is null;
end;
$$;

-- ---------------------------------------------------------------------------
-- GRANT EXECUTE. Phai o cuoi file nay: chay o 02_rls.sql se loi "function
-- does not exist" vi luc do chua tao.
-- ---------------------------------------------------------------------------
grant execute on function public.new_token()            to anon, authenticated;
grant execute on function public.new_pair_code()        to anon, authenticated;
grant execute on function public.pair_request(text, text, boolean) to anon, authenticated;
grant execute on function public.pair_poll(text)                 to anon, authenticated;
grant execute on function public.pair_code_status(text)          to anon, authenticated;
grant execute on function public.pair_claim(text, text)          to anon, authenticated;
grant execute on function public.library_info(text)              to anon, authenticated;
grant execute on function public.rename_library(text, text)      to anon, authenticated;
grant execute on function public.unpair_device(text, uuid)       to anon, authenticated;
grant execute on function public.tv_home(integer)                to anon, authenticated;
grant execute on function public.search_videos(text, integer)    to anon, authenticated;
grant execute on function public.save_progress(uuid, integer, integer) to anon, authenticated;
grant execute on function public.report_duration(uuid, integer)   to anon, authenticated;
grant execute on function public.create_shelf(text)              to anon, authenticated;
grant execute on function public.reorder_shelves(uuid[])         to anon, authenticated;
grant execute on function public.reorder_shelf_videos(uuid, uuid[]) to anon, authenticated;
grant execute on function public.add_video(text, text, text, text, uuid) to anon, authenticated;
