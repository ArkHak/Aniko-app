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
| Resiliency base-URL | `ConstantNetFetcher` → `ConfigNetFetcher` (`config/urls`) → `FirebaseNetFetcher` → `GithubPagesNetFetcher` (fallback `anixhelper.github.io`) — в проекте не реализовано, `baseUrl` вынесен параметром на будущее | `ChainedNetFetcher.java` |
| Статика/CDN | **Расхождение**: код проекта/старый черновик указывают `static.anixart.tv`, но живые ответы отдают реальные хосты постеров/иконок — `https://s.anixmirai.com/posters/...`, `https://s3.anixmirai.com/voiceovers/...`. Требует перепроверки/фикса в `ApiConfig` | сэмплы в `docs/api/samples/` |
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

**`FilterApi`** — `POST filter/{page}`, body `FilterRequest` — расширенный фильтр каталога (жанры/год/статус/тип и т.п.).

**`TypeApi`** — управление типами озвучки: pin/unpin, hide-widget.

**В проекте реализован только `SearchApi.releaseSearch`.**

## 5. Релиз

**`ReleaseApi`**
- `GET release/{r_id}?extended_mode=&token=` → `ReleaseResponse`
- `GET release/random?extended_mode=&token=` → `ReleaseResponse`
- `GET release/vote/add/{r_id}/{vote}?token=` → `VoteReleaseResponse`
- `GET release/vote/delete/{r_id}?token=` → `DeleteVoteReleaseResponse`

**`RelatedApi`** — `related/{relatedId}/{page}` (заголовок `API-Version`) — сиквелы/приквелы/спин-оффы.

**`ReleaseCommentApi`** — комментарии к релизу: добавление/редактирование/удаление/голосование/ответы (реплаи).

**`ReleaseStreamingPlatformApi`** — легальные стриминг-площадки, где доступен релиз.

### Модель `Release` (крупнейшая, ~906 строк decompiled)

Ключевые поля (Jackson `@JsonProperty`, подтверждены сэмплом `release_186_extended.json`):
`age_rating`, `title_ru` / `title_original` / `title_alt`, `episodes_released` / `episodes_total`,
`profile_list_status`, `is_favorite`, `is_viewed`, `your_vote`, `vote_1_count`…`vote_5_count`,
`related_releases`, `recommended_releases`, `episode_last_update`, `genres`, `status`, `poster`, `year` и др.

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
- `url: String` — прямая ссылка на видео/embed
- `iframe: Boolean` — надо ли грузить `url` как iframe

Примеры: `kodik` → `iframe: true`, ссылка на `kodikplayer.com`. `sibnet` → `iframe: false`, ссылка на `video.sibnet.ru/shell.php`, которую всё равно нужно доп. парсить (это не прямой mp4).

Источники, встречающиеся в парсерах (`utils/parser/*`): `kodik`, `sibnet`, `rutube`, `vkvideo`, `okru`, `mailru`, `myvi`, `allvideo`, `anilibria`, `sovetromantica`, `studiomir`, `torlook`.

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

**`ProfilePreferenceApi`** — смена email/пароля/логина, привязка VK/Google/Telegram/Yandex, темы оформления, настройки приватности. В проекте реализованы только privacy-эндпоинты.

**`ProfileBadgeApi`** — значки профиля.
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

## 9. Контент-сообщество (не реализовано в проекте)

- **`ArticleApi` / `ArticleCommentApi` / `ArticleSuggestionApi`** — статьи блога: CRUD, голосование, репосты, комментарии, модерация предложенных статей.
- **`ChannelApi`** — каналы/блоги: подписки, permissions, блок-лист, аватар/обложка (multipart), рекомендации.
- **`CollectionApi` / `CollectionCommentApi` / `CollectionFavoriteApi` / `CollectionMyApi`** — подборки релизов: чтение, избранное, CRUD своих (multipart для картинки).
- **`FeedApi`** — лента статей по каналам/датам.
- **`NotificationApi` / `NotificationPreferenceApi`** — уведомления (друзья, эпизоды, комментарии, статьи, related-релизы) + тонкая настройка по типам.
- **`ReportApi`** — жалобы на 8 типов сущностей (release/collection/episode/profile/channel/article/comments), у каждого свой `GET .../reasons`.
- **`ScheduleApi`** — `GET schedule` (без параметров) — расписание выхода эпизодов.
- **`ConfigApi`** — `config/anixplayer`, `config/toggles`, `config/urls` (feature-флаги и resiliency-цепочка базового URL).

## 10. Сводная таблица: что уже реализовано в Kotlin-клиенте Aniko

| API-класс (decompiled) | Статус в `shared/data/.../api/*.kt` |
|---|---|
| `AuthApi` | частично — только `signIn` |
| `DiscoverApi` | частично, встроен в `ReleaseApi.kt` (watching/recommendations/interesting) |
| `EpisodeApi` | полностью — вся цепочка резолвинга + watch/unwatch |
| `FavoriteApi` | полностью |
| `HistoryApi` | полностью |
| `ProfileApi` | частично — только `profile/{id}` |
| `ProfileListApi` | полностью |
| `ProfilePreferenceApi` | частично — только privacy-эндпоинты |
| `ReleaseApi` | полностью (+ discover) |
| `SearchApi` | частично — только `releaseSearch` |
| остальные ~32 класса (Article*, Channel*, Collection*, Notification*, Report, Schedule, Type, Filter, Related, Export/Import, Profile{Badge,BlockList,Deletion,Friend,Health,RoleList}, ReleaseVideo*, ReleaseStreamingPlatform, ReleaseComment, Config) | не реализованы |

## 11. Открытые вопросы / что стоит перепроверить

1. Реальный хост статики/CDN (`s.anixmirai.com` / `s3.anixmirai.com` vs `static.anixart.tv` в `ApiConfig`).
2. Точная схема `search/releases/{page}` могла измениться на сервере относительно decompiled APK 9.0-beta-19 — стоит держать в уме при добавлении новых полей поиска.
3. Полные JSON-схемы `Article*`, `Channel*`, `Collection*`, `Notification*` не сверялись с живыми сэмплами (только с decompile) — при реализации этих фич сначала снять живой сэмпл.
4. Механика 18+ toggle (`X-Amz-Meta-Is-Explicit`) в `profile/preference/my` не подтверждена живым трафиком.
5. Сверить семантику полей (не путей — пути уже подтверждены из APK) со сторонними reverse-engineered клиентами (`Nekonyx/anixart-api`, `anixart.py`, `AniAnglia`) на предмет расхождений.
