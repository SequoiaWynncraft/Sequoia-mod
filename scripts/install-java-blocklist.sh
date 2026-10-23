#!/usr/bin/env bash
set -euo pipefail
java_home="${1:?Usage: install-java-blocklist.sh JAVA_HOME}"
source_file="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../security" && pwd)/blocked.certs"
target="$java_home/lib/security/blocked.certs"
[[ -f "$target" ]] || { printf 'Missing Java blocklist: %s\n' "$target" >&2; exit 1; }
[[ "$(head -n 1 "$target" | tr -d '\r')" == 'Algorithm=SHA-256' ]] || { printf 'Unsupported Java blocklist algorithm\n' >&2; exit 1; }
metadata() {
  stat -c '%u:%g:%a' "$1" 2>/dev/null || stat -f '%u:%g:%Lp' "$1"
}
original_metadata="$(metadata "$target")"
temporary="$(mktemp "${target}.XXXXXX")"
trap 'rm -f -- "$temporary"' EXIT
cp -p "$target" "$temporary"
# BSD cp can silently fail to preserve ownership; do not replace in that case.
[[ "$(metadata "$temporary")" == "$original_metadata" ]] || {
  printf 'Unable to preserve Java blocklist metadata\n' >&2; exit 1;
}
chmod u+w "$temporary"
# Keep the original Properties syntax, annotations and continuations byte-for-byte.
appended=false
while IFS= read -r fingerprint || [[ -n "$fingerprint" ]]; do
  [[ "$fingerprint" =~ ^[0-9A-F]{64}$ ]] || continue
  if ! grep -Fqx "$fingerprint" "$target"; then
    if [[ "$appended" == false ]]; then printf '\n\n' >> "$temporary"; appended=true; fi
    printf '%s\n' "$fingerprint" >> "$temporary"
  fi
done < "$source_file"
chmod "${original_metadata##*:}" "$temporary"
[[ "$(metadata "$temporary")" == "$original_metadata" ]] || exit 1
mv -- "$temporary" "$target"
