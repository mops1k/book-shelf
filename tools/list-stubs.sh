#!/usr/bin/env bash
# Рабочий бэклог проекта: печатает все маркеры STUB(Sprint N) по файлам и сводку по спринтам.
# Маркер ставится на каждую нереализованную часть (ADR-013), поэтому этот список —
# актуальное состояние работ, а не «пожелания в комментариях».
#
# Использование:
#   bash tools/list-stubs.sh            # все заглушки
#   bash tools/list-stubs.sh core/data  # только в указанном каталоге
set -euo pipefail

cd "$(dirname "$0")/.."
scope="${1:-.}"
if [ ! -e "$scope" ]; then
  echo "Каталог не найден: $scope" >&2
  exit 2
fi

out="$(mktemp)"
trap 'rm -f "$out"' EXIT

grep -rn --include='*.kt' --include='*.kts' -E 'STUB\(Sprint [0-9]+\)' "$scope" >"$out" || true

total="$(wc -l <"$out" | tr -d ' ')"
if [ "$total" -eq 0 ]; then
  echo "Заглушек не найдено — всё реализовано."
  exit 0
fi

echo "Всего заглушек: $total"
echo
echo "По спринтам:"
grep -o -E 'STUB\(Sprint [0-9]+\)' "$out" | sort | uniq -c | sed 's/^/  /'
echo
echo "По модулям:"
sed 's|^\./||' "$out" | cut -d: -f1 | cut -d/ -f1-2 | sort | uniq -c | sort -rn | sed 's/^/  /'
echo
echo "Файлы и места:"
sed 's|^\./||' "$out" | sort | sed 's/^/  /'
