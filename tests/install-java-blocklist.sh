#!/usr/bin/env bash
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d "${TMPDIR:-/tmp}/java-blocklist-test.XXXXXX")"
trap 'rm -rf -- "$work"' EXIT
mkdir -p "$work/jdk/lib/security" "$work/bin"
target="$work/jdk/lib/security/blocked.certs"
operator=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
# Exercise a CRLF header and a valid final entry with no newline.
printf 'Algorithm=SHA-256\r\n%s' "$operator" > "$target"
chmod 600 "$target"
bash "$root/scripts/install-java-blocklist.sh" "$work/jdk"
grep -qx "$operator" "$target"
expected="$(grep -Ec '^[0-9A-Fa-f]{64}$' "$root/security/blocked.certs")"
actual="$(grep -Ec '^[0-9A-Fa-f]{64}$' "$target")"
[[ "$actual" == "$((expected + 1))" ]]
mode="$(stat -c '%a' "$target" 2>/dev/null || stat -f '%Lp' "$target")"
[[ "$mode" == 600 ]]
cp "$target" "$work/first"
bash "$root/scripts/install-java-blocklist.sh" "$work/jdk"
cmp "$target" "$work/first"
# Emulate BSD stat's option interface. This is not a native macOS test.
real_stat="$(PATH="$(getconf PATH)" command -v stat)"
cat > "$work/bin/stat" <<'STAT'
#!/usr/bin/env bash
if [[ "$1" == -c ]]; then exit 1; fi
[[ "$1" == -f && "$2" == '%Lp' ]]
exec "$TEST_REAL_STAT" -c '%a' "$3"
STAT
chmod +x "$work/bin/stat"
TEST_REAL_STAT="$real_stat" PATH="$work/bin:$PATH" \
  bash "$root/scripts/install-java-blocklist.sh" "$work/jdk"
cmp "$target" "$work/first"
printf 'Algorithm=SHA-1\n' > "$target"
if bash "$root/scripts/install-java-blocklist.sh" "$work/jdk" 2>/dev/null; then
  printf 'Unsupported algorithm unexpectedly accepted\n' >&2
  exit 1
fi
printf 'Algorithm=SHA-1\n' > "$work/unchanged"
cmp "$target" "$work/unchanged"
rm "$target"
if bash "$root/scripts/install-java-blocklist.sh" "$work/jdk" 2>/dev/null; then
  printf 'Missing target unexpectedly accepted\n' >&2
  exit 1
fi
printf 'PASS: CRLF, unterminated entry, all bundled hashes, mode, idempotency, BSD stat fallback, fail-closed checks\n'
