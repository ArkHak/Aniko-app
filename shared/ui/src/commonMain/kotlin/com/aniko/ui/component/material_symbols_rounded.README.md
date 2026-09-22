# `material_symbols_rounded.ttf` — как пересобрать subset

Файл лежит здесь, а не в `composeResources/font/` рядом со шрифтом, потому что генератор
ресурсов Compose Multiplatform (`generateResourceAccessorsForCommonMain`) сканирует ВСЁ
содержимое `composeResources/<каталог>/` и пытается построить typed-аксессор для каждого файла;
имя с точками (`material_symbols_rounded.README.md`) валит сборку ошибкой
`Can't escape identifier ... because it contains illegal characters: .`. Файл шрифта:
`shared/ui/src/commonMain/composeResources/font/material_symbols_rounded.ttf`.

## Что это

`material_symbols_rounded.ttf` — это **не статический** инстанс, а сам вариативный шрифт
Google `Material Symbols Rounded` (`MaterialSymbolsRounded[FILL,GRAD,opsz,wght]`),
обрезанный (`fonttools subset`) до нужных глифов. Оси `FILL`/`GRAD`/`opsz`/`wght` в файле
сохранены целиком — [AnixIcon] (`AnixIcon.kt` в этом же каталоге) выставляет их в рантайме
через `FontVariation.Settings` (`FILL` переключает outline↔filled, `wght=400`, `GRAD=0`,
`opsz=24` зафиксированы значениями мокапа). **Инстансировать в статику нельзя** — это сломает
переключение `filled` у всех мест, использующих `AnixIcon(..., filled = true)` (сейчас:
`grid_view`/`view_list` в переключателе вида «Мои списки», и другие).

Источник: `google/material-design-icons`, шрифт `Material Symbols Rounded`
(`MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf`). Лицензия шрифта — Apache License 2.0
(см. `LICENSE` в репозитории `google/material-design-icons`), сабсеттинг и модификация разрешены.

## Список глифов и словарь codepoint'ов

Единственный источник правды — словарь [MaterialSymbolsCodepoints] (`MaterialSymbolsCodepoints.kt`
в этом же каталоге): имя иконки → Unicode codepoint (Private Use Area). Subset строится **по
этому словарю**: берём весь список codepoint'ов из `MaterialSymbolsCodepoints.map`, отдаём
`fonttools subset` через `--unicodes`. Заполненные (`.fill`) варианты (`grid_view.fill`,
`home.fill`, `view_list.fill` и т.д.) в subset не запрашиваются явно — они подтягиваются
автоматически через closure GSUB-фичи `rclt` (условная замена outline→filled при `FILL` ∈
[0.99, 1.0]), которая в шрифте участвует в замыкании по умолчанию (`rclt` входит в дефолтный
набор `--layout-features` у `pyftsubset`).

Если добавляется новое имя иконки — сначала добавить codepoint в `MaterialSymbolsCodepoints.kt`,
затем пересобрать subset этой командой (актуальный список `--unicodes` формируется прямо из
словаря — см. команду ниже).

## Команда пересборки (2026-09-22, добавление `view_list`)

Полный вариативный шрифт (~15 МБ, не хранится в репозитории) был скачан отдельно
из `google/material-design-icons` (variable font `MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf`,
`fontRevision 2.971`, в шрифте репозитория на тот момент была версия `2.967` — минорная
апстрим-дельта, контуры всех 31 использовавшихся на тот момент глифов побайтово совпали, см.
проверку ниже).

```bash
# 1. Список codepoint'ов берём прямо из MaterialSymbolsCodepoints.kt (имя → 0xNNNN):
python3 - <<'EOF'
import re
text = open("shared/ui/src/commonMain/kotlin/com/aniko/ui/component/MaterialSymbolsCodepoints.kt").read()
pairs = re.findall(r'"([a-z_0-9]+)"\s+to\s+0x([0-9a-fA-F]+)', text)
codepoints = sorted(int(cp, 16) for _, cp in pairs)
print(",".join(f"{c:04x}" for c in codepoints))
EOF
# -> e034,e037,e043,e044,e04a,e056,e059,e1c4,e2c1,e5c4,e5c8,e5cd,e5d0,e7f5,e80d,e836,e87e,e8b8,
#    e8d5,e8e7,e8ef,e8f4,e8f5,e8fd,e911,e9b0,e9b2,ebcc,ef7a,f09a,f0be,f0d3   (32 шт., 2026-09-22)

# 2. Subset полного вариативного шрифта по этому списку (сохраняет fvar/gvar/avar/HVAR/STAT/GSUB):
python3 -m fontTools.subset MaterialSymbolsRounded-full.ttf \
  --output-file=material_symbols_rounded.ttf \
  --unicodes=<список из шага 1> \
  --glyph-names \
  --notdef-outline
```

Флаги подобраны так, чтобы воспроизвести структуру файла, уже лежавшего в репозитории
(сравнение таблиц/`post`/`name`/hinting до правки):
- **без `--no-hinting`** — у оригинального файла таблица `prep` (7 байт) сохранена, значит
  хинтинг не выключали явно (сам шрифт и так не хинтован — `fpgm`/`cvt ` отсутствуют что в
  оригинале, что в апстриме, поэтому на итоговый размер флаг всё равно не повлиял бы).
- **`--glyph-names`** — у оригинала `post`-таблица версии 2.0 с настоящими именами глифов
  (не anonymous-glyph3), это НЕ дефолт `pyftsubset` (дефолт — версия 3.0 без имён).
- **`--notdef-outline`** — у оригинала `.notdef` — не пустой бокс, а реальный контур
  (5 контуров, форма «коробка с крестом»); дефолт `pyftsubset` даёт пустой `.notdef`.
- **`--layout-features`** не переопределялся — дефолтный набор `pyftsubset` уже включает `rclt`
  (фича, на которой держится переключение `FILL` в этом шрифте), поэтому GSUB-замыкание для
  `.fill`-вариантов срабатывает без дополнительных флагов.
- **`--desubroutinize`** не нужен — шрифт использует `glyf`/`loca` (TrueType-контуры), не `CFF`.

## Что изменилось в этой правке (добавление `view_list`, U+E8EF)

- Codepoint `view_list` = `U+E8EF` подтверждён по официальному файлу codepoint'ов
  `google/material-design-icons` и напрямую по `cmap` скачанного полного шрифта
  (`0xE8EF -> "view_list"`).
- В исходном файле репозитория (до этой правки) помимо 31 иконки из словаря были ещё 4 «мёртвых»
  глифа без записи в `cmap` — `delete`, `delete.fill`, `restore_from_trash`,
  `restore_from_trash.fill` (не адресуются никаким codepoint'ом, значит не достижимы через
  `AnixIcon` ни при каких обстоятельствах — по всей видимости, остаток от более раннего этапа
  разработки). Явный `--glyphs=` для них в команде выше **намеренно не указан** — они не входят
  в текущий словарь `MaterialSymbolsCodepoints.kt`, поэтому новый subset их не содержит. Это
  сознательное отклонение от побитового повторения старого файла (см. отчёт в PR/коммите),
  дополнительно уменьшает размер и не может быть регрессией — эти глифы были и остаются
  недостижимыми из кода.
- Все 31 ранее использовавшихся глифа проверены на полную идентичность (контуры `glyf`,
  `gvar`-дельты, `hmtx`, записи `cmap`) между старым файлом и новой сборкой — 0 отличий.
- Новый файл: 98 160 байт (было 99 076 байт) — при том, что добавлен новый глиф `view_list` +
  автоматически подтянутый `view_list.fill`, а 4 недостижимых глифа выше исключены.
