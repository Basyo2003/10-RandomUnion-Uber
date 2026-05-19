#!/usr/bin/env bash
# ============================================================
#  User-Service End-to-End Test Suite
#  Tests all user-service endpoints against the K8s cluster
#  via the API Gateway.
#
#  Covers: Auth, User CRUD, SavedAddress CRUD, S1-F1..F12, MS2/MS3
#    S1-F1:  Search users (GET /api/users/search)
#    S1-F2:  Update preferences (PUT /{id}/preferences — JSONB merge)
#    S1-F3:  Ride summary (GET /{id}/ride-summary — Feign→ride-service)
#    S1-F4:  Deactivate user (PUT /{id}/deactivate — Feign→ride-service)
#    S1-F5:  Filter by preference (GET /preferences/search)
#    S1-F6:  Top riders by spending (GET /reports/top-riders — Feign→payment-service)
#    S1-F7:  Set default address (PUT /{userId}/addresses/{addressId}/default)
#    S1-F8:  User profile with addresses (GET /{id}/profile)
#    S1-F9:  Language + min rides filter (GET /preferences/language — Feign→ride-service)
#    S1-F10: Register (POST /api/auth/register — MS2 BCrypt, 201)
#    S1-F11: Login (POST /api/auth/login — MS2 JWT, 200)
#    S1-F12: Activity feed (GET /{id}/activity — MS2 MongoDB, ownership check)
#    CC-2:   Role management (PUT /{id}/role)
#
#  Known implementation notes:
#    - POST /api/users (createUser): does NOT BCrypt password — test-only CRUD path
#    - PUT /api/users/{id}/role: no ADMIN-only check in code → any auth user → 200
#    - GET /api/users/{id}/profile: no ownership check → any auth user → 200
#    - PUT /api/users/{id}/preferences: no ownership check → any auth user → 200
#    - SavedAddressService.setDefaultSavedAddress: wrong-user address → 404 (not 400)
#    - S1-F3/F4/F9: Feign→ride-service may return 503 if ride-service unavailable
#    - S1-F6: Feign→payment-service per user; silently skips failed Feign; returns []
#    - S1-F12: MongoDB required; events appear after login/register actions
#
#  Prerequisites:
#    - minikube running (all pods Ready in namespace: uber)
#    - minikube tunnel running (for Windows/Docker-driver setups)
#    - bash >= 4, curl
#
#  Usage:
#    chmod +x user-service-tests.sh && bash user-service-tests.sh
#
#  Override gateway URL (use tunnel URL on Windows):
#    BASE_URL=http://127.0.0.1:<tunnel-port> bash user-service-tests.sh
# ============================================================

set -uo pipefail

# ── Colours ──────────────────────────────────────────────────
GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

PASS=0; FAIL=0; SKIP=0
declare -a RESULTS=()

pass() { PASS=$((PASS+1)); RESULTS+=("${GREEN}  PASS${NC}  $1"); echo -e "${GREEN}  PASS${NC}  $1"; }
fail() { FAIL=$((FAIL+1)); RESULTS+=("${RED}  FAIL${NC}  $1 — $2"); echo -e "${RED}  FAIL${NC}  $1 — $2"; }
skip() { SKIP=$((SKIP+1)); RESULTS+=("${YELLOW}  SKIP${NC}  $1 — $2"); echo -e "${YELLOW}  SKIP${NC}  $1 — $2"; }

# check <name> <expected-http-code> [curl-args…]
check() {
  local name="$1" expected="$2"; shift 2
  local code body
  code=$(curl -s -o /tmp/_uts_body -w "%{http_code}" "$@" 2>/dev/null) || code="000"
  body=$(cat /tmp/_uts_body 2>/dev/null || echo "")
  if [[ "$code" == "$expected" ]]; then
    pass "$name"
  else
    fail "$name" "expected HTTP $expected, got $code — ${body:0:160}"
  fi
}

# ── JSON helpers (no jq required) ────────────────────────────
json_num() { printf '%s' "$1" | grep -oP "\"$2\"\s*:\s*\K[0-9]+" | head -1; }
json_str() { printf '%s' "$1" | grep -oP "\"$2\"\s*:\s*\"\K[^\"]+" | head -1; }

decode_jwt_uid() {
  local payload; payload=$(printf '%s' "$1" | cut -d. -f2 | tr -- '-_' '+/')
  local mod4=$(( ${#payload} % 4 ))
  [[ $mod4 -eq 2 ]] && payload="${payload}=="
  [[ $mod4 -eq 3 ]] && payload="${payload}="
  printf '%s' "$payload" | base64 -d 2>/dev/null | grep -oP '"uid"\s*:\s*\K[0-9]+' | head -1
}

phase() { echo -e "\n${CYAN}${BOLD}─── $1 ───${NC}"; }

# =============================================================
#  0. Bootstrap
# =============================================================
echo -e "\n${CYAN}${BOLD}══════════════════════════════════════════════════════════"
echo "  User-Service E2E Test Suite  —  $(date)"
echo -e "══════════════════════════════════════════════════════════${NC}"

if [[ -n "${BASE_URL:-}" ]]; then
  BASE="$BASE_URL"
else
  # Try minikube service tunnel URL first (works on Windows with Docker driver)
  BASE=$(minikube service api-gateway -n uber --url 2>/dev/null | head -1 || echo "")
  if [[ -z "$BASE" ]]; then
    MINIKUBE_IP=$(minikube ip 2>/dev/null) || {
      echo -e "${RED}FATAL: 'minikube ip' failed. Is Minikube running?${NC}"
      echo -e "${YELLOW}TIP: run with: BASE_URL=http://127.0.0.1:<port> bash user-service-tests.sh${NC}"
      exit 1
    }
    BASE="http://${MINIKUBE_IP}:30080"
  fi
fi
echo "  Gateway: $BASE"

GW_CODE=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 "$BASE/actuator/health" 2>/dev/null || echo "000")
if [[ "$GW_CODE" == "200" ]]; then
  echo -e "${GREEN}  Gateway reachable (HTTP $GW_CODE)${NC}"
else
  echo -e "${RED}  ERROR: Gateway not reachable (HTTP $GW_CODE)${NC}"
  echo -e "${YELLOW}  TIP: BASE_URL=http://127.0.0.1:<tunnel-port> bash user-service-tests.sh${NC}"
  exit 1
fi

phase "Phase 0: Auth setup (register + login)"

# registerUser always sets role=RIDER regardless of body — ADMIN must be promoted via DB.
auth_setup() {
  local email="$1" pass="$2" name="$3" phone="$4"
  curl -s -X POST "$BASE/api/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"$pass\",\"name\":\"$name\",\"phone\":\"$phone\"}" \
    -o /dev/null
  local resp
  resp=$(curl -s -X POST "$BASE/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"$pass\"}")
  local token; token=$(json_str "$resp" "token")
  if [[ -z "${token:-}" ]]; then
    echo -e "${RED}FATAL: no token for $email — response: ${resp:0:200}${NC}" >&2; exit 1
  fi
  echo "$token"   # ← only stdout output: the JWT itself
}

# admin_setup: register → promote role to ADMIN in DB via kubectl → re-login for fresh JWT
# ALL diagnostic output goes to stderr (&2) — only the token is printed to stdout
# so that ADMIN_TOKEN=$(admin_setup ...) captures only the JWT string.
admin_setup() {
  local email="$1" pass="$2" name="$3" phone="$4"
  # Register (creates RIDER role — registerUser ignores role field)
  curl -s -X POST "$BASE/api/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"$pass\",\"name\":\"$name\",\"phone\":\"$phone\"}" \
    -o /dev/null
  # Login to get initial token and decode user ID
  local resp; resp=$(curl -s -X POST "$BASE/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"$pass\"}")
  local token; token=$(json_str "$resp" "token")
  if [[ -z "${token:-}" ]]; then
    echo -e "${RED}FATAL: no token for $email${NC}" >&2; exit 1
  fi
  local uid; uid=$(decode_jwt_uid "$token")
  # Promote to ADMIN directly in user-postgres via kubectl exec
  # user-postgres: postgres:17, user=postgres, db=uberdb-users, label=app=user-postgres
  local pg_pod
  pg_pod=$(kubectl get pods -n uber -l app=user-postgres \
    -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
  if [[ -n "${pg_pod:-}" && -n "${uid:-}" ]]; then
    kubectl exec -n uber "$pg_pod" -- \
      psql -U postgres -d uberdb-users -c \
      "UPDATE users SET role='ADMIN' WHERE id=${uid};" >/dev/null 2>&1 \
      && echo "  Promoted user $uid to ADMIN via kubectl (pod: $pg_pod)" >&2
    # Re-login: AuthController reads role from DB → new JWT will carry role=ADMIN
    resp=$(curl -s -X POST "$BASE/api/auth/login" \
      -H 'Content-Type: application/json' \
      -d "{\"email\":\"$email\",\"password\":\"$pass\"}")
    token=$(json_str "$resp" "token")
    if [[ -z "${token:-}" ]]; then
      echo -e "${RED}FATAL: re-login failed for $email after DB promotion${NC}" >&2; exit 1
    fi
  else
    echo -e "${YELLOW}  WARNING: user-postgres pod not found — ADMIN token has RIDER role; ownership-bypass tests will skip${NC}" >&2
  fi
  echo "$token"   # ← only stdout output: the JWT itself
}

RIDER_TOKEN=$(auth_setup "uts_rider@test.com"   "Test1234!" "UTS Rider"   "+15560201")
RIDER_ID=$(decode_jwt_uid "$RIDER_TOKEN")
echo "  Rider  ID=$RIDER_ID  ✓"

ADMIN_TOKEN=$(admin_setup "uts_admin@test.com"  "Test1234!" "UTS Admin"   "+15560202")
ADMIN_ID=$(decode_jwt_uid "$ADMIN_TOKEN")
# Verify ADMIN role was applied
_ADMIN_ROLE=$(printf '%s' "$ADMIN_TOKEN" | cut -d. -f2 | tr -- '-_' '+/' | \
  (read p; mod4=$(( ${#p} % 4 )); [[ $mod4 -eq 2 ]] && p="${p}=="; [[ $mod4 -eq 3 ]] && p="${p}="; \
   printf '%s' "$p") | base64 -d 2>/dev/null | grep -oP '"role"\s*:\s*"\K[^"]+' | head -1 || echo "UNKNOWN")
echo "  Admin  ID=$ADMIN_ID  role=$_ADMIN_ROLE  ✓"
if [[ "$_ADMIN_ROLE" != "ADMIN" ]]; then
  echo -e "${YELLOW}  WARNING: Admin JWT has role=$_ADMIN_ROLE (not ADMIN) — kubectl promotion may have failed${NC}"
  echo -e "${YELLOW}  Tests relying on ADMIN ownership bypass will be marked SKIP${NC}"
  ADMIN_IS_REAL=false
else
  ADMIN_IS_REAL=true
fi

RIDER2_TOKEN=$(auth_setup "uts_rider2@test.com" "Test1234!" "UTS Rider2"  "+15560203")
RIDER2_ID=$(decode_jwt_uid "$RIDER2_TOKEN")
echo "  Rider2 ID=$RIDER2_ID  ✓"

if [[ -z "${RIDER_ID:-}" || -z "${ADMIN_ID:-}" ]]; then
  echo -e "${RED}FATAL: Could not decode user IDs from JWT. Aborting.${NC}"; exit 1
fi

TODAY=$(date +%Y-%m-%d 2>/dev/null || echo "2026-05-19")
PAST=$(date -d "60 days ago" +%Y-%m-%d 2>/dev/null \
  || date -v-60d +%Y-%m-%d 2>/dev/null \
  || echo "2026-03-20")
FUTURE="2027-12-31"

# Unique suffix per run — prevents unique-constraint failures on rerun
RUN_ID=$(date +%s 2>/dev/null || echo "0")

ADDRESS_ID=""

# =============================================================
#  1. Auth & Security
# =============================================================
phase "Phase 1: Auth & Security"

check "GET /api/users without token → 401" \
  401 "$BASE/api/users"

check "GET /api/users with bad token → 401" \
  401 "$BASE/api/users" -H "Authorization: Bearer not.a.valid.jwt"

check "POST /api/users without token → 401" \
  401 -X POST "$BASE/api/users" \
  -H 'Content-Type: application/json' \
  -d '{"name":"X","email":"x@x.com","password":"x","phone":"+1"}'

check "GET /api/addresses without token → 401" \
  401 "$BASE/api/addresses"

check "GET /api/users (authenticated RIDER → 200)" \
  200 "$BASE/api/users" -H "Authorization: Bearer $RIDER_TOKEN"

# Public endpoints: /api/auth/** — no token needed
# register with existing email returns 409 (serves as public-endpoint check)
check "POST /api/auth/register (public, duplicate → 409)" \
  409 -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"name":"X","email":"uts_rider@test.com","password":"Test1234!","phone":"+19991001"}'

check "POST /api/auth/login (public → 200)" \
  200 -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"uts_rider@test.com","password":"Test1234!"}'

# =============================================================
#  2. User CRUD
#  NOTE: POST /api/users (createUser) does NOT BCrypt passwords.
#  The /api/auth/register path does BCrypt. For CRUD testing we use
#  createUser to avoid needing to login as the throwaway user.
# =============================================================
phase "Phase 2: User CRUD"

# POST — create throwaway user (raw password, no BCrypt)
CRUD_CODE=$(curl -s -o /tmp/_uts_body -w "%{http_code}" \
  -X POST "$BASE/api/users" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $RIDER_TOKEN" \
  -d "{\"name\":\"UTS CRUD User\",\"email\":\"uts_crud_${RUN_ID}@test.com\",
       \"password\":\"raw_pass\",\"phone\":\"+1556${RUN_ID: -4}\",
       \"role\":\"RIDER\",\"status\":\"ACTIVE\"}" 2>/dev/null)
CRUD_BODY=$(cat /tmp/_uts_body)
CRUD_USER_ID=$(json_num "$CRUD_BODY" "id")

if [[ "$CRUD_CODE" == "200" || "$CRUD_CODE" == "201" ]]; then
  pass "POST /api/users (create → HTTP $CRUD_CODE)"
  echo "  CRUD user ID=$CRUD_USER_ID"
else
  fail "POST /api/users (create)" "expected 200, got $CRUD_CODE — ${CRUD_BODY:0:160}"
fi

# Duplicate email → DataIntegrityViolationException → 400 (GlobalExceptionHandler)
check "POST /api/users (duplicate email → 400)" \
  400 -X POST "$BASE/api/users" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"name\":\"Dup\",\"email\":\"uts_crud_${RUN_ID}@test.com\",
       \"password\":\"x\",\"phone\":\"+1999${RUN_ID: -4}\",
       \"role\":\"RIDER\",\"status\":\"ACTIVE\"}"

check "GET /api/users (list all → 200)" \
  200 "$BASE/api/users" -H "Authorization: Bearer $RIDER_TOKEN"

check "GET /api/users/$RIDER_ID (own → 200)" \
  200 "$BASE/api/users/$RIDER_ID" -H "Authorization: Bearer $RIDER_TOKEN"

# Other RIDER reading another RIDER's record → 403 (checkSelfOrAdmin)
if [[ -n "${RIDER2_ID:-}" ]]; then
  check "GET /api/users/$RIDER2_ID (rider1 token → 403)" \
    403 "$BASE/api/users/$RIDER2_ID" -H "Authorization: Bearer $RIDER_TOKEN"

  # ADMIN bypasses ownership → 200 (only if kubectl promotion succeeded)
  if [[ "$ADMIN_IS_REAL" == "true" ]]; then
    check "GET /api/users/$RIDER2_ID (admin token → 200)" \
      200 "$BASE/api/users/$RIDER2_ID" -H "Authorization: Bearer $ADMIN_TOKEN"
  else
    skip "GET /api/users/$RIDER2_ID (admin token → 200)" \
      "ADMIN role not set — kubectl promotion failed; RIDER token would return 403"
  fi
fi

# 9999999 not found: ADMIN bypasses ownership and hits user lookup → 404
# RIDER token: ownership check fires first (requesterId!=9999999) → 403 (not 404)
if [[ "$ADMIN_IS_REAL" == "true" ]]; then
  check "GET /api/users/9999999 (not found → 404)" \
    404 "$BASE/api/users/9999999" -H "Authorization: Bearer $ADMIN_TOKEN"
else
  skip "GET /api/users/9999999 (not found → 404)" \
    "ADMIN role required to bypass ownership; RIDER gets 403 before 404"
fi

# PUT — update own user (must supply all fields; updateUser overwrites entirely)
check "PUT /api/users/$RIDER_ID (update own → 200)" \
  200 -X PUT "$BASE/api/users/$RIDER_ID" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $RIDER_TOKEN" \
  -d "{\"name\":\"UTS Rider Updated\",\"email\":\"uts_rider@test.com\",
       \"password\":\"Test1234!\",\"phone\":\"+15560201\",
       \"role\":\"RIDER\",\"status\":\"ACTIVE\",\"preferences\":{}}"

# PUT other user with RIDER token → 403 (checkSelfOrAdmin)
if [[ -n "${RIDER2_ID:-}" ]]; then
  check "PUT /api/users/$RIDER2_ID (rider token on other → 403)" \
    403 -X PUT "$BASE/api/users/$RIDER2_ID" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $RIDER_TOKEN" \
    -d "{\"name\":\"Should Fail\",\"email\":\"uts_rider2@test.com\",
         \"password\":\"Test1234!\",\"phone\":\"+15560203\",
         \"role\":\"RIDER\",\"status\":\"ACTIVE\"}"
fi

# DELETE — throwaway CRUD user; deleteUser returns String body → 200 (not 204)
# Requires ADMIN role to delete a user that isn't the calling user
if [[ -n "${CRUD_USER_ID:-}" ]]; then
  if [[ "$ADMIN_IS_REAL" == "true" ]]; then
    DEL_CODE=$(curl -s -o /tmp/_uts_body -w "%{http_code}" \
      -X DELETE "$BASE/api/users/$CRUD_USER_ID" \
      -H "Authorization: Bearer $ADMIN_TOKEN" 2>/dev/null)
    if [[ "$DEL_CODE" == "200" || "$DEL_CODE" == "204" ]]; then
      pass "DELETE /api/users/$CRUD_USER_ID (admin → HTTP $DEL_CODE)"
      check "GET /api/users/$CRUD_USER_ID after delete (→ 404)" \
        404 "$BASE/api/users/$CRUD_USER_ID" -H "Authorization: Bearer $ADMIN_TOKEN"
    else
      DEL_BODY=$(cat /tmp/_uts_body)
      fail "DELETE /api/users/$CRUD_USER_ID" "expected 200, got $DEL_CODE — ${DEL_BODY:0:160}"
      skip "GET /api/users/$CRUD_USER_ID after delete" "DELETE did not succeed"
    fi
  else
    skip "DELETE /api/users/$CRUD_USER_ID (admin)" \
      "ADMIN role required to delete another user's account"
    skip "GET /api/users/$CRUD_USER_ID after delete" "DELETE was skipped"
  fi
else
  skip "DELETE /api/users/{id}" "CRUD user creation failed"
fi

# DELETE own (RIDER deletes self) — create a fresh register user for this
DEL_SELF_RESP=$(curl -s -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"UTS Self Del\",\"email\":\"uts_sdel_${RUN_ID}@test.com\",
       \"password\":\"Test1234!\",\"phone\":\"+1557${RUN_ID: -4}\"}")
DEL_SELF_TOKEN=$(json_str "$DEL_SELF_RESP" "token")
DEL_SELF_ID=$(decode_jwt_uid "$DEL_SELF_TOKEN")

if [[ -n "${DEL_SELF_ID:-}" && -n "${DEL_SELF_TOKEN:-}" ]]; then
  DEL_SELF_CODE=$(curl -s -o /tmp/_uts_body -w "%{http_code}" \
    -X DELETE "$BASE/api/users/$DEL_SELF_ID" \
    -H "Authorization: Bearer $DEL_SELF_TOKEN" 2>/dev/null)
  if [[ "$DEL_SELF_CODE" == "200" || "$DEL_SELF_CODE" == "204" ]]; then
    pass "DELETE /api/users/$DEL_SELF_ID (self delete → HTTP $DEL_SELF_CODE)"
  else
    DEL_SELF_BODY=$(cat /tmp/_uts_body)
    fail "DELETE /api/users/$DEL_SELF_ID (self delete)" \
      "expected 200, got $DEL_SELF_CODE — ${DEL_SELF_BODY:0:160}"
  fi
else
  skip "DELETE /api/users/{id} (self delete)" "Could not register throwaway user"
fi

# =============================================================
#  3. SavedAddress CRUD
#  Endpoints: POST /api/addresses/user/{userId}
#             POST /api/users/{userId}/addresses  (alternate)
#             GET  /api/addresses
#             GET  /api/addresses/{id}
#             PUT  /api/addresses/{id}
#             DELETE /api/addresses/{id}
#             GET  /api/users/{userId}/addresses
#             DELETE /api/users/{userId}/addresses/{addressId}
# =============================================================
phase "Phase 3: SavedAddress CRUD"

# POST /api/addresses/user/{userId} — primary create endpoint
ADDR_RESP=$(curl -s -X POST "$BASE/api/addresses/user/$RIDER_ID" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $RIDER_TOKEN" \
  -d '{"label":"Home","address":"123 Main St, Cairo",
       "latitude":30.044,"longitude":31.235,
       "isDefault":false,"metadata":{"notes":"front door"}}')
ADDRESS_ID=$(json_num "$ADDR_RESP" "id")

if [[ -n "${ADDRESS_ID:-}" ]]; then
  pass "POST /api/addresses/user/$RIDER_ID (create → 200)"
  echo "  Address ID=$ADDRESS_ID"
else
  fail "POST /api/addresses/user/$RIDER_ID (create)" "no id in response — ${ADDR_RESP:0:160}"
fi

check "GET /api/addresses (list all → 200)" \
  200 "$BASE/api/addresses" -H "Authorization: Bearer $RIDER_TOKEN"

if [[ -n "${ADDRESS_ID:-}" ]]; then
  check "GET /api/addresses/$ADDRESS_ID (by ID → 200)" \
    200 "$BASE/api/addresses/$ADDRESS_ID" -H "Authorization: Bearer $RIDER_TOKEN"

  check "GET /api/addresses/9999999 (not found → 404)" \
    404 "$BASE/api/addresses/9999999" -H "Authorization: Bearer $RIDER_TOKEN"

  check "PUT /api/addresses/$ADDRESS_ID (update → 200)" \
    200 -X PUT "$BASE/api/addresses/$ADDRESS_ID" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $RIDER_TOKEN" \
    -d '{"label":"Home Updated","address":"456 Main St, Cairo",
         "latitude":30.055,"longitude":31.245,
         "isDefault":false,"metadata":{"notes":"side door"}}'
else
  skip "GET/PUT /api/addresses/{id}" "Address creation failed"
fi

# POST /api/users/{userId}/addresses — alternate create endpoint
ALT_ADDR_RESP=$(curl -s -X POST "$BASE/api/users/$RIDER_ID/addresses" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $RIDER_TOKEN" \
  -d '{"label":"Work","address":"789 Work Ave, Cairo",
       "latitude":30.060,"longitude":31.250,"isDefault":false}')
ALT_ADDR_ID=$(json_num "$ALT_ADDR_RESP" "id")

if [[ -n "${ALT_ADDR_ID:-}" ]]; then
  pass "POST /api/users/$RIDER_ID/addresses (alternate endpoint → 200)"
  echo "  Alt Address ID=$ALT_ADDR_ID"
else
  fail "POST /api/users/$RIDER_ID/addresses (alternate endpoint)" "no id — ${ALT_ADDR_RESP:0:160}"
fi

check "GET /api/users/$RIDER_ID/addresses (list user addresses → 200)" \
  200 "$BASE/api/users/$RIDER_ID/addresses" -H "Authorization: Bearer $RIDER_TOKEN"

# DELETE via /api/users/{userId}/addresses/{addressId}
if [[ -n "${ALT_ADDR_ID:-}" ]]; then
  DEL_UADDR_CODE=$(curl -s -o /tmp/_uts_body -w "%{http_code}" \
    -X DELETE "$BASE/api/users/$RIDER_ID/addresses/$ALT_ADDR_ID" \
    -H "Authorization: Bearer $RIDER_TOKEN" 2>/dev/null)
  if [[ "$DEL_UADDR_CODE" == "200" || "$DEL_UADDR_CODE" == "204" ]]; then
    pass "DELETE /api/users/$RIDER_ID/addresses/$ALT_ADDR_ID (→ HTTP $DEL_UADDR_CODE)"
  else
    fail "DELETE /api/users/$RIDER_ID/addresses/$ALT_ADDR_ID" \
      "expected 200, got $DEL_UADDR_CODE — $(cat /tmp/_uts_body | head -c 160)"
  fi
fi

# DELETE via /api/addresses/{id} — create throwaway address then delete it
DEL_ADDR_RESP=$(curl -s -X POST "$BASE/api/addresses/user/$RIDER_ID" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $RIDER_TOKEN" \
  -d '{"label":"ToDelete","address":"Del St, Cairo","latitude":30.001,"longitude":31.001,"isDefault":false}')
DEL_ADDR_ID=$(json_num "$DEL_ADDR_RESP" "id")

if [[ -n "${DEL_ADDR_ID:-}" ]]; then
  # deleteSavedAddress(id) returns String → HTTP 200 (not 204)
  check "DELETE /api/addresses/$DEL_ADDR_ID (→ 200)" \
    200 -X DELETE "$BASE/api/addresses/$DEL_ADDR_ID" \
    -H "Authorization: Bearer $RIDER_TOKEN"
else
  skip "DELETE /api/addresses/{id}" "Could not create throwaway address"
fi

# =============================================================
#  4. S1-F1: Search Users
#  GET /api/users/search?name=&email=&role= (all optional)
# =============================================================
phase "Phase 4: S1-F1 — Search Users"

check "GET /api/users/search (no params → 200, returns all)" \
  200 "$BASE/api/users/search" -H "Authorization: Bearer $RIDER_TOKEN"

check "GET /api/users/search?name=UTS (partial match → 200)" \
  200 "$BASE/api/users/search?name=UTS" -H "Authorization: Bearer $RIDER_TOKEN"

check "GET /api/users/search?email=uts_rider (partial email → 200)" \
  200 "$BASE/api/users/search?email=uts_rider" -H "Authorization: Bearer $RIDER_TOKEN"

check "GET /api/users/search?role=RIDER (→ 200)" \
  200 "$BASE/api/users/search?role=RIDER" -H "Authorization: Bearer $RIDER_TOKEN"

check "GET /api/users/search?role=ADMIN (→ 200)" \
  200 "$BASE/api/users/search?role=ADMIN" -H "Authorization: Bearer $RIDER_TOKEN"

check "GET /api/users/search?name=UTS&role=RIDER (combined → 200)" \
  200 "$BASE/api/users/search?name=UTS&role=RIDER" -H "Authorization: Bearer $RIDER_TOKEN"

check "GET /api/users/search?name=NOMATCH_XYZ_99999 (no results → 200, empty list)" \
  200 "$BASE/api/users/search?name=NOMATCH_XYZ_99999" -H "Authorization: Bearer $RIDER_TOKEN"

# =============================================================
#  5. S1-F2: Update User Preferences (JSONB merge)
#  PUT /api/users/{id}/preferences — no ownership check in controller!
#  Any authenticated user can update any user's preferences.
# =============================================================
phase "Phase 5: S1-F2 — Update User Preferences (JSONB merge)"

# Set language=ar for RIDER_ID — needed by S1-F9 test later
PREF_HTTP=$(curl -s -o /tmp/_uts_body -w "%{http_code}" \
  -X PUT "$BASE/api/users/$RIDER_ID/preferences" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $RIDER_TOKEN" \
  -d '{"language":"ar","theme":"dark","notifications":true}' 2>/dev/null)
PREF_BODY=$(cat /tmp/_uts_body)

if [[ "$PREF_HTTP" == "200" ]]; then
  pass "PUT /api/users/$RIDER_ID/preferences (set language=ar, theme=dark → 200)"
else
  fail "PUT /api/users/$RIDER_ID/preferences" "expected 200, got $PREF_HTTP — ${PREF_BODY:0:160}"
fi

# Merge new key — existing keys must NOT be wiped
check "PUT /api/users/$RIDER_ID/preferences (merge new key currency=EGP → 200)" \
  200 -X PUT "$BASE/api/users/$RIDER_ID/preferences" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $RIDER_TOKEN" \
  -d '{"currency":"EGP"}'

# No ownership check: RIDER token can update ADMIN's preferences
check "PUT /api/users/$ADMIN_ID/preferences (rider token, no ownership check → 200)" \
  200 -X PUT "$BASE/api/users/$ADMIN_ID/preferences" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $RIDER_TOKEN" \
  -d '{"language":"en"}'

# User not found → 404
check "PUT /api/users/9999999/preferences (not found → 404)" \
  404 -X PUT "$BASE/api/users/9999999/preferences" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"key":"value"}'

# =============================================================
#  6. S1-F3: User Ride Summary (Feign → ride-service)
#  GET /api/users/{id}/ride-summary — ownership check + Feign
#  Returns 503 if ride-service unavailable (not 200 with empty data)
# =============================================================
phase "Phase 6: S1-F3 — User Ride Summary (Feign→ride-service)"

# Own token → ownership check passes; Feign may return 503
RIDE_SUM_HTTP=$(curl -s -o /tmp/_uts_body -w "%{http_code}" \
  "$BASE/api/users/$RIDER_ID/ride-summary" \
  -H "Authorization: Bearer $RIDER_TOKEN" 2>/dev/null)
RIDE_SUM_BODY=$(cat /tmp/_uts_body)

if [[ "$RIDE_SUM_HTTP" == "200" ]]; then
  pass "GET /api/users/$RIDER_ID/ride-summary (own token → 200, Feign OK)"
elif [[ "$RIDE_SUM_HTTP" == "503" ]]; then
  skip "GET /api/users/$RIDER_ID/ride-summary (own → 503)" "ride-service Feign unavailable"
else
  fail "GET /api/users/$RIDER_ID/ride-summary (own token)" \
    "expected 200 or 503, got $RIDE_SUM_HTTP — ${RIDE_SUM_BODY:0:160}"
fi

# Other RIDER token → ownership check fires BEFORE Feign → 403
check "GET /api/users/$RIDER_ID/ride-summary (rider2 token → 403)" \
  403 "$BASE/api/users/$RIDER_ID/ride-summary" \
  -H "Authorization: Bearer $RIDER2_TOKEN"

# ADMIN on own ID → 200 or 503
ADMIN_SUM_HTTP=$(curl -s -o /tmp/_uts_body -w "%{http_code}" \
  "$BASE/api/users/$ADMIN_ID/ride-summary" \
  -H "Authorization: Bearer $ADMIN_TOKEN" 2>/dev/null)
if [[ "$ADMIN_SUM_HTTP" == "200" || "$ADMIN_SUM_HTTP" == "503" ]]; then
  pass "GET /api/users/$ADMIN_ID/ride-summary (admin own → HTTP $ADMIN_SUM_HTTP)"
else
  fail "GET /api/users/$ADMIN_ID/ride-summary (admin own)" \
    "expected 200 or 503, got $ADMIN_SUM_HTTP — $(cat /tmp/_uts_body | head -c 120)"
fi

# User not found: ADMIN bypasses ownership → user lookup → 404
# RIDER with id!=9999999 → ownership fires first → 403 (not 404)
if [[ "$ADMIN_IS_REAL" == "true" ]]; then
  check "GET /api/users/9999999/ride-summary (not found → 404)" \
    404 "$BASE/api/users/9999999/ride-summary" \
    -H "Authorization: Bearer $ADMIN_TOKEN"
else
  skip "GET /api/users/9999999/ride-summary (not found → 404)" \
    "ADMIN role required to bypass ownership before 404"
fi

# =============================================================
#  7. S1-F4: Deactivate User (Feign → ride-service for activeCount)
#  PUT /api/users/{id}/deactivate — ownership check + Feign
#  Returns 400 if active rides exist, 503 if ride-service down.
#  Already DEACTIVATED → 200 no-op (early return, no Feign call).
# =============================================================
phase "Phase 7: S1-F4 — Deactivate User (Feign→ride-service)"

# Create throwaway user with BCrypt (register) so login works if needed
DEACT_RESP=$(curl -s -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"UTS Deact\",\"email\":\"uts_deact_${RUN_ID}@test.com\",
       \"password\":\"Test1234!\",\"phone\":\"+1558${RUN_ID: -4}\"}")
DEACT_TOKEN=$(json_str "$DEACT_RESP" "token")
DEACT_ID=$(decode_jwt_uid "$DEACT_TOKEN")

if [[ -n "${DEACT_ID:-}" ]]; then
  echo "  Deact user ID=$DEACT_ID"

  # RIDER2 trying to deactivate DEACT_ID (not their own) → 403 (checkSelfOrAdmin)
  check "PUT /api/users/$DEACT_ID/deactivate (rider2 on other → 403)" \
    403 -X PUT "$BASE/api/users/$DEACT_ID/deactivate" \
    -H "Authorization: Bearer $RIDER2_TOKEN"

  # ADMIN token bypasses ownership; Feign checks active rides
  if [[ "$ADMIN_IS_REAL" == "true" ]]; then
    DEACT_HTTP=$(curl -s -o /tmp/_uts_body -w "%{http_code}" \
      -X PUT "$BASE/api/users/$DEACT_ID/deactivate" \
      -H "Authorization: Bearer $ADMIN_TOKEN" 2>/dev/null)
    DEACT_BODY=$(cat /tmp/_uts_body)

    if [[ "$DEACT_HTTP" == "200" ]]; then
      pass "PUT /api/users/$DEACT_ID/deactivate (admin → 200, DEACTIVATED)"
      # Already DEACTIVATED → 200 no-op (status check before Feign call)
      check "PUT /api/users/$DEACT_ID/deactivate (already DEACTIVATED → 200 no-op)" \
        200 -X PUT "$BASE/api/users/$DEACT_ID/deactivate" \
        -H "Authorization: Bearer $ADMIN_TOKEN"
    elif [[ "$DEACT_HTTP" == "503" ]]; then
      skip "PUT /api/users/$DEACT_ID/deactivate (→ 503)" "ride-service Feign unavailable"
      skip "PUT /api/users/$DEACT_ID/deactivate (already DEACTIVATED → 200 no-op)" \
        "Previous deactivate did not succeed"
    elif [[ "$DEACT_HTTP" == "400" ]]; then
      skip "PUT /api/users/$DEACT_ID/deactivate (→ 400)" \
        "User has active rides: ${DEACT_BODY:0:100}"
      skip "PUT /api/users/$DEACT_ID/deactivate (already DEACTIVATED → 200 no-op)" \
        "Previous deactivate returned 400"
    else
      fail "PUT /api/users/$DEACT_ID/deactivate" \
        "expected 200 or 503, got $DEACT_HTTP — ${DEACT_BODY:0:160}"
      skip "PUT /api/users/$DEACT_ID/deactivate (already DEACTIVATED → 200 no-op)" \
        "Previous deactivate did not succeed"
    fi
  else
    skip "PUT /api/users/$DEACT_ID/deactivate (admin)" \
      "ADMIN role required to deactivate another user's account"
    skip "PUT /api/users/$DEACT_ID/deactivate (already DEACTIVATED → 200 no-op)" \
      "ADMIN role unavailable"
  fi
else
  skip "S1-F4 Deactivate (main path)" "Could not register throwaway user"
  skip "S1-F4 Deactivate (already-deactivated no-op)" "Throwaway user unavailable"
fi

# Not found with ADMIN → 404 (user lookup throws before Feign call)
# RIDER token with id!=9999999 → ownership check → 403 first
if [[ "$ADMIN_IS_REAL" == "true" ]]; then
  check "PUT /api/users/9999999/deactivate (not found → 404)" \
    404 -X PUT "$BASE/api/users/9999999/deactivate" \
    -H "Authorization: Bearer $ADMIN_TOKEN"
else
  skip "PUT /api/users/9999999/deactivate (not found → 404)" \
    "ADMIN role required to bypass ownership before 404"
fi

# =============================================================
#  8. S1-F5: Filter Users by Preference (JSONB search)
#  GET /api/users/preferences/search?key=language&value=ar
#  language=ar was set for RIDER_ID in Phase 5.
# =============================================================
phase "Phase 8: S1-F5 — Filter Users by Preference"

check "GET /api/users/preferences/search?key=language&value=ar (→ 200)" \
  200 "$BASE/api/users/preferences/search?key=language&value=ar" \
  -H "Authorization: Bearer $RIDER_TOKEN"

check "GET /api/users/preferences/search?key=theme&value=dark (→ 200)" \
  200 "$BASE/api/users/preferences/search?key=theme&value=dark" \
  -H "Authorization: Bearer $RIDER_TOKEN"

check "GET /api/users/preferences/search?key=currency&value=EGP (→ 200)" \
  200 "$BASE/api/users/preferences/search?key=currency&value=EGP" \
  -H "Authorization: Bearer $RIDER_TOKEN"

check "GET /api/users/preferences/search?key=language&value=NOMATCH (→ 200, empty)" \
  200 "$BASE/api/users/preferences/search?key=language&value=NOMATCH_99999" \
  -H "Authorization: Bearer $RIDER_TOKEN"

# Missing key → Spring MVC MissingServletRequestParameterException → 400
check "GET /api/users/preferences/search (missing key → 400)" \
  400 "$BASE/api/users/preferences/search?value=ar" \
  -H "Authorization: Bearer $RIDER_TOKEN"

# Missing value → 400
check "GET /api/users/preferences/search (missing value → 400)" \
  400 "$BASE/api/users/preferences/search?key=language" \
  -H "Authorization: Bearer $RIDER_TOKEN"

# =============================================================
#  9. S1-F6: Top Riders by Spending (Feign → payment-service per user)
#  GET /api/users/reports/top-riders?startDate=&endDate=&limit=
#  Feign failure per user is silently skipped → may return []
# =============================================================
phase "Phase 9: S1-F6 — Top Riders by Spending (Feign→payment-service)"

TOP_HTTP=$(curl -s -o /tmp/_uts_body -w "%{http_code}" \
  "$BASE/api/users/reports/top-riders?startDate=$PAST&endDate=$TODAY&limit=5" \
  -H "Authorization: Bearer $RIDER_TOKEN" 2>/dev/null)
TOP_BODY=$(cat /tmp/_uts_body)

if [[ "$TOP_HTTP" == "200" ]]; then
  pass "GET /api/users/reports/top-riders?startDate=$PAST&endDate=$TODAY&limit=5 (→ 200)"
else
  fail "GET /api/users/reports/top-riders?startDate=$PAST&endDate=$TODAY&limit=5" \
    "expected 200, got $TOP_HTTP — ${TOP_BODY:0:160}"
fi

check "GET /api/users/reports/top-riders?limit=1 (→ 200)" \
  200 "$BASE/api/users/reports/top-riders?startDate=$PAST&endDate=$TODAY&limit=1" \
  -H "Authorization: Bearer $RIDER_TOKEN"

# startDate > endDate → 400 (validated in getTopRiders service method)
check "GET /api/users/reports/top-riders (startDate > endDate → 400)" \
  400 "$BASE/api/users/reports/top-riders?startDate=$TODAY&endDate=$PAST&limit=5" \
  -H "Authorization: Bearer $RIDER_TOKEN"

# Invalid date format → 400 (DateTimeParseException in service)
check "GET /api/users/reports/top-riders (invalid date format → 400)" \
  400 "$BASE/api/users/reports/top-riders?startDate=not-a-date&endDate=$TODAY&limit=5" \
  -H "Authorization: Bearer $RIDER_TOKEN"

# Missing required param (startDate) → MissingServletRequestParameterException → 400
check "GET /api/users/reports/top-riders (missing startDate → 400)" \
  400 "$BASE/api/users/reports/top-riders?endDate=$TODAY&limit=5" \
  -H "Authorization: Bearer $RIDER_TOKEN"

# Missing limit → 400
check "GET /api/users/reports/top-riders (missing limit → 400)" \
  400 "$BASE/api/users/reports/top-riders?startDate=$PAST&endDate=$TODAY" \
  -H "Authorization: Bearer $RIDER_TOKEN"

# =============================================================
#  10. S1-F7: Set Default Address
#  PUT /api/users/{userId}/addresses/{addressId}/default
#  SavedAddressService: user not found → 404, address not found → 404,
#  address belongs to different user → 404 (NOT 400 — see SavedAddressService)
# =============================================================
phase "Phase 10: S1-F7 — Set Default Address"

if [[ -n "${ADDRESS_ID:-}" ]]; then
  DEF_HTTP=$(curl -s -o /tmp/_uts_body -w "%{http_code}" \
    -X PUT "$BASE/api/users/$RIDER_ID/addresses/$ADDRESS_ID/default" \
    -H "Authorization: Bearer $RIDER_TOKEN" 2>/dev/null)
  DEF_BODY=$(cat /tmp/_uts_body)

  if [[ "$DEF_HTTP" == "200" ]]; then
    pass "PUT /api/users/$RIDER_ID/addresses/$ADDRESS_ID/default (→ 200)"
  else
    fail "PUT /api/users/$RIDER_ID/addresses/$ADDRESS_ID/default" \
      "expected 200, got $DEF_HTTP — ${DEF_BODY:0:160}"
  fi

  # Idempotent: set same address as default again → 200
  check "PUT /api/users/$RIDER_ID/addresses/$ADDRESS_ID/default (idempotent → 200)" \
    200 -X PUT "$BASE/api/users/$RIDER_ID/addresses/$ADDRESS_ID/default" \
    -H "Authorization: Bearer $RIDER_TOKEN"
else
  skip "S1-F7 Set default address" "No address ID — address creation failed in Phase 3"
fi

# Address not found → 404
check "PUT /api/users/$RIDER_ID/addresses/9999999/default (address not found → 404)" \
  404 -X PUT "$BASE/api/users/$RIDER_ID/addresses/9999999/default" \
  -H "Authorization: Bearer $RIDER_TOKEN"

# User not found → 404 (SavedAddressService.findUserByIdOrThrow)
# No ownership check on this endpoint — RIDER can call it; user lookup fires → 404
check "PUT /api/users/9999999/addresses/1/default (user not found → 404)" \
  404 -X PUT "$BASE/api/users/9999999/addresses/1/default" \
  -H "Authorization: Bearer $RIDER_TOKEN"

# Address belongs to different user → SavedAddressService throws NOT_FOUND (404)
if [[ -n "${ADDRESS_ID:-}" && -n "${RIDER2_ID:-}" ]]; then
  check "PUT /api/users/$RIDER2_ID/addresses/$ADDRESS_ID/default (wrong user's addr → 404)" \
    404 -X PUT "$BASE/api/users/$RIDER2_ID/addresses/$ADDRESS_ID/default" \
    -H "Authorization: Bearer $RIDER2_TOKEN"
fi

# =============================================================
#  11. S1-F8: User Profile with Saved Addresses
#  GET /api/users/{id}/profile — NO ownership check in controller!
#  Any authenticated user can fetch any user's profile.
# =============================================================
phase "Phase 11: S1-F8 — User Profile with Saved Addresses"

check "GET /api/users/$RIDER_ID/profile (own token → 200)" \
  200 "$BASE/api/users/$RIDER_ID/profile" -H "Authorization: Bearer $RIDER_TOKEN"

# No ownership check — other RIDER token works
check "GET /api/users/$RIDER_ID/profile (rider2 token, no ownership check → 200)" \
  200 "$BASE/api/users/$RIDER_ID/profile" -H "Authorization: Bearer $RIDER2_TOKEN"

check "GET /api/users/$ADMIN_ID/profile (admin own → 200)" \
  200 "$BASE/api/users/$ADMIN_ID/profile" -H "Authorization: Bearer $ADMIN_TOKEN"

# Not found → 404
check "GET /api/users/9999999/profile (not found → 404)" \
  404 "$BASE/api/users/9999999/profile" -H "Authorization: Bearer $ADMIN_TOKEN"

# =============================================================
#  12. S1-F9: Find Users by Language + Min Rides (Feign → ride-service)
#  GET /api/users/preferences/language?lang=ar&minRides=3
#  For each matching user, calls rideServiceClient.getCompletedRideCount()
#  Feign failure per user → silently skipped (user excluded from result)
# =============================================================
phase "Phase 12: S1-F9 — Language + Min Rides (Feign→ride-service)"

# lang=ar was set for RIDER_ID in Phase 5; minRides=0 → all language matches returned
LANG_HTTP=$(curl -s -o /tmp/_uts_body -w "%{http_code}" \
  "$BASE/api/users/preferences/language?lang=ar&minRides=0" \
  -H "Authorization: Bearer $RIDER_TOKEN" 2>/dev/null)
LANG_BODY=$(cat /tmp/_uts_body)

if [[ "$LANG_HTTP" == "200" ]]; then
  pass "GET /api/users/preferences/language?lang=ar&minRides=0 (→ 200)"
else
  fail "GET /api/users/preferences/language?lang=ar&minRides=0" \
    "expected 200, got $LANG_HTTP — ${LANG_BODY:0:160}"
fi

# High minRides threshold → likely empty list (Feign returns low count) → 200
check "GET /api/users/preferences/language?lang=ar&minRides=9999 (high threshold → 200, empty)" \
  200 "$BASE/api/users/preferences/language?lang=ar&minRides=9999" \
  -H "Authorization: Bearer $RIDER_TOKEN"

# minRides defaults to 0 when not provided
check "GET /api/users/preferences/language?lang=en (default minRides=0 → 200)" \
  200 "$BASE/api/users/preferences/language?lang=en" \
  -H "Authorization: Bearer $RIDER_TOKEN"

# Missing lang → MissingServletRequestParameterException → 400
check "GET /api/users/preferences/language (missing lang → 400)" \
  400 "$BASE/api/users/preferences/language?minRides=3" \
  -H "Authorization: Bearer $RIDER_TOKEN"

# =============================================================
#  13. S1-F10: Register (MS2 — BCrypt password, 201, duplicate → 409)
# =============================================================
phase "Phase 13: S1-F10 — Register (MS2 BCrypt)"

# Fresh registration with unique email + phone → 201 + JWT token in body
REG_CODE=$(curl -s -o /tmp/_uts_body -w "%{http_code}" \
  -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"UTS New ${RUN_ID}\",\"email\":\"uts_new_${RUN_ID}@test.com\",
       \"password\":\"Test1234!\",\"phone\":\"+1559${RUN_ID: -4}\"}" 2>/dev/null)
REG_BODY=$(cat /tmp/_uts_body)

if [[ "$REG_CODE" == "201" ]]; then
  pass "POST /api/auth/register (fresh → 201)"
  REG_TOKEN_VAL=$(json_str "$REG_BODY" "token")
  if [[ -n "${REG_TOKEN_VAL:-}" ]]; then
    pass "POST /api/auth/register response contains JWT token"
  else
    fail "POST /api/auth/register response contains JWT token" \
      "token missing — ${REG_BODY:0:160}"
  fi
else
  fail "POST /api/auth/register (fresh)" "expected 201, got $REG_CODE — ${REG_BODY:0:160}"
fi

# Duplicate email → ResponseStatusException(409)
check "POST /api/auth/register (duplicate email → 409)" \
  409 -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Dup\",\"email\":\"uts_rider@test.com\",
       \"password\":\"Test1234!\",\"phone\":\"+19990001\"}"

# Duplicate phone → 409
check "POST /api/auth/register (duplicate phone → 409)" \
  409 -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Dup\",\"email\":\"unique_reg_${RUN_ID}@test.com\",
       \"password\":\"Test1234!\",\"phone\":\"+15560201\"}"

# Missing name → 400 (registerUser validation: name blank check)
check "POST /api/auth/register (missing name → 400)" \
  400 -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"noname_${RUN_ID}@test.com\",\"password\":\"Test1234!\",\"phone\":\"+19990002\"}"

# Missing email → 400
check "POST /api/auth/register (missing email → 400)" \
  400 -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"NoEmail\",\"password\":\"Test1234!\",\"phone\":\"+19990003\"}"

# Missing password → 400
check "POST /api/auth/register (missing password → 400)" \
  400 -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"NoPass\",\"email\":\"nopass_${RUN_ID}@test.com\",\"phone\":\"+19990004\"}"

# Missing phone → 400
check "POST /api/auth/register (missing phone → 400)" \
  400 -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"NoPhone\",\"email\":\"nophone_${RUN_ID}@test.com\",\"password\":\"Test1234!\"}"

# =============================================================
#  14. S1-F11: Login (MS2 — JWT, credentials check)
#  POST /api/auth/login
#  Wrong/nonexistent user → 401 (not 404 — same error for security)
# =============================================================
phase "Phase 14: S1-F11 — Login (MS2 JWT)"

check "POST /api/auth/login (valid rider credentials → 200)" \
  200 -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"uts_rider@test.com","password":"Test1234!"}'

check "POST /api/auth/login (valid admin credentials → 200)" \
  200 -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"uts_admin@test.com","password":"Test1234!"}'

check "POST /api/auth/login (wrong password → 401)" \
  401 -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"uts_rider@test.com","password":"WrongPassword!"}'

# Nonexistent user → 401 (same as wrong password — no user enumeration)
check "POST /api/auth/login (nonexistent email → 401, not 404)" \
  401 -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"nobody_9999999@test.com","password":"Test1234!"}'

# =============================================================
#  15. S1-F12: Activity Feed (MS2 — MongoDB, ownership, pagination)
#  GET /api/users/{id}/activity?page=0&size=10
#  Events are logged on login (logAuthEvent), register (REGISTERED event).
#  Ownership check: requesterId != id && role != ADMIN → 403
# =============================================================
phase "Phase 15: S1-F12 — Activity Feed (MS2 MongoDB)"

# Own token → 200 (login events logged in Phase 0)
check "GET /api/users/$RIDER_ID/activity (own token → 200)" \
  200 "$BASE/api/users/$RIDER_ID/activity" \
  -H "Authorization: Bearer $RIDER_TOKEN"

check "GET /api/users/$RIDER_ID/activity?page=0&size=10 (paginated → 200)" \
  200 "$BASE/api/users/$RIDER_ID/activity?page=0&size=10" \
  -H "Authorization: Bearer $RIDER_TOKEN"

check "GET /api/users/$RIDER_ID/activity?page=0&size=1 (size=1 → 200)" \
  200 "$BASE/api/users/$RIDER_ID/activity?page=0&size=1" \
  -H "Authorization: Bearer $RIDER_TOKEN"

check "GET /api/users/$RIDER_ID/activity?page=1&size=5 (page=1 → 200)" \
  200 "$BASE/api/users/$RIDER_ID/activity?page=1&size=5" \
  -H "Authorization: Bearer $RIDER_TOKEN"

# Other RIDER → 403 (controller ownership check)
check "GET /api/users/$RIDER_ID/activity (rider2 token → 403)" \
  403 "$BASE/api/users/$RIDER_ID/activity" \
  -H "Authorization: Bearer $RIDER2_TOKEN"

# ADMIN bypasses ownership check → 200
if [[ "$ADMIN_IS_REAL" == "true" ]]; then
  check "GET /api/users/$RIDER_ID/activity (admin token → 200)" \
    200 "$BASE/api/users/$RIDER_ID/activity" \
    -H "Authorization: Bearer $ADMIN_TOKEN"
else
  skip "GET /api/users/$RIDER_ID/activity (admin token → 200)" \
    "ADMIN role required to bypass ownership"
fi

# ADMIN's own activity feed (always works — own ID matches regardless of role)
check "GET /api/users/$ADMIN_ID/activity (admin own → 200)" \
  200 "$BASE/api/users/$ADMIN_ID/activity" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Invalid pagination → 400 (checked AFTER ownership check in controller)
check "GET /api/users/$RIDER_ID/activity?page=-1 (page=-1 → 400)" \
  400 "$BASE/api/users/$RIDER_ID/activity?page=-1" \
  -H "Authorization: Bearer $RIDER_TOKEN"

check "GET /api/users/$RIDER_ID/activity?size=0 (size=0 → 400)" \
  400 "$BASE/api/users/$RIDER_ID/activity?size=0" \
  -H "Authorization: Bearer $RIDER_TOKEN"

# User not found → 404 (ADMIN bypasses ownership → user lookup → 404)
# RIDER with id!=9999999 would get 403 before 404
if [[ "$ADMIN_IS_REAL" == "true" ]]; then
  check "GET /api/users/9999999/activity (not found → 404)" \
    404 "$BASE/api/users/9999999/activity" \
    -H "Authorization: Bearer $ADMIN_TOKEN"
else
  skip "GET /api/users/9999999/activity (not found → 404)" \
    "ADMIN role required to bypass ownership check before 404"
fi

# =============================================================
#  16. CC-2: Role Management (MS2)
#  PUT /api/users/{id}/role  body: {"role":"ADMIN"}
#  RoleAuthorizationHandler enforces ADMIN for PUT /api/users/*/role.
#  RIDER token → 403 at the filter level.
# =============================================================
phase "Phase 16: CC-2 — Role Management (MS2)"

# ADMIN updating RIDER2 → ADMIN → 200 (requires real ADMIN JWT)
if [[ -n "${RIDER2_ID:-}" ]]; then
  if [[ "$ADMIN_IS_REAL" == "true" ]]; then
    ROLE_HTTP=$(curl -s -o /tmp/_uts_body -w "%{http_code}" \
      -X PUT "$BASE/api/users/$RIDER2_ID/role" \
      -H 'Content-Type: application/json' \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -d '{"role":"ADMIN"}' 2>/dev/null)
    ROLE_BODY=$(cat /tmp/_uts_body)
    if [[ "$ROLE_HTTP" == "200" ]]; then
      pass "PUT /api/users/$RIDER2_ID/role (admin token → ADMIN → 200)"
    else
      fail "PUT /api/users/$RIDER2_ID/role (admin token)" \
        "expected 200, got $ROLE_HTTP — ${ROLE_BODY:0:160}"
    fi

    # Restore RIDER2 to RIDER role
    curl -s -o /dev/null \
      -X PUT "$BASE/api/users/$RIDER2_ID/role" \
      -H 'Content-Type: application/json' \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -d '{"role":"RIDER"}'

    # RIDER token: RoleAuthorizationHandler rejects → 403
    check "PUT /api/users/$RIDER2_ID/role (rider token → 403, ADMIN-only enforced)" \
      403 -X PUT "$BASE/api/users/$RIDER2_ID/role" \
      -H 'Content-Type: application/json' \
      -H "Authorization: Bearer $RIDER_TOKEN" \
      -d '{"role":"RIDER"}'
  else
    skip "PUT /api/users/$RIDER2_ID/role (admin token)" \
      "ADMIN role required (RoleAuthorizationHandler) — kubectl promotion failed"
    skip "PUT /api/users/$RIDER2_ID/role (rider token → 403)" \
      "ADMIN setup unavailable"
  fi
else
  skip "CC-2 Role update (main paths)" "RIDER2 ID unavailable"
fi

# Invalid role value → RoleAuthorizationHandler: ADMIN required → 403 if not ADMIN
# OR IllegalArgumentException in service → 400 if ADMIN passes filter
if [[ "$ADMIN_IS_REAL" == "true" ]]; then
  check "PUT /api/users/$RIDER_ID/role (invalid role → 400)" \
    400 -X PUT "$BASE/api/users/$RIDER_ID/role" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d '{"role":"SUPERUSER"}'

  check "PUT /api/users/$RIDER_ID/role (blank role → 400)" \
    400 -X PUT "$BASE/api/users/$RIDER_ID/role" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d '{"role":""}'

  check "PUT /api/users/9999999/role (not found → 404)" \
    404 -X PUT "$BASE/api/users/9999999/role" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d '{"role":"RIDER"}'
else
  # Without ADMIN, RoleAuthorizationHandler returns 403 for all /role calls
  check "PUT /api/users/$RIDER_ID/role (non-admin token → 403 from filter)" \
    403 -X PUT "$BASE/api/users/$RIDER_ID/role" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $RIDER_TOKEN" \
    -d '{"role":"SUPERUSER"}'
  skip "PUT /api/users/$RIDER_ID/role (blank role → 400)" \
    "ADMIN role required to reach service validation"
  skip "PUT /api/users/9999999/role (not found → 404)" \
    "ADMIN role required to reach service"
fi

# =============================================================
#  SUMMARY
# =============================================================
TOTAL=$((PASS + FAIL + SKIP))
echo -e "\n${CYAN}${BOLD}══════════════════════════════════════════════════════════"
echo "  RESULTS"
echo -e "══════════════════════════════════════════════════════════${NC}"
for r in "${RESULTS[@]}"; do echo -e "$r"; done
echo ""
echo -e "  ${GREEN}${BOLD}PASS:${NC} $PASS   ${RED}${BOLD}FAIL:${NC} $FAIL   ${YELLOW}${BOLD}SKIP:${NC} $SKIP   ${BOLD}TOTAL:${NC} $TOTAL"
echo ""

if   [[ $FAIL -eq 0 && $PASS -gt 0 ]]; then
  echo -e "${GREEN}${BOLD}  All executed tests passed!${NC}"
elif [[ $FAIL -gt 0 ]]; then
  echo -e "${RED}${BOLD}  $FAIL test(s) FAILED — see details above.${NC}"
fi
if [[ $SKIP -gt 0 ]]; then
  echo -e "${YELLOW}  $SKIP skipped — usually due to:${NC}"
  echo -e "${YELLOW}    • S1-F3/F4/F9: ride-service Feign unavailable (503)${NC}"
  echo -e "${YELLOW}    • S1-F6: payment-service Feign silently skipped per user (returns [])${NC}"
  echo -e "${YELLOW}    • S1-F4: ride-service reports active rides (400) or Feign down (503)${NC}"
fi

echo ""
[[ $FAIL -eq 0 ]]  # exit 0 on all-pass, exit 1 on any failure
