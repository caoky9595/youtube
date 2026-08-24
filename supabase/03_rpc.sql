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

  -- Da ghep va khong ai doi ma thi tra ve luon trang thai.
  -- Xet tv_token chu khong xet library_id: thiet bi bi Ngat van giu library_id
  -- (de ghep lai tro ve dung kho cu) nhung phai duoc coi la CHUA ghep.
  if v_device.tv_token is not null and not p_force_code then
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
    'paired', v_device.tv_token is not null,
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

  -- Xet tv_token: thiet bi bi Ngat van con library_id nhung chua duoc ghep lai
  if v_device.tv_token is null or v_device.library_id is null then
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
                   'paired', d.tv_token is not null,
                   'paired_at', d.paired_at, 'last_seen_at', d.last_seen_at
                 ) order by d.paired_at nulls last, d.created_at
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

/**
 * Ngat mot TV khoi kho: thu hoi tv_token nen TV do mat quyen doc ngay, va hien
 * lai man hinh nhap ma.
 *
 * GIU NGUYEN library_id. Truoc day ham nay xoa ca library_id, hau qua la khi TV
 * ghep lai ma trang admin khong kem token (trinh duyet khac, hoac da "Quen kho")
 * thi pair_claim thay thiet bi khong thuoc kho nao va TAO KHO MOI rong. Kho cu
 * van con video nhung khong ai co token de vao, con admin thi them video vao kho
 * cu trong khi TV da sang kho moi.
 *
 * Giu library_id thi ghep lai la tro ve dung kho cu, khong mat gi.
 * Muon bo han thiet bi khoi kho thi dung forget_device().
 */
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
     set tv_token = null, paired_at = null
   where id = p_device_id and library_id = v_library_id;
  if not found then
    raise exception 'Thiết bị không thuộc kho này';
  end if;
end;
$$;

/**
 * Bo han mot thiet bi khoi kho. Khac unpair_device: sau khi goi ham nay, TV do
 * ghep lai se tao kho MOI chu khong tro ve kho cu. Dung khi thanh ly hoac cho
 * TV di.
 */
create or replace function public.forget_device(p_admin_token text, p_device_id uuid)
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

  delete from public.devices
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
grant execute on function public.forget_device(text, uuid)       to anon, authenticated;
grant execute on function public.tv_home(integer)                to anon, authenticated;
grant execute on function public.search_videos(text, integer)    to anon, authenticated;
grant execute on function public.save_progress(uuid, integer, integer) to anon, authenticated;
grant execute on function public.report_duration(uuid, integer)   to anon, authenticated;
grant execute on function public.create_shelf(text)              to anon, authenticated;
grant execute on function public.reorder_shelves(uuid[])         to anon, authenticated;
grant execute on function public.reorder_shelf_videos(uuid, uuid[]) to anon, authenticated;
grant execute on function public.add_video(text, text, text, text, uuid) to anon, authenticated;
