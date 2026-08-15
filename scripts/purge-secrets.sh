#!/usr/bin/env bash
# ============================================================================
# Purga las claves de Backblaze B2 del historial de git de Vivid.
#
# Los valores viejos (ya revocados) quedaron en el historial público dentro
# de vivid-app/app/src/main/java/com/vivid/app/di/BuildConfigSecrets.kt
# (commit ea24dea en adelante). Este script reescribe TODO el historial:
#   1. elimina el archivo BuildConfigSecrets.kt de todos los commits, y
#   2. reemplaza los literales viejos por ***REMOVED*** en cualquier archivo
#      o mensaje de commit.
#
# Uso:
#   bash scripts/purge-secrets.sh \
#     'K0043ske+MzlEoRWXQtmJ18opgnipXQ' \      # B2_APPLICATION_KEY vieja
#     '0044482642d8bb00000000005' \             # B2_KEY_ID viejo
#     '4482642d8bb0' \                          # B2_ACCOUNT_ID (master key)
#     '94c488b2a624f22d98eb0b10'                # B2_BUCKET_ID viejo
#
#   # Solo valida en un clon temporal (sin tocar GitHub):
#   bash scripts/purge-secrets.sh 'K004...' ... 
#   # Valida Y hace force-push de main + tags:
#   bash scripts/purge-secrets.sh 'K004...' ... --push
#
# Requisitos: git-filter-repo (pip install git-filter-repo).
#
# ⚠️ Después del force-push:
#   - Los clones/fork existentes siguen teniendo los commits viejos.
#   - Los PRs abiertos pueden seguir exponiendo los commits viejos: cerrá
#     los PRs antes de purgar.
#   - GitHub puede conservar datos en caché/forks: si querés un borrado
#     completo, contactá GitHub Support (https://support.github.com) para
#     que purguen vistas cacheadas tras un force-push de este tipo.
# ============================================================================
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/santes210/Vivid.git}"
BRANCH="${BRANCH:-main}"
PUSH_FLAG=""
SECRETS=()

for arg in "$@"; do
  if [ "$arg" = "--push" ]; then
    PUSH_FLAG="1"
  else
    SECRETS+=("$arg")
  fi
done

if [ "${#SECRETS[@]}" -eq 0 ]; then
  echo "Uso: $0 '<valor_secreto_viejo>' [otro valor...] [--push]" >&2
  exit 1
fi

if ! command -v git-filter-repo >/dev/null 2>&1; then
  echo "Falta git-filter-repo. Instalalo con: pip install git-filter-repo" >&2
  exit 1
fi

WORKDIR="$(mktemp -d /tmp/vivid-purge-XXXXXX)"
echo "== Clonando $REPO_URL en $WORKDIR ..."
git clone --quiet "$REPO_URL" "$WORKDIR/repo"
cd "$WORKDIR/repo"
git checkout --quiet "$BRANCH"

# Archivo de reemplazos (fuera del repo, en el dir temporal).
REPLACEMENTS="$WORKDIR/replacements.txt"
: > "$REPLACEMENTS"
for secret in "${SECRETS[@]}"; do
  # Solo strings no vacíos; los nombres de constantes no se tocan.
  if [ -n "$secret" ]; then
    printf 'literal:%s\n' "$secret" >> "$REPLACEMENTS"
  fi
done

echo "== Reescribiendo historial (git filter-repo)..."
git filter-repo --force \
  --invert-paths \
  --path-glob '*BuildConfigSecrets.kt' \
  --replace-text "$REPLACEMENTS"

echo "== Verificando que no queden claves en el historial..."
FAIL=0

if git log --all --oneline -- '*BuildConfigSecrets.kt' | grep -q .; then
  echo "ERROR: todavía hay commits que tocan BuildConfigSecrets.kt" >&2
  FAIL=1
fi

for secret in "${SECRETS[@]}"; do
  if [ -n "$secret" ] && git log -S "$secret" --all --oneline | grep -q .; then
    echo "ERROR: el valor '$secret' sigue presente en el historial" >&2
    FAIL=1
  fi
done

if [ "$FAIL" -ne 0 ]; then
  echo "== La purga NO quedó limpia. Revisá el repo en $WORKDIR/repo" >&2
  exit 1
fi

echo "== OK: BuildConfigSecrets.kt eliminado del historial y literales scrubbeados."

if [ -n "$PUSH_FLAG" ]; then
  echo "== Force-push de TODAS las refs (--mirror) a origin..."
  # git filter-repo elimina el remote origin: lo restauramos.
  # Se usa --mirror porque cualquier otra branch/tag con el historial viejo
  # (p. ej. branches arena/*) mantendría las claves expuestas en GitHub.
  git remote add origin "$REPO_URL"
  git push --force --mirror origin
  echo "== Hecho. Revisá que los PRs estén cerrados y avisá a los forks."
else
  echo "== Modo validación: no se pusheó nada. Repo reescrito en $WORKDIR/repo"
  echo "   Para pushear, reejecutá con --push."
fi
