# HANDOFF — пакет из 4 багфиксов (ветка `fix/legal-playback-block-player-fixes`)

Дата: 2026-09-24. Задача стартована в Kimi Work, продолжается в Kimi Code.
Ветка уже создана от свежего `origin/main`: `fix/legal-playback-block-player-fixes`
(все четыре фикса идут ОДНОЙ веткой — решение пользователя).

⚠️ **Рабочее дерево сейчас НЕ компилируется** — рефакторинг бага 1 сделан наполовину
(см. «Баг 1 → осталось»). Первый шаг в Kimi Code — довести его до компиляции.

Правила из AGENTS.md действуют: conventional commit (`fix:` …), работа только в этой ветке,
валидация перед завершением, push/PR — только по явному решению пользователя.

---

## Баг 1 — легализованный тайтл: блокировать воспроизведение из приложения

Требование пользователя: если тайтл легализован на территории страны (показываем note +
ссылку на Кинопоиск/платформы), воспроизведение с озвучками должно быть заблокировано —
как в официальном приложении Anixart (кнопки блокируются).

Контекст: фича легальных платформ добавлена в PR #106 (коммит b648aa8). Она прячет кнопку
«Смотреть» и сетку серий только при `is_third_party_platforms_disabled == true`, но в живых
ответах это поле пока не приходит вовсе — приходят `note` («Данный материал лицензирован на
территории вашей страны.») и список платформ. Deep link на серию блокировку не проверяет вовсе.

### Уже сделано

- `composeApp/.../release/ReleaseDetailsContract.kt` — добавлен предикат
  `val ReleaseDetailsUiState.isLicensedPlaybackBlocked: Boolean` =
  `details?.isThirdPartyPlatformsDisabled == true || !details?.note.isNullOrBlank() || streamingPlatforms.isNotEmpty()`.
  Fail-open при сетевых сбоях — задокументировано в KDoc предиката.
- `composeApp/.../release/ReleaseHeaderSection.kt`:
  - публичная `ReleaseHeaderSection` — добавлен параметр `playbackBlocked: Boolean`,
    прокинут во все три раскладки; KDoc обновлён;
  - `WideHeaderLayout` — параметр добавлен, `hideWatchAction = playbackBlocked`;
  - `CompactHeroHeader` — параметр добавлен в сигнатуру.

### Осталось

- `ReleaseHeaderSection.kt`:
  - `CompactHeroHeader`, вызов `HeroActionsRow` (≈строка 632): `hideWatchAction =
    details?.isThirdPartyPlatformsDisabled == true` → `hideWatchAction = playbackBlocked`;
  - `ExpandedDrawerHeader` (≈строка 684): добавить `playbackBlocked: Boolean` в сигнатуру
    и в вызове `HeroActionsRow` (≈строка 763) та же замена.
- `ReleaseDetailsScreen.kt` (≈строка 307, вызов `ReleaseHeaderSection` в `ReleaseDetailsContent`):
  передать `playbackBlocked = state.isLicensedPlaybackBlocked`.
- `ReleaseStreamingPlatformsSection.kt`, `EpisodesOrStreamingPlatformsSection` (строка 170):
  условие `state.details?.isThirdPartyPlatformsDisabled == true` → `state.isLicensedPlaybackBlocked`.
  Существующий текст `releaseThirdPartyPlatformsDisabledHint` («Правообладатель ограничил показ
  сторонних источников…») подходит — новые i18n-строки НЕ нужны. Обновить KDoc функции
  (сейчас говорит только про `isThirdPartyPlatformsDisabled`).
- `ReleaseDetailsViewModel.kt` — guards (defense in depth, чтобы обойти блокировку было нельзя
  даже через deep link):
  - `resolvePlayTarget()` (≈строка 340): после проверки release — `if
    (_uiState.value.isLicensedPlaybackBlocked) return null`;
  - `resolveDeepLinkEpisode()` (≈строка 429): тот же guard;
  - обновить KDoc обеих функций.
- Тесты:
  - `composeApp/src/desktopTest/.../smoke/ReleaseStreamingPlatformsSectionSmokeTest.kt` — обновить
    вызовы `ReleaseHeaderSection` (новый обязательный параметр `playbackBlocked`), добавить кейс:
    `note` без флага тоже скрывает Watch (через `playbackBlocked = true`);
  - юнит-тест предиката `isLicensedPlaybackBlocked` (триггеры: флаг / note / непустой список;
    fail-open при пустых данных) — можно в `composeApp/src/desktopTest/kotlin/com/aniko/app/feature/`.

---

## Баг 2 — чёрный экран после выхода из плеера (Desktop)

Симптом: после воспроизведения и «назад» — продолжительный чёрный экран. Пользователь
подтвердил: точно Desktop, возможно и другие платформы.

**Причина найдена при разведке.** `shared/player/src/desktopMain/.../EmbedPlayer.desktop.kt`,
`DesktopVlcjPlayer`: `DisposableEffect.onDispose` синхронно, на UI-потоке, вызывает
`mediaPlayer().controls().stop()` + `mediaPlayer().release()`. Teardown libVLC по сетевому
потоку блокируется на секунды. Последний отрисованный кадр главного окна — чёрный плеер
(`Surface(color = Color.Black)` + чёрный placeholder `EmbedPlayerView`), поэтому всё это время
пользователь видит чёрный экран.

**Фикс:** увести stop/release в фоновый daemon-поток (например
`Thread { runCatching { ...stop() }; runCatching { ...release() } }.apply { isDaemon = true;
name = "vlc-player-release"; start() }`). `controller?.detach()` оставить синхронным (он
лёгкий — снимает listener и гасит scope, см. `EmbedVideoController.desktop.kt`).

Проверка: живая на Desktop — `./gradlew :composeApp:run`, открыть серию, нажать «назад»,
экран списка/деталей должен появиться мгновенно. В смоук-тестах VLC заменён placeholder'ом
(`aniko.playerTestMode=true`), регрессию этим путём не поймать — нужен живой запуск.

---

## Баг 3 — текущая версия ПО в настройках

Сейчас в `SettingsScreen` версии нет; `versionName = "0.0.2"` задан только в Android-таргете
`composeApp/build.gradle.kts` (строка 132). Desktop-`packageVersion = "1.0.2"` — отдельная
версия macOS-инсталлятора, не версия приложения (см. комментарий в build.gradle.kts).

**План:**
- Единый источник версии в `composeApp/build.gradle.kts` (например `val anikoAppVersion =
  "0.0.2"`), использовать его и в `android { defaultConfig { versionName = … } }`.
- Кодогенерация `BuildInfo.kt` (`const val APP_VERSION: String`) в build-директорию,
  подключённую к `commonMain` — тот же паттерн, что `generateSmokeApiFixtures`
  (`GenerateApiFixturesTask`, провайдер-зависимость через `srcDir(task.flatMap { it.outputDir })`).
  Так версия доступна на всех трёх платформах без `expect/actual`.
- `SettingsScreen.kt`: некликабельный пункт внизу списка (перед/после «Выйти» — на усмотрение,
  логично в самом низу): заголовок «Версия приложения» + значение `APP_VERSION`.
- i18n: добавить `settingsAppVersion` в `Strings.kt` / `EnStrings.kt` / `RuStrings.kt`
  (секция settings). Смоук-тест: есть `DefaultVideoQualitySettingsSmokeTest` как образец
  паттерна для экрана настроек.

---

## Баг 4 — любимая озвучка по дефолту при воспроизведении

Требование пользователя: если в тайтле выбрана любимая (pinned) озвучка, при воспроизведении
она должна ставиться сразу. Сейчас `resolvePlayTargetChain`
(`composeApp/.../release/ReleaseDetailsViewModel.kt`, ≈строка 365) выбирает тип по приоритетам:
явный выбор чипами → `TitleVoicePreferenceStore` (dubbing memory) → первый в списке.

Факты из разведки:
- `VoiceType.pinned` (shared/model `Episode.kt`) — серверный флаг любимой озвучки, уже
  используется для сортировки чипов (`ReleaseEpisodesSection.kt` ≈строка 179) и пикера в плеере
  (`PlayerOverlay.kt` ≈строка 609). KDoc в `resolvePlayTarget` устарел: утверждает «нет поля
  pinned» — поправить.
- `LocalVoicePinStore` (`shared/data/.../voicepin/`) — локальные пины P16.T6, `pinnedIds():
  Flow<Set<Int>>`, id озвучек глобальные между релизами. Зарегистрирован в `DataModule`
  (`shared/data/.../di/DataModule.kt`); ViewModel создаются через `viewModelOf` в
  `composeApp/.../di/AppModule.kt` — инжектить туда.

**План:** приоритеты в `resolvePlayTargetChain`:
1. явный выбор чипами этого экрана (`selectedTypeId`);
2. серверный `VoiceType.pinned`;
3. локальный пин (`LocalVoicePinStore.pinnedIds().first()`);
4. dubbing memory (`TitleVoicePreferenceStore.load`);
5. первый в списке.

Пользователь подтвердил: «когда выбирается любимая озвучка в тайтле, при воспроизведении сразу
её ставить» — pinned намеренно выше dubbing memory. Решение зафиксировать в KDoc.
Тест: вынести выбор типа в чистую функцию (например `internal fun chooseDefaultVoiceType(...)`)
и покрыть табличным юнит-тестом (образец — `shouldStartLoad` в `PlayerViewModel.kt`).

---

## Финализация (после всех фиксов)

- Валидация: `./gradlew ktlintCheck detektMetadataCommonMain detektDesktopMain
  :composeApp:compileKotlinDesktop :composeApp:desktopTest` (+ `:shared:data:desktopTest`,
  если тронут data-слой). UI-изменения — живой запуск Desktop.
- Обновить `docs/REELWAVE_PLAN.md` (журнал изменений).
- Коммит(ы) conventional `fix:`, английский. Push/PR — только по явному решению пользователя.
