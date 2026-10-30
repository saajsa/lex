#!/bin/bash
# Build the client app's Test copy (preview build type) and publish it, so every
# phone with "Legum Lex Test" (a contact on app_test_contacts) offers the update
# on its next open. Production by default. Publishing uses the staff API with an
# administrator's token, as the staff app's script does.
#
#   tools/publish.sh "what changed"        build + publish to www.legumlex.com
#   tools/publish.sh --login               once: sign in as an administrator; the
#                                          token is kept in local.properties
#   tools/publish.sh --dev "what changed"  build against dev and publish there
#                                          (TEST_API=<url> picks the dev address)
#
# The version code is minutes since 1 Jan 2026 plus 26,000,000: it rises every
# minute, so each build is newer than the last.
set -euo pipefail
cd "$(dirname "$0")/.."
APP=client
BAT='C:\Users\saaja\AppData\Local\Temp\lexgw.bat'
PROPS=local.properties
prop() { grep -E "^$1=" "$PROPS" 2>/dev/null | head -1 | cut -d= -f2- || true; }

if [ "${1:-}" = "--login" ]; then
    read -rp "Admin email: " EMAIL
    read -rsp "Password: " PASS; echo
    TOKEN=$(curl -s -X POST https://www.legumlex.com/api/auth/login --data-urlencode "email=$EMAIL" --data-urlencode "password=$PASS" \
        | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('token','')) if d.get('status') else sys.exit(d.get('message','Sign-in failed'))")
    grep -v '^PUBLISH_TOKEN=' "$PROPS" > "$PROPS.tmp" && mv "$PROPS.tmp" "$PROPS"
    echo "PUBLISH_TOKEN=$TOKEN" >> "$PROPS"
    echo "Signed in; the token is kept in $PROPS."
    exit 0
fi

DEV=0
if [ "${1:-}" = "--dev" ]; then DEV=1; shift; fi
NOTES="${1:-}"
[ -n "$NOTES" ] || { echo 'Say what changed: tools/publish.sh "what changed"'; exit 1; }

CODE=$(( 26000000 + ( $(date +%s) - $(date -d 2026-01-01 +%s) ) / 60 ))
EXTRA=()
if [ $DEV = 1 ]; then
    API=${TEST_API:-$(prop API_BASE_URL)}   # TEST_API= overrides (e.g. the USB address)
    EXTRA=("-PtestApi=$API")
    export PUBLISH_HOST=lex.dvl.to
    export PUBLISH_TOKEN=$(mysql -h127.0.0.1 -uperfex -pperfex perfex -N -e "select token from tbluser_api where name='Staff App' and user=1 and expiration_date>now() order by id desc limit 1" 2>/dev/null)
    BASE=https://127.0.0.1/api/
else
    export PUBLISH_TOKEN=$(prop PUBLISH_TOKEN)
    [ -n "$PUBLISH_TOKEN" ] || { echo "No token yet: run tools/publish.sh --login first."; exit 1; }
    BASE=https://www.legumlex.com/api/
fi

echo "Building $APP Test build $CODE…"
# The dev check talks to dev's customers_api; uploads still go to the staff API.
timeout 1200 cmd.exe /c "$BAT" assemblePreview "-PtestCode=$CODE" "${EXTRA[@]}" -q 2>&1 | grep -E "^e:|FAILED|error:" && exit 1
APK=app/build/outputs/apk/preview/app-preview.apk
read -r BUILT NAME < <(python3 -c "import json; e=json.load(open('app/build/outputs/apk/preview/output-metadata.json'))['elements'][0]; print(e['versionCode'], e['versionName'])")
[ "$BUILT" = "$CODE" ] || { echo "The build did not finish (found $BUILT, expected $CODE)."; exit 1; }
python3 tools/upload.py "$BASE" "$APP" "$APK" "$CODE" "$NAME" "$NOTES"
