#!/usr/bin/env bash
# Ia Horizon CLI + server.jar oficial 26.3, ruleaza horizon-prepare si face 26.3.tar.gz.
# Gandit sa ruleze pe runnerul GitHub (are internet complet), fara PC-ul utilizatorului.
set -uo pipefail

echo "== java"
java -version 2>&1 || echo "java lipseste"

echo "== descarc Horizon CLI"
curl -fsSL -o h.tar.gz https://github.com/horizon-rust/horizon/releases/download/nightly/horizon-linux-x86_64.tar.gz
echo "download=$? ($(stat -c%s h.tar.gz 2>/dev/null) bytes)"
rm -rf horizon-cli && mkdir -p horizon-cli && tar xzf h.tar.gz -C horizon-cli
ls horizon-cli | head -20

echo "== descarc server.jar 26.3 de la Mojang"
curl -fsSL -o manifest.json https://piston-meta.mojang.com/mc/game/version_manifest_v2.json
echo "manifest=$? ($(stat -c%s manifest.json 2>/dev/null) bytes)"
python3 << 'PY'
import json, urllib.request
manifest = json.load(open('manifest.json'))
versions = {v['id']: v for v in manifest['versions']}
print('latest:', manifest.get('latest'))
pick = versions.get('26.3')
if pick is None:
    candidates = [v for v in manifest['versions'] if v['id'].startswith('26.')]
    print('versiuni 26.x:', [v['id'] for v in candidates][:10])
    pick = candidates[0] if candidates else manifest['versions'][0]
print('folosesc versiunea', pick['id'])
meta = json.load(urllib.request.urlopen(pick['url']))
url = meta['downloads']['server']['url']
print('server.url =', url)
urllib.request.urlretrieve(url, 'server.jar')
PY
ls -lh server.jar || { echo "server.jar NU a fost descarcat"; exit 1; }

echo "== horizon-prepare"
chmod +x horizon-cli/horizon-prepare
./horizon-cli/horizon-prepare --prepare --server-jar server.jar
echo "prepare=$?"

echo "== caut registries.json"
find . -name registries.json -not -path "./horizon-src/*" 2>/dev/null | head -5

DIR=$(find . -name registries.json -not -path "./horizon-src/*" 2>/dev/null | head -1 | xargs -r dirname)
echo "folder pregatit: ${DIR:-<niciunul>}"
if [ -n "${DIR}" ]; then
  tar czf 26.3.tar.gz -C "$(dirname "$DIR")" "$(basename "$DIR")"
  ls -lh 26.3.tar.gz
fi
