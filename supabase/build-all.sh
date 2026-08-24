#!/bin/bash
# Sinh lai all.sql tu ba file goc. Chay sau moi lan sua 01/02/03.
set -e
cd "$(dirname "$0")"
{
  cat <<'HEADER'
-- =============================================================================
-- YouTube cho TV — TOAN BO schema trong mot file, de dan mot lan vao
-- Supabase Dashboard > SQL Editor > Run.
--
-- File nay la 01_schema.sql + 02_rls.sql + 03_rpc.sql noi lai. Neu sua ba file
-- kia thi sinh lai bang:
--     cd supabase && ./build-all.sh
-- =============================================================================

HEADER
  for f in 01_schema.sql 02_rls.sql 03_rpc.sql; do
    echo ""
    echo "-- ═══════════════════════════════════════════════════════════════════════"
    echo "-- $f"
    echo "-- ═══════════════════════════════════════════════════════════════════════"
    echo ""
    cat "$f"
  done
} > all.sql
echo "all.sql: $(wc -l < all.sql) dong"
