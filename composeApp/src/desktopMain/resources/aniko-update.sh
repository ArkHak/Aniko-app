#!/bin/sh
# Замена Aniko.app новой версией. Запускается MacOsAppUpdateInstaller отдельным процессом уже после
# того, как приложение попросило себя завершить. Все пути приходят позиционными аргументами
# (никакой интерполяции недоверенных строк в текст скрипта нет).
#
# Аргументы:
#   1 PID       процесс приложения, завершения которого нужно дождаться
#   2 NEW_APP   новый Aniko.app внутри смонтированного DMG
#   3 TARGET    текущий Aniko.app, который заменяем
#   4 MOUNT     точка монтирования DMG (размонтируем в конце)
#   5 DMG       скачанный файл (удаляем после успешного обновления)
#   6 OPEN      команда запуска приложения (по умолчанию `open`; в тестах подменяется)
#   7 LOG       файл лога
#   8 VERIFY    сколько секунд ждать, что новое приложение запустилось (по умолчанию 8)
PID="$1"
NEW_APP="$2"
TARGET="$3"
MOUNT="$4"
DMG="$5"
OPEN="${6:-open}"
LOG="$7"
VERIFY="${8:-8}"

if [ -n "$LOG" ]; then
  exec >>"$LOG" 2>&1
fi

STAGING="$TARGET.new"
BACKUP="$TARGET.old"

say() { printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
detach_dmg() {
  hdiutil detach "$MOUNT" -force >/dev/null 2>&1
  rmdir "$MOUNT" 2>/dev/null
}
# Любой сбой до подмены оставляет старую версию на месте и просто запускает её снова.
give_up() {
  say "$1"
  detach_dmg
  "$OPEN" "$TARGET"
  exit 1
}

# 1. Ждём выхода приложения (до минуты).
tries=0
while kill -0 "$PID" 2>/dev/null; do
  tries=$((tries + 1))
  if [ "$tries" -gt 120 ]; then
    give_up "the app did not exit in time"
  fi
  sleep 0.5
done

# 2. Копируем новую версию рядом с текущей: ditto сохраняет подпись и атрибуты bundle.
rm -rf "$STAGING" "$BACKUP"
if ! ditto "$NEW_APP" "$STAGING"; then
  rm -rf "$STAGING"
  give_up "ditto failed"
fi
xattr -cr "$STAGING" 2>/dev/null

# 3. Подменяем; старую версию держим как .old до успешного запуска новой.
if ! mv "$TARGET" "$BACKUP"; then
  rm -rf "$STAGING"
  give_up "cannot move the old app aside"
fi
if ! mv "$STAGING" "$TARGET"; then
  mv "$BACKUP" "$TARGET"
  rm -rf "$STAGING"
  give_up "cannot move the new app into place"
fi

# 4. Размонтируем, запускаем и убеждаемся, что процесс новой версии живёт; иначе откат.
detach_dmg
"$OPEN" "$TARGET"
sleep "$VERIFY"
if pgrep -f "$TARGET/Contents/MacOS/" >/dev/null 2>&1; then
  rm -rf "$BACKUP"
  rm -f "$DMG"
  say "updated"
else
  rm -rf "$TARGET"
  mv "$BACKUP" "$TARGET"
  "$OPEN" "$TARGET"
  say "the new app did not start; rolled back"
  exit 1
fi
