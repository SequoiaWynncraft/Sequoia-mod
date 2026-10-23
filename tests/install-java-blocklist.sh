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
# Metadata-copy failure must leave the active blocklist untouched.
cat > "$work/bin/cp" <<'COPY'
#!/usr/bin/env bash
exit 1
COPY
chmod +x "$work/bin/cp"
if PATH="$work/bin:$PATH" bash "$root/scripts/install-java-blocklist.sh" "$work/jdk"; then
  printf 'Metadata-copy failure unexpectedly accepted\n' >&2
  exit 1
fi
cmp "$target" "$work/first"
shopt -s nullglob
leftovers=("$target".*)
[[ "${#leftovers[@]}" == 0 ]]
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
printf 'PASS: CRLF, unterminated entry, all bundled hashes, mode, idempotency, metadata-copy failure, cleanup, fail-closed checks\n'
