#!/usr/bin/env bash
set -euo pipefail
java_home="${1:?Usage: install-java-blocklist.sh JAVA_HOME}"
source_file="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../security" && pwd)/blocked.certs"
target="$java_home/lib/security/blocked.certs"
[[ -f "$target" ]] || { printf 'Missing Java blocklist: %s\n' "$target" >&2; exit 1; }
[[ "$(head -n 1 "$target" | tr -d '\r')" == 'Algorithm=SHA-256' ]] || { printf 'Unsupported Java blocklist algorithm\n' >&2; exit 1; }
# Preserve vendor and operator entries when adding the OpenJDK advisory blocklist.
{ printf 'Algorithm=SHA-256\n'; cat "$target" "$source_file" | tr -d '\r' | grep -E '^[0-9A-Fa-f]{64}$' | tr '[:lower:]' '[:upper:]' | LC_ALL=C sort -u; } > "$target.new"
chmod --reference="$target" "$target.new"
mv -- "$target.new" "$target"
