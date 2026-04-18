#!/usr/bin/env bash
# =============================================================================
# TehisAI CAD — Stripe hinnastruktuuri setup (2026)
# =============================================================================
#
# Loob 3 toodet (Maker, Pro, Team) ja 6 hinda (kuu + aasta igaühe kohta) üle
# Stripe API otse curl-iga. Ei vaja Stripe CLI-d — vaja ainult STRIPE_SECRET_KEY.
#
# KASUTUS:
#   export STRIPE_SECRET_KEY=sk_live_...           # või sk_test_... arenduses
#   bash scripts/setup-stripe-prices.sh
#
# OUTPUT: Kirjutab .env.stripe faili, kust saad kopeerida väärtused
# application.yml ENV-muutujatesse (STRIPE_PRICE_MAKER_MONTHLY jne).
#
# TURVALISUS:
#   - Kasuta TEST võtit kõigepealt (sk_test_...) — mängi läbi
#   - LIVE võtmega (sk_live_...) teeb päris tooteid sinu Stripe-kontosse
#   - Skript on idempotentne ainult ühekordne — ära jooksuta 2x sama võtmega,
#     muidu tekivad duplikaatid (Stripe ei lase samade nimedega aga loob "Maker (2)").
#   - Duplikaatide kustutamiseks: Stripe Dashboard → Products → Archive.
#
# HINNAD (sis. KM, EUR):
#   Maker — 12.99 €/kuu  või  129 €/a (−17%)
#   Pro   — 29.99 €/kuu  või  299 €/a (−17%)
#   Team  — 79 €/koht/kuu või  65 €/koht/kuu aastaplaanil (−18%)
# =============================================================================

set -euo pipefail

if [[ -z "${STRIPE_SECRET_KEY:-}" ]]; then
  echo "❌ STRIPE_SECRET_KEY pole seadistatud."
  echo ""
  echo "   Näide:"
  echo "     export STRIPE_SECRET_KEY=sk_test_... "
  echo "     bash $0"
  exit 1
fi

# Detect test vs live mode
if [[ "$STRIPE_SECRET_KEY" == sk_test_* ]]; then
  MODE="TEST"
elif [[ "$STRIPE_SECRET_KEY" == sk_live_* ]]; then
  MODE="LIVE"
else
  echo "⚠️  Võti ei alga sk_test_ ega sk_live_-ga — kas see on Stripe Secret Key?"
  exit 1
fi

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  TehisAI CAD — Stripe setup   [MODE: $MODE]                 ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

# ---- helper: loo toode ---------------------------------------------------------
create_product() {
  local name="$1"
  local description="$2"
  local id
  id=$(curl -sS https://api.stripe.com/v1/products \
    -u "$STRIPE_SECRET_KEY:" \
    -d "name=$name" \
    -d "description=$description" \
    -d "metadata[app]=tehisai-cad" \
    -d "metadata[tier]=$(echo "$name" | tr '[:upper:]' '[:lower:]')" \
    | grep -o '"id": *"prod_[^"]*"' | head -1 | sed 's/.*"prod_/prod_/;s/"$//')
  if [[ -z "$id" ]]; then
    echo "❌ Ei õnnestunud luua toodet: $name" >&2
    exit 1
  fi
  echo "$id"
}

# ---- helper: loo hind ----------------------------------------------------------
# Args: product_id, amount_cents, interval (month|year), nickname
create_price() {
  local product="$1"
  local amount="$2"
  local interval="$3"
  local nickname="$4"
  local id
  id=$(curl -sS https://api.stripe.com/v1/prices \
    -u "$STRIPE_SECRET_KEY:" \
    -d "product=$product" \
    -d "unit_amount=$amount" \
    -d "currency=eur" \
    -d "recurring[interval]=$interval" \
    -d "nickname=$nickname" \
    -d "tax_behavior=inclusive" \
    -d "metadata[cycle]=$interval" \
    | grep -o '"id": *"price_[^"]*"' | head -1 | sed 's/.*"price_/price_/;s/"$//')
  if [[ -z "$id" ]]; then
    echo "❌ Ei õnnestunud luua hinda: $nickname" >&2
    exit 1
  fi
  echo "$id"
}

echo "→ Loon tooted..."
MAKER_PROD=$(create_product "Maker" "TehisAI CAD Maker — 100 STL/kuu, kõik mallid, eelvaade, e-mail tugi")
echo "  ✓ Maker: $MAKER_PROD"
PRO_PROD=$(create_product   "Pro"   "TehisAI CAD Pro — piiramatu STL+STEP, Darwin CAD, freeform, API, kommertslitsents")
echo "  ✓ Pro:   $PRO_PROD"
TEAM_PROD=$(create_product  "Team"  "TehisAI CAD Team — koha-põhine, jagatud ajalugu, SSO, prioriteetne tugi")
echo "  ✓ Team:  $TEAM_PROD"
echo ""

echo "→ Loon hinnad (kuu + aasta)..."
# Maker: 12.99 €/kuu = 1299 c, 129 €/a = 12900 c
MAKER_M=$(create_price "$MAKER_PROD" 1299  month "Maker kuu (12.99 €)")
MAKER_Y=$(create_price "$MAKER_PROD" 12900 year  "Maker aasta (129 €)")
echo "  ✓ Maker kuu:   $MAKER_M"
echo "  ✓ Maker aasta: $MAKER_Y"

# Pro: 29.99 €/kuu = 2999 c, 299 €/a = 29900 c
PRO_M=$(create_price "$PRO_PROD" 2999  month "Pro kuu (29.99 €)")
PRO_Y=$(create_price "$PRO_PROD" 29900 year  "Pro aasta (299 €)")
echo "  ✓ Pro kuu:     $PRO_M"
echo "  ✓ Pro aasta:   $PRO_Y"

# Team: 79 €/koht/kuu = 7900 c, 65 €/koht/kuu aastas → 780 €/a/koht = 78000 c
TEAM_M=$(create_price "$TEAM_PROD" 7900  month "Team koht/kuu (79 €)")
TEAM_Y=$(create_price "$TEAM_PROD" 78000 year  "Team koht/aasta (780 €, −18%)")
echo "  ✓ Team kuu:    $TEAM_M"
echo "  ✓ Team aasta:  $TEAM_Y"
echo ""

# ---- kirjuta .env.stripe fail --------------------------------------------------
OUT=".env.stripe"
cat > "$OUT" <<EOF
# =============================================================================
# TehisAI CAD — Stripe Price ID-d [$MODE mode]
# Genereeritud: $(date -Iseconds)
# =============================================================================
# Kopeeri need application.yml ENV-muutujatesse või docker-compose.yml-i.

STRIPE_PRICE_MAKER_MONTHLY=$MAKER_M
STRIPE_PRICE_MAKER_YEARLY=$MAKER_Y
STRIPE_PRICE_PRO_MONTHLY=$PRO_M
STRIPE_PRICE_PRO_YEARLY=$PRO_Y
STRIPE_PRICE_TEAM_MONTHLY=$TEAM_M
STRIPE_PRICE_TEAM_YEARLY=$TEAM_Y

# Legacy alias — vana "hobi" URL'id saadavad nüüd kasutaja Maker-plaanile
STRIPE_PRICE_HOBI=$MAKER_M
STRIPE_PRICE_PRO=$PRO_M
EOF

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  ✅ VALMIS — kirjutati $OUT                           ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo "📋 JÄRGMINE SAMM:"
echo ""
echo "  1. Ava $OUT ja kopeeri väärtused oma .env (või docker-compose.yml)"
echo "  2. Restart backend:  docker compose restart backend"
echo "  3. Testi checkout'i:  klõpsa 'Uuenda Pro' nupul UI-s"
echo ""
echo "🔍 Kontrolli Stripe Dashboard'is:"
if [[ "$MODE" == "TEST" ]]; then
  echo "   https://dashboard.stripe.com/test/products"
else
  echo "   https://dashboard.stripe.com/products"
fi
echo ""
