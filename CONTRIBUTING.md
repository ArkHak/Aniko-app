# Участие в разработке Aniko

Спасибо, что хотите помочь! Issues и Pull Request'ы приветствуются. Если планируете крупную
фичу — сначала откройте issue и обсудите подход: так вы не потратите время на то, что не впишется
в архитектуру.

Правила ниже действуют и для людей, и для ИИ-агентов; подробности — в [`AGENTS.md`](AGENTS.md).

> **Как устроена работа над проектом.** Основная разработка ведётся вне этого репозитория, а сюда
> изменения попадают вместе с релизами. Ваши Issues и PR это не отменяет: мейнтейнер вручную переносит
> принятые правки в основную кодовую базу и сохраняет вас в авторстве (`Co-authored-by`).
> Поэтому PR лучше делать небольшими и самодостаточными.

## Git-флоу

1. Сделайте fork и клонируйте его; добавьте исходный репозиторий как `upstream`:
   ```bash
   git remote add upstream https://github.com/ArkHak/Aniko-app.git
   ```
2. **Всегда** отводите новую ветку от свежей `upstream/main` — не работайте в `main`:
   ```bash
   git fetch upstream
   git checkout -b feature/short-description upstream/main
   ```
   Префиксы: `feature/`, `fix/`, `docs/`, `refactor/`, `chore/`.
3. Коммиты — на английском, в формате [Conventional Commits](https://www.conventionalcommits.org/):
   `feat:`, `fix:`, `docs:`, `chore:`, `refactor:`, `test:`. Один коммит — одна законченная идея.
   ```
   feat(player): remember voice-over per title
   fix(desktop): release VLC player off the UI thread
   ```
4. Не коммитьте секреты и личные данные: `keystore.properties`, `*.jks`, `local.properties`, Apple
   Team ID (в `iosApp/Configuration/Config.xcconfig` он должен оставаться пустым).

## Проверки перед PR

Соберите затронутые таргеты и прогоните линтеры и тесты:

```bash
./gradlew ktlintCheck detektMetadataCommonMain detektDesktopMain :composeApp:compileKotlinDesktop
./gradlew :composeApp:desktopTest :shared:data:desktopTest

# при изменениях под конкретную платформу
./gradlew :composeApp:compileDebugKotlinAndroid
./gradlew :composeApp:compileKotlinIosSimulatorArm64   # только macOS
```

Изменения интерфейса проверьте на живом запуске (эмулятор или `./gradlew :composeApp:run`), а не
только компиляцией. Новые раскладки на `BoxWithConstraints` / `SubcomposeLayout` — с особой
осторожностью: в проекте это известный источник багов (нулевые constraints, устаревшее состояние
рядом с `AndroidView`).

## Конвенции кода

- **MVI**: экраны строятся на `BaseViewModel<State, Intent, Effect>`; ошибки — типизированные,
  текст для пользователя выбирает экран, а не ViewModel.
- **i18n**: все пользовательские строки — через `Strings` / `EnStrings` / `RuStrings`
  (`shared/ui/.../i18n`). Кириллица в коде вне i18n-слоя запрещена детект-правилом
  `ForbiddenCyrillicStringLiteral`.
- **expect/actual** вместо проверок платформы; максимум кода — в `commonMain`.
- **KDoc** на публичном API; detekt и ktlint — без новых записей в baseline.
- **Доступность**: `minTouchTarget >= 48dp`, контраст WCAG AA (4.5:1), `contentDescription` на
  интерактивных элементах (паттерн `clearAndSetSemantics`).
- **DI** — Koin.

## Pull Request

- Заголовок — по формату коммита; в описании — зачем изменение и как его проверить.
- Если PR закрывает issue — добавьте `Fixes #123`.
- Держите PR небольшим и сфокусированным; обновляйте ветку через `git rebase upstream/main`.

## Лицензия вклада

Отправляя код, вы соглашаетесь, что он распространяется на условиях проекта — [GPL-3.0](LICENSE).

## Полезные ссылки

- [`docs/REELWAVE_PLAN.md`](docs/REELWAVE_PLAN.md) — трекер разработки: фазы, решения, техдолг.
- [`docs/api/ANIXART_API.md`](docs/api/ANIXART_API.md) — описание API Anixart.
