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

echo "== reconstruiesc o instalare minima de Minecraft (ca sa aiba ce citi prepare)"
python3 << 'PY'
import json, os, urllib.request
meta = json.load(open('manifest.json'))
mine = next(v for v in meta['versions'] if v['id'] == '26.3')
data = json.load(urllib.request.urlopen(mine['url']))
root = os.path.abspath('.minecraft')
verdir = os.path.join(root, 'versions', '26.3')
os.makedirs(verdir, exist_ok=True)
json.dump(data, open(os.path.join(verdir, '26.3.json'), 'w'))
def fetch(url, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return
    try:
        urllib.request.urlretrieve(url, path)
    except Exception as error:
        print('  esec', url, error)
fetch(data['downloads']['client']['url'], os.path.join(verdir, '26.3.jar'))
print('client jar ok')
count = 0
for lib in data.get('libraries', []):
    art = lib.get('downloads', {}).get('artifact')
    if not art:
        continue
    target = os.path.join(root, 'libraries', art['path'])
    fetch(art['url'], target)
    count += 1
print('biblioteci:', count)
index = data.get('assetIndex')
os.makedirs(os.path.join(root, 'assets', 'indexes'), exist_ok=True)
if index:
    fetch(index['url'], os.path.join(root, 'assets', 'indexes', index['id'] + '.json'))
    print('asset index:', index['id'])
PY
du -sh .minecraft 2>/dev/null

echo "== horizon-prepare"
PREPARE=$(find horizon-cli -maxdepth 3 -name horizon-prepare -type f | head -1)
echo "binar: ${PREPARE:-<lipsa>}"
if [ -z "${PREPARE}" ]; then
  echo "nu gasesc horizon-prepare; continut:"
  find horizon-cli -maxdepth 3 -type f | head -30
  exit 1
fi
chmod +x "$PREPARE"
"$PREPARE" --prepare --server-jar server.jar --minecraft-dir .minecraft
echo "prepare=$?"

echo "== caut registries.json"
find . -path "*horizon-client/26.3/registries.json" 2>/dev/null | head -5
du -sh ./horizon-cli/horizon-linux-x86_64/.local/horizon-client/26.3 2>/dev/null

DIR=$(find . -path "*horizon-client/26.3/registries.json" 2>/dev/null | head -1 | xargs -r dirname)
echo "folder pregatit: ${DIR:-<niciunul>}"
if [ -n "${DIR}" ]; then
  tar czf 26.3.tar.gz -C "$(dirname "$DIR")" "$(basename "$DIR")"
  ls -lh 26.3.tar.gz
else
  echo "ESEC: nu s-a produs folderul pregatit"
  exit 1
fi
