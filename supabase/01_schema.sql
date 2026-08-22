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
