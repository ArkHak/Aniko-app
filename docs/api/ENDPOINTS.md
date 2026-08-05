# Anixart API — извлечено из APK 9.0-beta-19

Источник: `jadx`-декомпиляция `anixart_9-0-beta-19.apk` (пакет `com.swiftsoft.anixartd`), файлы `network/api/*Api.java`. Все сигнатуры ниже — прямая цитата из декомпилированного кода, помечены `[APK]`. Значения, не найденные статически (нужна живая проверка, этап R3 из плана), помечены `[TODO: verify live]`.

## Базовый конфиг

- **Base URL**: `https://api-s.anixsekai.com/` — константа в `utils/anixnet/ConstantNetFetcher.java` и `presentation/start/StartPresenter.java` (`API_BASE_URL`).
- Есть resiliency-цепочка (`ChainedNetFetcher`) поверх `ConstantNetFetcher` → `ConfigNetFetcher` (эндпоинт `config/urls`) → `FirebaseNetFetcher` → `GithubPagesNetFetcher` (fallback на `anixhelper.github.io`). Для MVP достаточно константы; цепочку резолвинга добавить, если базовый хост окажется недоступен без VPN.
- **Аутентификация запроса**: токен передаётся как **query-параметр `?token=`**, НЕ как заголовок (подтверждено во всех *Api.java: `@Query("token") String token`).
- На `search/releases/{page}` замечен доп. заголовок `@Header("API-Version") String apiVersion` — точное текущее значение не найдено статически, `[TODO: verify live]` через перехват реального трафика или `strings`-поиск по BuildConfig.
- Статика/постеры: `static.anixart.tv` (из предыдущего анализа `strings`).

## Auth (`AuthApi`) [APK]

| Метод | Путь | Параметры | Ответ |
|---|---|---|---|
| POST | `auth/checkLogin` | form: `login` | `CheckLoginResponse` |
| POST | `auth/signIn` | form: `login`, `password` | `SignInResponse` (содержит `profile: Profile`, `profileToken: ProfileToken{id, token}`) |
| POST | `auth/signUp` | form: `login`, `email`, `password` | `SignUpResponse` |
| POST | `auth/google` | form: `googleIdToken` (signIn) / `+login,email` (signUp) | `GoogleResponse` |
| POST | `auth/vk` | form: `vkAccessToken` (+`login,email` на signUp) | `VkResponse` |
| POST | `auth/telegram` | form: `telegramIdToken` (+`login,email` на signUp) | `TelegramResponse` |
| POST | `auth/verify` | form: `login,email,password,vkAccessToken,googleIdToken,telegramIdToken,hash,code` | `VerifyResponse` |
| POST | `auth/resend` | form: `login,email,password,vkAccessToken,googleIdToken,telegramIdToken,hash` | `ResendResponse` |
| POST | `auth/restore` | form: `data` | `RestoreResponse` |
| POST | `auth/restore/resend` | form: `data,password,hash` | `RestoreResendResponse` |
| POST | `auth/restore/verify` | form: `data,password,hash,code` | `RestoreVerifyResponse` |
| POST | `auth/firebase` | query: `token` | `FirebaseResponse` |

**MVP-путь**: `auth/signIn(login, password)` → `SignInResponse.profileToken.token` — это и есть значение для всех последующих `?token=`.

## Каталог / поиск / рекомендации

**`DiscoverApi`** [APK]
- `POST discover/interesting` → `PageableResponse<Interesting>`
- `POST discover/recommendations/{page}?previous_page=&token=` → `PageableResponse<Release>`
- `POST discover/watching/{page}?token=` → `PageableResponse<Release>`
- `POST discover/discussing?token=` → `PageableResponse<Release>`
- `POST discover/comments` → `PageableResponse<ReleaseComment>`

**`SearchApi`** [APK] — все POST с `Body: SearchRequest`, `Query: token`:
- `search/releases/{page}` → `ReleaseSearchResponse` (доп. `@Header("API-Version")`)
- `search/feed/{page}` → `FeedSearchResponse`
- `search/profile/list/{status}/{page}` → `PageableResponse<Release>` — поиск внутри своего списка по статусу
- `search/favorites/{page}`, `search/history/{page}`, `search/collections/{page}`, `search/profiles/{page}`, `search/channels/{page}`, `search/articles/{page}` — аналогично

**`FilterApi`, `TypeApi`** — не прочитаны в деталях, для MVP не критичны (доп. фильтры каталога), см. `docs/api/jadx-out/sources/.../network/api/FilterApi.java` при необходимости.

## Релиз

**`ReleaseApi`** [APK]
- `GET release/{r_id}?extended_mode=&token=` → `ReleaseResponse`
- `GET release/random?extended_mode=&token=` → `ReleaseResponse`
- `GET release/vote/add/{r_id}/{vote}?token=` → `VoteReleaseResponse`
- `GET release/vote/delete/{r_id}?token=` → `DeleteVoteReleaseResponse`

**`RelatedApi`** — `related/{relatedId}/{page}` (из первичного анализа, не перепрочитано детально).

## Серии и источники видео — САМОЕ ВАЖНОЕ ДЛЯ ПЛЕЕРА

**`EpisodeApi`** [APK]
| Метод | Путь | Смысл |
|---|---|---|
| GET | `episode/{releaseId}?token=` | `TypesResponse` — типы озвучки/перевода |
| GET | `episode/{releaseId}/{typeId}` | `SourcesResponse` — список источников (кодик/sibnet/vk/...) для этого типа |
| GET | `episode/{releaseId}/{typeId}/{sourceId}?sort=&token=` | `EpisodeResponse` — список серий у конкретного источника |
| GET | `episode/target/{releaseId}/{sourceId}/{position}` | `EpisodeTargetResponse` — **резолв конкретной серии в проигрываемый источник** |
| POST | `episode/watch/{releaseId}/{sourceId}[/{position}]?token=` | отметить как просмотренное |
| POST | `episode/unwatch/{releaseId}/{sourceId}[/{position}]?token=` | снять отметку |
| GET | `episode/updates/{releaseId}/{page}` | `PageableResponse<EpisodeUpdate>` |

**Цепочка резолвинга плеера**: `types` → выбрать typeId → `sources` (typeId) → выбрать sourceId → `episodes` (releaseId, typeId, sourceId) → список серий → `episodeTarget` (releaseId, sourceId, position) → получить финальный playable URL/embed.

Источники в коде (`utils/parser/*`): `kodik`, `sibnet`, `rutube`, `vkvideo`, `okru`, `mailru`, `myvi`, `allvideo`, `anilibria`, `sovetromantica`, `studiomir`, `torlook` — подтверждает гипотезу «часть источников = embed-парсинг стороннего плеера, не прямая ссылка». Точный формат ответа `EpisodeTargetResponse` (прямой URL vs iframe) — `[TODO: verify live]`, читать `network/response/release/episode/EpisodeTargetResponse.java` в `docs/api/jadx-out`.

**`ReleaseVideoApi`** [APK] — это отдельная фича «видео о релизе» (трейлеры/AMV?), не путать с эпизодами:
- `GET /video/release/{releaseId}` → `ReleaseVideosResponse`
- `GET /video/release/{releaseId}/{page}` → `PageableResponse<ReleaseVideo>`
- `GET /video/release/categories` → `ReleaseVideoCategoriesResponse`

## Списки и синхронизация (ключевое для требования «данные совпадают с официальным приложением»)

**Статусы списков** — расшифрованы из `ui/model/common/TopReleaseModel.java` (сопоставление с `R.string.status_*`):

| int | Значение |
|---|---|
| 1 | смотрю (`status_watching`) |
| 2 | в планах (`status_plan`) |
| 3 | просмотрено (`status_completed`) |
| 4 | отложено (`status_hold_on`) |
| 5 | брошено (`status_dropped`) |

**`ProfileListApi`** [APK]
- `GET profile/list/add/{status}/{r_id}?token=` → `ProfileListResponse`
- `GET profile/list/delete/{status}/{r_id}?token=` → `ProfileListResponse`
- `GET profile/list/all/{status}/{page}?sort=&filter_announce=&token=` → `PageableResponse<Release>` — свой список
- `GET profile/list/all/{p_id}/{status}/{page}?sort=&filter_announce=&token=` → список другого профиля

**`FavoriteApi`** [APK]
- `GET favorite/add/{r_id}?token=` / `favorite/delete/{r_id}?token=` → `FavoritesResponse`
- `GET favorite/all/{page}?sort=&filter_announce=&token=` → `PageableResponse<Release>`

**`HistoryApi`** — по аналогии с Favorite (не перечитано детально), пути вида `history/{page}`, `history/delete/{r_id}`.

## Профиль

**`ProfileApi`, `ProfilePreferenceApi`** — не прочитаны детально в этой сессии. Для MVP нужен минимум: `profile/{id}` (получить данные профиля, включая избранное/статистику), `profile/preference/my` (настройки, включая 18+ toggle — см. `X-Amz-Meta-Is-Explicit` из первичного `strings`-анализа).

## Что осталось сделать (R2/R3 из плана)

1. Прочитать `network/request/**` и `network/response/**` полностью — точные имена JSON-полей (обязательно для `kotlinx.serialization`, поля должны совпадать 1:1 или размечаться `@SerialName`).
2. Прочитать `EpisodeTargetResponse.java`, `SourcesResponse.java`, `EpisodeResponse.java` — понять формат «прямая ссылка vs embed» на практике.
3. Сверить с `Nekonyx/anixart-api`, `anixart.py`, `AniAnglia` — на предмет расхождений в семантике полей (не путей — пути уже подтверждены из APK).
4. **Живая верификация (R3)**: после получения токена прогнать `auth/signIn`, `discover/watching/{page}`, `release/{id}`, `episode/{id}`, `profile/list/all/{status}/{page}` реальным curl'ом, сохранить сэмплы в `docs/api/samples/`.
5. Найти актуальное значение заголовка `API-Version` для `search/releases`.
