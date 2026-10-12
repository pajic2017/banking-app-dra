#!/usr/bin/env bash
# Portable tool for the Linux x86_64 VFS cloud workaround only.
set -euo pipefail
binary_dir=/workspace/toolchains/bin
if [[ -x "$binary_dir/crane" ]]; then "$binary_dir/crane" version; exit 0; fi
[[ "$(uname -m)" == x86_64 ]] || { echo 'Use the official crane build for this architecture.' >&2; exit 1; }
mkdir -p "$binary_dir"
archive_dir=$(mktemp -d /tmp/bank-crane.XXXXXX)
trap 'rm -rf "$archive_dir"' EXIT
curl -fsSL https://github.com/google/go-containerregistry/releases/download/v0.20.3/go-containerregistry_Linux_x86_64.tar.gz -o "$archive_dir/crane.tar.gz"
printf '%s  %s\n' '36c67a932f489b3f2724b64af90b599a8ef2aa7b004872597373c0ad694dc059' "$archive_dir/crane.tar.gz" | sha256sum --check
tar -xzf "$archive_dir/crane.tar.gz" -C "$binary_dir" crane
chmod 755 "$binary_dir/crane"
