# Anixart API — сводная документация

Консолидированное описание REST API Anixart (`api-s.anixsekai.com`) — стороннего сервиса, вокруг которого
построен клиент Aniko. Сам API и его база (`anixart_9-0-beta-19.apk`, пакет `com.swiftsoft.anixartd`) — чужие,
названия оригинального сервиса (Anixart) намеренно не переименовываются; переименован только наш проект-клиент (Aniko).

Источники: декомпиляция `anixart_9-0-beta-19.apk` (`docs/api/jadx-out(-21)/sources/.../network/api|request|response/**`,
цитаты оттуда помечены `[APK]`), живые сэмплы реального трафика (`docs/api/samples/*.json`) и уже реализованный
Kotlin-клиент (`shared/network`, `shared/data/.../api/*.kt`). Значения, не найденные статически и требующие живой
проверки, помечены `[TODO: verify live]`. Этот файл объединяет и заменяет собой черновик первого прохода
(ранее `docs/api/ENDPOINTS.md`).

## 1. Базовая конфигурация

| Параметр | Значение | Источник |
|---|---|---|
| Base URL | `https://api-s.anixsekai.com/` | `ConstantNetFetcher.java`, `StartPresenter.API_BASE_URL`, `ApiConfig.DEFAULT_BASE_URL` |
| Аутентификация | query-параметр `?token=` (**не заголовок**) на каждом запросе | все `*Api.java` (`@Query("token") String token`), `AnixTokenPlugin.kt` |
| Заголовок `API-Version` | объявлен у `SearchApi.releaseSearch`, `RelatedApi.related`, `ProfilePreferenceApi.changeEmail`; точное значение не найдено статически. **Живой тест показал, что сервер его не требует** — `POST search/releases/0` без заголовка вернул `200 OK` (`docs/api/samples/search_releases_page0_no_api_version_header.json`) | `ApiConfig.apiVersionHeader = null` |
| Формат ошибок | HTTP-статус почти всегда `200`; реальный результат — в поле `code` тела ответа (`Response.java`) | `ApiCall.kt` |
| Коды `code` | `0` = SUCCESSFUL, `1` = FAILED, `402` = BANNED, `403` = PERM_BANNED | `Response.java`, `ApiCall.CODE_OK` |
| Resiliency base-URL | **В текущей реализации отсутствует.** Задокументированная (в декомпиле оригинального APK) цепочка: `ConstantNetFetcher` → `ConfigNetFetcher` (`config/urls`) → `FirebaseNetFetcher` → `GithubPagesNetFetcher` (fallback `anixhelper.github.io`). В проекте Aniko базовый URL — константа (`DEFAULT_BASE_URL = "https://api-s.anixsekai.com/"`); `baseUrl` вынесен параметром конфигурации (`ApiConfig.kt`, строка 12) для будущей реализации fallback-цепочки, но сама логика переключения не кодирована — это план развития, а не текущая фишка. Реализация может быть добавлена в `AnixHttpClient.kt` как plugin, см. P0.T7 в `docs/REELWAVE_PLAN.md`. | `ApiConfig.kt`, `AnixHttpClient.kt`, `ChainedNetFetcher.java` (APK) |
| Статика/CDN | Реальные хосты постеров/аватаров/скриншотов — `https://s.anixmirai.com/posters/...`; отдельный хост `https://s3.anixmirai.com/voiceovers/...` — только для озвучек. Исправлено в `ApiConfig.kt`: `DEFAULT_STATIC_BASE_URL` теперь `https://s.anixmirai.com/` (был неверный `static.anixart.tv`) | сэмплы в `docs/api/samples/`, `ApiConfig.kt` |
| JSON-парсинг | Android-клиент — Jackson (`@JsonProperty`); в проекте — kotlinx.serialization с `ignoreUnknownKeys=true, isLenient=true, coerceInputValues=true` (осознанная терпимость к недокументированному/меняющемуся API) | `shared/network` |
| 401/403 | Инвалидируют локальную сессию везде, **кроме** `auth/*` (там это «неверный пароль», а не «токен протух») | `shared/network` HttpResponseValidator |

## 2. Пагинация

`PageableResponse<T>` (наследует `Response`, содержит `code`):

- `content: T[]`
- `current_page: Int`
- `total_page_count: Int`
- `total_count: Int`

Явного булева «есть следующая страница» нет — вычисляется как `current_page < total_page_count - 1`.

## 3. Аутентификация — `AuthApi`

| Метод | Путь | Параметры | Ответ |
|---|---|---|---|
| POST | `auth/checkLogin` | form: `login` | `CheckLoginResponse` |
| POST | `auth/signIn` | form: `login`, `password` | `SignInResponse{ profile, profileToken{ id, token } }` |
| POST | `auth/signUp` | form: `login`, `email`, `password` | `SignUpResponse` |
| POST | `auth/google` | form: `googleIdToken` (+`login,email` на signUp) | `GoogleResponse` |
| POST | `auth/vk` | form: `vkAccessToken` (+`login,email` на signUp) | `VkResponse` |
| POST | `auth/telegram` | form: `telegramIdToken` (+`login,email` на signUp) | `TelegramResponse` |
| POST | `auth/verify` | form: `login,email,password,vkAccessToken,googleIdToken,telegramIdToken,hash,code` | `VerifyResponse` |
| POST | `auth/resend` | form: `login,email,password,vkAccessToken,googleIdToken,telegramIdToken,hash` | `ResendResponse` |
| POST | `auth/restore` | form: `data` | `RestoreResponse` |
| POST | `auth/restore/resend` | form: `data,password,hash` | `RestoreResendResponse` |
| POST | `auth/restore/verify` | form: `data,password,hash,code` | `RestoreVerifyResponse` |
| POST | `auth/firebase` | query: `token` | `FirebaseResponse` |

`SignInResponse.profileToken.token` — это и есть значение, идущее во все последующие `?token=`.

**В проекте (`AuthApi.kt`) реализован только `signIn`.**

## 4. Каталог, поиск, рекомендации

**`DiscoverApi`**
- `POST discover/interesting` → `PageableResponse<Interesting>` — **не требует токена, публичный**
- `POST discover/recommendations/{page}?previous_page=&token=` → `PageableResponse<Release>`
- `POST discover/watching/{page}?token=` → `PageableResponse<Release>`
- `POST discover/discussing?token=` → `PageableResponse<Release>`
- `POST discover/comments` → `PageableResponse<ReleaseComment>`

**`SearchApi`** (body: `SearchRequest`, query: `token`)
- `search/releases/{page}` → живой ответ — обычный `PageableResponse<Release>` (`code/content/current_page/...`). **Расходится с decompiled `ReleaseSearchResponse.java`** (`{ related, releases }`) — вероятно, сервер изменился после версии APK 9.0-beta-19.
- `search/feed/{page}` → `FeedSearchResponse`
- `search/profile/list/{status}/{page}`, `search/favorites/{page}`, `search/history/{page}`, `search/collections/{page}`, `search/profiles/{page}`, `search/channels/{page}`, `search/articles/{page}` → аналогично, `PageableResponse<...>`

**`FilterApi`** — `POST filter/{page}?extended_mode=&token=`, body `FilterRequest` — расширенный фильтр каталога (жанры/год/статус/тип и т.п.). Ответ — обычный `PageableResponse<Release>` (проверено вживую 2026-08-10, `POST filter/0` с телом `{"sort":0,"genres":[],"types":[],"age_ratings":[],"profile_list_exclusions":[]}` → HTTP 200).

Поля `FilterRequest` (`FilterRequestDto.kt`, все опциональны кроме `sort`/списков/`isGenresExcludeModeEnabled`, у которых есть дефолты):

| Kotlin-поле | JSON-ключ | Тип | Дефолт |
|---|---|---|---|
| `categoryId` | `category_id` | `Long?` | `null` |
| `statusId` | `status_id` | `Long?` | `null` |
| `startYear` | `start_year` | `Int?` | `null` |
| `endYear` | `end_year` | `Int?` | `null` |
| `studio` | `studio` | `String?` | `null` |
| `source` | `source` | `String?` | `null` |
| `episodesFrom` | `episodes_from` | `Int?` | `null` |
| `episodesTo` | `episodes_to` | `Int?` | `null` |
| `sort` | `sort` | `Int` | `0` |
| `country` | `country` | `String?` | `null` |
| `season` | `season` | `Int?` | `null` |
| `episodeDurationFrom` | `episode_duration_from` | `Int?` | `null` |
| `episodeDurationTo` | `episode_duration_to` | `Int?` | `null` |
| `genres` | `genres` | `List<String>` | `[]` |
| `profileListExclusions` | `profile_list_exclusions` | `List<Int>` | `[]` |
| `types` | `types` | `List<Long>` | `[]` |
| `ageRatings` | `age_ratings` | `List<Int>` | `[]` |
| `isGenresExcludeModeEnabled` | `is_genres_exclude_mode_enabled` | `Boolean` | `false` |
| `genresMode` | `genres_mode` | `Int?` | `null` |

Именованные константы (`FilterRequestDto.Companion`, из декомпила `FilterRequest.java`):
- `sort`: `0`=дата обновления убыв., `1`=оценка убыв., `2`=год убыв., `3`=популярность убыв., `4`=дата обновления возр., `5`=оценка возр., `6`=год возр., `7`=популярность возр.
- `genresMode`: `0`=ALL, `1`=ANY, `2`=EXCLUDE.
- `ageRatings`: `1`=`LESS_THAN_13`, `2`=`MORE_THAN_13`, `3`=`MORE_THAN_26`, `4`=`MORE_THAN_100` — судя по именам констант в декомпиле, это, вероятно, диапазоны **количества серий**, а не возрастной рейтинг, несмотря на название поля; не подтверждено вживую, оставлено как наблюдение.

**`TypeApi`** — управление типами озвучки: pin/unpin, hide-widget.

**В проекте реализованы `SearchApi.releaseSearch` и `FilterApi.filter`.**

## 5. Релиз

**`ReleaseApi`**
- `GET release/{r_id}?extended_mode=&token=` → `ReleaseResponse`
- `GET release/random?extended_mode=&token=` → `ReleaseResponse`
- `GET release/vote/add/{r_id}/{vote}?token=` → `VoteReleaseResponse`
- `GET release/vote/delete/{r_id}?token=` → `DeleteVoteReleaseResponse`

**`RelatedApi`** — `related/{relatedId}/{page}` (заголовок `API-Version`) — сиквелы/приквелы/спин-оффы.

**`ReleaseCommentApi`** — комментарии к релизу (P3.T12, `shared/data/.../api/ReleaseCommentApi.kt`, реализовано полностью):

| Метод | Путь | Параметры | Ответ |
|---|---|---|---|
| GET | `release/comment/all/{releaseId}/{page}` | query: `sort`, `token` | `PageableResponse<ReleaseComment>` — живьём проверен 2026-08-10 |
| GET | `release/comment/{id}` | query: `token` | `ReleaseComment` — одиночный, не пагинированный; в decompile параметр назван `releaseId`, но по форме пути (рядом с `.../delete/{commentId}`, `.../edit/{commentId}`) вероятнее означает id комментария, `[TODO: verify live]` |
| POST | `release/comment/add/{releaseId}` | body `CommentAddRequest{parentCommentId?, replyToProfileId?, message, spoiler}`, query: `token` | `CommentAddResponse<ReleaseComment>{code, comment}` |
| POST | `release/comment/edit/{commentId}` | body `CommentEditRequest{message, spoiler}`, query: `token` | `CommentEditResponse{code}` — коды: `2`=NOT_FOUND, `3`=TOO_SHORT, `4`=TOO_LONG, `5`=NOT_OWNED, `6`=WAS_DELETED, `7`=EMBEDDABLE_NOT_FOUND |
| GET | `release/comment/delete/{commentId}` | query: `token` | `CommentDeleteResponse{code}` — коды: `2`=NOT_FOUND, `3`=NOT_OWNED |
| GET | `release/comment/vote/{commentId}/{vote}` | query: `token` | `SimpleResponse{code}` |
| POST | `release/comment/replies/{commentId}/{page}` | query: `sort`, `token` | `PageableResponse<ReleaseComment>` |
| GET | `release/comment/votes/{commentId}/{page}` | query: `sort`, `token` | `PageableResponse<Profile>` в decompile; реализовано как `PageableResponse<ProfileCompact>` по аналогии с профилем внутри комментария (`[TODO: verify live]`) |
| GET | `release/comment/all/profile/{p_id}/{page}` | query: `sort`, `token` | `PageableResponse<ReleaseComment>` — комментарии произвольного профиля |
| POST | `release/comment/process/{commentId}` | body `CommentProcessRequest{message?, reason?, banReason?, banExpires?, isSpoiler, isDeleted, isBanned}`, query: `token` | `SimpleResponse{code}` — модераторский эндпоинт, не для обычных пользователей |

Первые ~5 комментариев релиза уже приходят прямо в `GET release/{id}?extended_mode=true` → `comments[]` (тот же формат `ReleaseComment`), без похода в `ReleaseCommentApi` — см. аудит Title Detail в `docs/REELWAVE_PLAN.md`.

**`ReleaseStreamingPlatformApi`** — легальные стриминг-площадки, где доступен релиз.

### Модель `Release` (крупнейшая, ~906 строк decompiled)

Ключевые поля (Jackson `@JsonProperty`, подтверждены сэмплом `release_186_extended.json`):
`age_rating`, `title_ru` / `title_original` / `title_alt`, `episodes_released` / `episodes_total`,
`profile_list_status`, `is_favorite`, `is_viewed`, `your_vote`, `vote_1_count`…`vote_5_count`,
`related_releases`, `recommended_releases`, `episode_last_update`, `genres`, `status`, `poster`, `year` и др.

> **Полиморфное поле `episode_last_update`** (живой кейс 2026-09-17, релиз id 20236): обычно
> unix-timestamp числом или `null`, но в `extended_mode=true` может прийти объектом
> `{"last_episode_update_date": <unix-ts>, ...}` — без ленивого парсера роняет всю карточку
> (`JsonConvertException: Expected numeric literal`). Обработано `EpisodeLastUpdateSerializer`
> (`shared/data/dto`), тот же класс полиморфии, что у `last_view_episode` (см. открытый вопрос №6).

## 6. Серии и источники видео — цепочка резолвинга плеера

**`EpisodeApi`**

| Метод | Путь | Смысл |
|---|---|---|
| GET | `episode/{releaseId}?token=` | `TypesResponse{ types: Type[] }` — типы озвучки/перевода |
| GET | `episode/{releaseId}/{typeId}` | `SourcesResponse{ sources: Source[] }` — источники (kodik/sibnet/vk/...) для этого типа |
| GET | `episode/{releaseId}/{typeId}/{sourceId}?sort=&token=` | `EpisodeResponse{ episodes: Episode[] }` — серии у источника |
| GET | `episode/target/{releaseId}/{sourceId}/{position}` | `EpisodeTargetResponse{ episode: Episode }` — **резолв конкретной серии в playable URL** |
| POST | `episode/watch/{releaseId}/{sourceId}[/{position}]?token=` | отметить просмотренным |
| POST | `episode/unwatch/{releaseId}/{sourceId}[/{position}]?token=` | снять отметку |
| GET | `episode/updates/{releaseId}/{page}` | `PageableResponse<EpisodeUpdate>` |

Цепочка: `types` → выбрать `typeId` → `sources(typeId)` → выбрать `sourceId` → `episodes(releaseId, typeId, sourceId)` → список серий → `episodeTarget(releaseId, sourceId, position)` → финальный playable-объект.

**Формат `Episode` из `episode/target` (подтверждено живыми сэмплами, снимает старый `[TODO: verify live]`):**
- `url: String` — ссылка на страницу плеера (см. ниже: прямого потока не отдаёт ни один хост)
- `iframe: Boolean` — поле есть в ответе, назначение неясно; **для ветвления не годится**

**Живая проверка Фазы 8 (P8.T2) — что закрыто окончательно:**

1. **Прямого m3u8/mp4 из API не отдаёт ни один хост.** `episode/target` всегда возвращает HTML-страницу embed-плеера. Соответственно `PlaybackSource.Direct` в `:shared:player` не используется вообще, всё идёт через `PlaybackSource.Embed`.
2. **`iframe` не коррелирует с «прямой поток vs embed»** — вопреки старой формулировке в этом файле. `Sibnet`, `Libria`/`Liberty`, `RuTube` и `VK Видео` приходят с `iframe: false`, и все четыре — HTML-страницы плеера. Отдельно `kodik` → `iframe: true`, ссылка на `kodikplayer.com`; страницу нужно грузить в `<iframe>` c `Referer: https://anixmirai.com/` (иначе `500 "Error code: ds"`).
3. **`Libria`/`Liberty` — уточнение старой пометки «предположительно прямой поток».** Это неверно: API отдаёт embed-страницу `anixart.libria.fun/public/iframe.php`. Но сама эта страница содержит готовые `.m3u8` (480/720/1080) прямо в HTML — их можно достать простым regex, без JS-движка. То есть прямой поток там теоретически достижим, но **не из ответа API**, а только доп. запросом к embed-странице; в приложении это не используется.

Источники, встречающиеся в парсерах (`utils/parser/*`): `kodik`, `sibnet`, `rutube`, `vkvideo`, `okru`, `mailru`, `myvi`, `allvideo`, `anilibria`, `sovetromantica`, `studiomir`, `torlook`.

**Имена источников в живом ответе ≠ этим ключам.** Живая выборка ~470 пар (релиз, тип озвучки) даёт человекочитаемые имена: `Kodik`, `Sibnet`, `RuTube`, `VK Видео`, `Libria`/`Liberty` (= `anilibria`), `TSM` (= `studiomir`), `Sovet (не работает)` (= `sovetromantica`). Хосты `okru`/`mailru`/`myvi`/`allvideo`/`torlook` в этой выборке не встретились ни разу — вероятно мёртвые записи из старого декомпила, но из `VideoHost` не удалены. Опознание в проекте идёт по домену URL (`VideoHost.fromUrl`), имя — только fallback (`VideoHost.fromKey`).

Домены, по которым хост опознаётся: `kodikplayer.com`/`aniqit.com`/`kodik.*` → KODIK, `sibnet.ru` → SIBNET, `libria.fun` → ANILIBRIA, `vk.com`/`vkvideo.ru` → VK_VIDEO, `rutube.ru` → RUTUBE, `sovetromantica.com` → SOVET_ROMANTICA, `studiomir.club` → STUDIO_MIR.

**`ReleaseVideoApi`** — отдельная фича «видео о релизе» (трейлеры/AMV), не путать с эпизодами:
- `GET /video/release/{releaseId}` → `ReleaseVideosResponse`
- `GET /video/release/{releaseId}/{page}` → `PageableResponse<ReleaseVideo>`
- `GET /video/release/categories` → `ReleaseVideoCategoriesResponse`

**`ReleaseVideoAppealApi` / `ReleaseVideoFavoriteApi`** — апелляции и избранное для этих видео.

**В проекте (`EpisodeApi.kt`) реализована полная цепочка types → sources → episodes → target + watch/unwatch.** `ReleaseVideoApi` не реализован.

## 7. Списки, синхронизация, избранное, история

Статусы списков (`ui/model/common/TopReleaseModel.java`, `R.string.status_*`):

| int | Значение |
|---|---|
| 1 | смотрю |
| 2 | в планах |
| 3 | просмотрено |
| 4 | отложено |
| 5 | брошено |

**`ProfileListApi`**
- `GET profile/list/add/{status}/{r_id}?token=` / `profile/list/delete/{status}/{r_id}?token=` → `ProfileListResponse`
- `GET profile/list/all/{status}/{page}?sort=&filter_announce=&token=` → `PageableResponse<Release>` — свой список
- `GET profile/list/all/{p_id}/{status}/{page}?sort=&filter_announce=&token=` → список другого профиля

**`FavoriteApi`**
- `GET favorite/add/{r_id}?token=` / `favorite/delete/{r_id}?token=` → `FavoritesResponse`
- `GET favorite/all/{page}?sort=&filter_announce=&token=` → `PageableResponse<Release>`

**`HistoryApi`** — по аналогии с Favorite: `history/{page}`, `history/delete/{r_id}`.

**Живая находка, задокументирована в коде проекта (расходится с UI-подписями декомпила):**
`sort=1` (а не `0`) на `profile/list/all` и `favorite/all` — это «сначала недавно добавленные/изменённые». Подписи в `res/values/arrays.xml` APK вводят в заблуждение — проверено вживую.

**`ProfileReleaseVoteApi`** — списки оценённых/неоценённых релизов профиля.

**`ExportApi` / `ImportApi`** — экспорт/импорт закладок (bookmarks) целиком.

**В проекте реализованы `FavoriteApi`, `HistoryApi`, `ProfileListApi` целиком.**

## 8. Профиль

**`ProfileApi`** — `GET profile/{id}` → карточка профиля (статистика, избранное, списки). В проекте реализован только этот метод.

**`ProfilePreferenceApi`** — смена email/пароля/логина, привязка VK/Google/Telegram/Yandex, темы оформления, настройки приватности, значки. В проекте реализованы privacy-эндпоинты и `badge/all/{page}` (см. ниже).

**`ProfileBadgeApi`** (decompile) — в реальности реализовано как метод `ProfilePreferenceApi.badges()` в проекте, путь того же неймспейса `profile/preference/badge/...`:

- `GET profile/preference/badge/all/{page}` → `PageableResponse<Badge>` (+ необязательное поле `profile` — полная карточка профиля, в проекте не мапится, не нужно).

Проверено вживую 2026-08-23 (эмулятор `Pixel_6_Pro_API_33`, реальный аккаунт с 2 бейджами):
`GET profile/preference/badge/all/0?token=...` → `HTTP 200`, `code: 0`, форма 1:1 с decompiled
`database/entity/profile/Badge.java` — `id`, `type` (`0` статика/`1` Lottie-анимация), `name`,
`image_url`, `timestamp` (unix-секунды получения). Сэмпл (поле `profile` вырезано, содержит личные
данные аккаунта): `docs/api/samples/profile_preference_badge_all_page0.json`.

Смысл эндпоинта — коллекция УЖЕ ПОЛУЧЕННЫХ пользователем значков (путь `profile/preference/…` +
соседние `edit`/`remove` в decompile выбирают/снимают ОДИН активный бейдж на аватар), а не общий
каталог всех ачивок с состоянием «получено/не получено» — данных о неполученных значках API не
отдаёт. В проекте: `BadgeDto`/`ProfilePreferenceApi.badges()` (`shared/data`), доменная модель
`Achievement` (`shared/model`), `ProfileRepository.achievements()`, секция `AchievementsSection`
на экране профиля (`composeApp`).

**`ProfileBlockListApi`** — чёрный список пользователей.
**`ProfileDeletionApi`** — удаление аккаунта.
**`ProfileFriendApi`** — друзья (запросы/подтверждение/удаление).
**`ProfileHealthApi`** — «здоровье» аккаунта: баны, апелляции.
**`ProfileRoleListApi`** — роли/права.

### Модель `Profile` — ключевые поля

`is_online`, `is_sponsor`, `is_verified`, `friend_status`, `watched_episode_count`,
`preferred_genres` / `preferred_audiences` / `preferred_themes`, набор `theme_*` полей кастомизации,
соцсети: `vk_page`, `tg_page`, `inst_page`, `discord_page`, `tt_page`.

18+ toggle настроек приватности (`profile/preference/my`) в первичном `strings`-анализе APK связан с
заголовком/метаданными `X-Amz-Meta-Is-Explicit` — точная механика (заголовок vs поле тела) не перепроверена
живым трафиком, см. открытые вопросы.

## 8a. Расписание — `ScheduleApi`

**`ScheduleApi`** — `GET schedule`, без токена и без параметров.

Проверено вживую 2026-08-10: `curl -s 'https://api-s.anixsekai.com/schedule'` без токена вернул
`HTTP 200`, тело `{"code": 0, "monday": [...], "tuesday": [...], ..., "sunday": [...]}`. Каждый
день недели — массив ПОЛНЫХ объектов `Release` (те же ~76 полей, что и в `discover/watching`,
`release/{id}`), поэтому в клиенте переиспользован уже существующий `ReleaseDto` — отдельный DTO
релиза для расписания не заводился. Статически подтверждено декомпилом:
`ScheduleResponse.java extends Response` (значит есть `code`), 7 полей `monday..sunday:
List<Release>` с дефолтом `emptyList()`, без `@JsonProperty` (имена полей — lowercase день
недели, совпадают с JSON as-is); `ScheduleApi.java` — интерфейс с единственным методом
`@GET("schedule") schedule()`, без параметров вообще (ни `token`, ни пагинации). Точного времени
выхода серии в ответе нет — только группировка по дню недели (см. `docs/REELWAVE_PLAN.md`,
раздел Schedule).

В проекте: `ScheduleApi.kt` (`shared/data/.../api`), `ScheduleResponseDto` (`.../dto/ScheduleResponseDto.kt`),
маппер `ScheduleResponseDto.toDomain(): Schedule` (`.../mapper/ScheduleMapper.kt`) в доменную
модель `Schedule`/`WeekDay` (`shared/model/.../Schedule.kt`).

## 9. Контент-сообщество (не реализовано в проекте)

- **`ArticleApi` / `ArticleCommentApi` / `ArticleSuggestionApi`** — статьи блога: CRUD, голосование, репосты, комментарии, модерация предложенных статей.
- **`ChannelApi`** — каналы/блоги: подписки, permissions, блок-лист, аватар/обложка (multipart), рекомендации.
- **`CollectionApi` / `CollectionCommentApi` / `CollectionFavoriteApi` / `CollectionMyApi`** — подборки релизов: чтение, избранное, CRUD своих (multipart для картинки).
- **`FeedApi`** — лента статей по каналам/датам.
- **`NotificationApi` / `NotificationPreferenceApi`** — уведомления (друзья, эпизоды, комментарии, статьи, related-релизы) + тонкая настройка по типам.
- **`ReportApi`** — жалобы на 8 типов сущностей (release/collection/episode/profile/channel/article/comments), у каждого свой `GET .../reasons`.
- **`ConfigApi`** — `config/anixplayer`, `config/toggles`, `config/urls` (feature-флаги и resiliency-цепочка базового URL).

## 10. Сводная таблица: что уже реализовано в Kotlin-клиенте Aniko

| API-класс (decompiled) | Статус в `shared/data/.../api/*.kt` |
|---|---|
| `AuthApi` | частично — только `signIn` |
| `DiscoverApi` | частично, встроен в `ReleaseApi.kt` (watching/recommendations/interesting) |
| `EpisodeApi` | полностью — вся цепочка резолвинга + watch/unwatch |
| `FavoriteApi` | полностью |
| `FilterApi` | полностью — `POST filter/{page}`, переиспользует `ReleaseDto` |
| `HistoryApi` | полностью |
| `ProfileApi` | частично — только `profile/{id}` |
| `ProfileListApi` | полностью |
| `ProfilePreferenceApi` | частично — privacy-эндпоинты + `badge/all/{page}` |
| `ReleaseApi` | полностью (+ discover) |
| `ReleaseCommentApi` | полностью |
| `ScheduleApi` | полностью — `GET schedule`, переиспользует `ReleaseDto` |
| `SearchApi` | частично — только `releaseSearch` |
| остальные ~28 классов (Article*, Channel*, Collection*, Notification*, Report, Type, Related, Export/Import, Profile{BlockList,Deletion,Friend,Health,RoleList}, ReleaseVideo*, ReleaseStreamingPlatform, Config) | не реализованы (`ProfileBadgeApi` — реализован как часть `ProfilePreferenceApi`, см. раздел 8) |

## 11. Открытые вопросы / что стоит перепроверить

1. ~~Реальный хост статики/CDN (`s.anixmirai.com` / `s3.anixmirai.com` vs `static.anixart.tv` в `ApiConfig`).~~
   **Исправлено (P3.T10, 2026-08-10)**: `ApiConfig.DEFAULT_STATIC_BASE_URL` теперь `https://s.anixmirai.com/`.
2. Точная схема `search/releases/{page}` могла измениться на сервере относительно decompiled APK 9.0-beta-19 — стоит держать в уме при добавлении новых полей поиска.
3. Полные JSON-схемы `Article*`, `Channel*`, `Collection*`, `Notification*` не сверялись с живыми сэмплами (только с decompile) — при реализации этих фич сначала снять живой сэмпл.
4. Механика 18+ toggle (`X-Amz-Meta-Is-Explicit`) в `profile/preference/my` не подтверждена живым трафиком.
5. `[TODO: verify live]` в `ReleaseCommentApi`: смысл параметра в `GET release/comment/{id}` (releaseId или commentId?) и точный тип элемента `release/comment/votes/{commentId}/{page}` (`Profile` в decompile vs реализованный `ProfileCompact`) — низкий приоритет, не блокирует использование остальных методов.
6. **Найден живым тестом 2026-08-23 (не исправлено, вне объёма задачи о `badge`-эндпоинте):** `GET profile/{id}` падает с `JsonConvertException` на аккаунте с непустой историей — `history[].last_view_episode` в живом ответе приходит ПОЛНЫМ объектом эпизода (`{"@id":...,"releaseId":...,"position":...,"release":{...}}`), а не числом. В `ReleaseDto.kt` (`shared/data/.../dto/ReleaseDto.kt:94`) поле `lastViewEpisode: Int? = null` рассчитано на числовой номер эпизода — верно для `last_view_episode` ВНУТРИ вложенных объектов `release` (там реально число), но не для верхнеуровневого поля `history[].last_view_episode`, у которого то же имя, но другая форма (полиморфизм по контексту, decompiled `HistoryPreview.java` этого не проговаривает явно). Ломает весь экран профиля («Не удалось загрузить профиль») для любого аккаунта, где `history` непуст, а не только страницу «Достижения» — стоит завести отдельную задачу на исправление (нужен отдельный DTO-тип для эпизода в `history[]`, отличный от числового `lastViewEpisode` в `ReleaseDto`).

### P3.T14 — сверка со сторонними reverse-engineered клиентами (2026-08-10)

Сверены пути и формы `ScheduleApi`/`ReleaseCommentApi`/`FilterApi` с тремя открытыми проектами (как дополнение к декомпилу, не как основной источник — см. правило R3):

- **[`Nekonyx/anixart-api`](https://github.com/Nekonyx/anixart-api)** (TypeScript). Проверено дерево репозитория (`src/api/auth.ts`, `release.ts`, `contracts/{auth,profile,release}.ts`) — покрывает только `auth`/`release`/`profile`. **Schedule, Comment и Filter в этом клиенте не реализованы вообще** — сверять не с чем, расхождений нет по определению.
- **[`vraestoren/anixart.py`](https://github.com/vraestoren/anixart.py)** (Python, `src/anixart.py`). Совпадает 1:1 по путям с нашей реализацией: `GET /schedule` (`get_schedule`, хотя в этом клиенте токен всё равно подставляется — не противоречит нашей живой проверке "токен не обязателен", просто клиент не обязан его опускать), `GET /release/comment/all/{release_id}/{page}?sort=` (`get_release_comments`), `POST /release/comment/add/{release_id}` с полем `parentCommentId` в теле (`send_comment`), `POST /release/comment/edit/{release_comment_id}` (`edit_comment`), `GET /release/comment/votes/{comment_id}/{page}?sort=` (`get_release_comment_votes`), `GET /release/comment/all/profile/{user_id}/{page}?sort=` (`get_user_comments`). **Filter не реализован** в этом клиенте — сверить не с чем.
- **[`AnAgTeam/AniAnglia`](https://github.com/AnAgTeam/AniAnglia)** (Objective-C++, iOS). Низкоуровневый HTTP-слой — часть невключённой в этот чекаут библиотеки, прямых путей не нашлось, но **структура `FilterRequest` независимо совпадает** с нашей `FilterRequestDto` (`AniAnglia/Main/Search/FilterViewController.mm`): поля `status`, `category`, `country`, `studio`, `season`, `episodes_count_from/to`, `is_genres_exclude_mode`, и enum `Sort{DateUpdate, Grade, Year, Popular}` в том же порядке, что и наши `SORT_DATE_UPDATE_DESC=0, SORT_GRADE_DESC=1, SORT_YEAR_DESC=2, SORT_POPULAR_DESC=3` — независимое (другой язык, другая кодовая база) подтверждение формы `FilterRequest` из декомпила. Также есть отдельный `CommentsPageableDataProvider`/`CommentRepliesViewController`, подтверждающий модель пагинированных комментариев с ответами (реплаями).

**Итог**: расхождений с уже реализованным не найдено; два из трёх клиентов не реализуют Filter, один не реализует ни один из трёх новых эндпоинтов — это ограничивает полноту сверки, но там где сверка была возможна (Schedule, Comment у `anixart.py`; форма `FilterRequest` у `AniAnglia`), она подтвердила декомпил и живые проверки без противоречий.
