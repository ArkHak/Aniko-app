package com.aniko.model

/**
 * Тип озвучки/перевода — первый шаг цепочки резолвинга плеера
 * (`GET episode/{releaseId}` → `TypesResponse`).
 */
data class VoiceType(
    val id: Int,
    val name: String,
    /** URL аватара/логотипа команды озвучки. Живая верификация (P16.T5): `icon` в ответе. */
    val icon: String? = null,
    /** Живая верификация (R3): реальный API отдаёт строку (или пусто), не массив имён. */
    val workers: String? = null,
    val episodesCount: Int? = null,
    /**
     * `true` — это дорожка субтитров, а не дубляж. Живая верификация (P8.T6, `GET episode/1`):
     * поле `is_sub` реально приходит с сервера (не выдумано под мокап), используется для бейджа
     * SUB и фильтра All/Dubs/Subs (P8.T6).
     */
    val isSub: Boolean = false,
    /** Счётчик просмотров этой озвучки. Живая верификация (P8.T6) — поле `view_count` в ответе. */
    val viewCount: Int? = null,
    /** Закреплённая озвучка — поднимается в начало списка. Живая верификация (P8.T6). */
    val pinned: Boolean = false,
    /** Метка качества озвучки (1 = 1080p, 2 = 1440p, 3 = 4K, 0 = скрыта). Живая верификация (P16.T4). */
    val quality: Int = 0,
)

/**
 * Источник видео для конкретного типа озвучки
 * (`GET episode/{releaseId}/{typeId}` → `SourcesResponse`).
 *
 * @param host нормализованный хост, см. [VideoHost].
 */
data class EpisodeSource(
    val id: Int,
    val name: String,
    val host: VideoHost,
    val episodesCount: Int? = null,
    /** Метка качества источника (1 = 1080p, 2 = 1440p, 3 = 4K, 0 = скрыта). Живая верификация (P16.T4). */
    val quality: Int = 0,
)

/**
 * Серия внутри источника
 * (`GET episode/{releaseId}/{typeId}/{sourceId}` → `EpisodeResponse`).
 *
 * @param position порядковый номер, используется в `episode/target/.../{position}`.
 */
data class Episode(
    val position: Int,
    val name: String?,
    val isWatched: Boolean = false,
)

/**
 * Итог резолвинга серии в проигрываемый источник
 * (`GET episode/target/{releaseId}/{sourceId}/{position}` → `EpisodeTargetResponse`).
 *
 * @param iframe Поле, которое отдаёт сервер; его назначение неясно и для ветвления не
 * используется.
 *
 * Раньше здесь было написано, что это «признак embed-страницы против прямого потока» — это
 * **фактически неверно**, живая проверка Фазы 8 (P8.T2) опровергла: `Sibnet`, `Libria`/`Liberty`,
 * `RuTube` и `VK Видео` все приходят с `iframe = false`, и все четыре — HTML-страницы плеера,
 * а не прямой поток. То есть поле не коррелирует с типом контента вообще. Прямого m3u8/mp4
 * из `episode/target` не отдаёт ни один хост (P8.T2), поэтому весь плеер работает через embed
 * (WebView) независимо от значения этого поля, см. `EpisodeRepository.resolvePlaybackSource`.
 * Поле сохранено только потому, что реально присутствует в ответе API.
 */
data class EpisodeTarget(
    val position: Int,
    val name: String?,
    val url: String?,
    val iframe: Boolean,
)

/**
 * Хосты-плееры, встречающиеся в пакете `utils.parser` оригинального APK.
 *
 * Живая проверка Фазы 8 (P8.T2) закрыла старую формулировку «часть отдаёт прямую ссылку»:
 * прямого потока не отдаёт **никто** — `episode/target` всегда возвращает HTML-страницу плеера.
 * Различаются только домены, на которые эта страница ведёт, — они и лежат в [domains].
 *
 * @param key машинный ключ, совпадает со значением `hostKey` в маршруте навигации.
 * @param domains домены, по которым хост опознаётся в реальном `url` из `episode/target`.
 * Пусто у [UNKNOWN] и у хостов, которых не встретили вживую и чей домен неизвестен.
 * @param aliases варианты человекочитаемого `Source.name` из API, по которым хост опознаётся,
 * когда домена ещё нет (список источников приходит раньше, чем ссылка на серию).
 */
enum class VideoHost(
    val key: String,
    val domains: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
) {
    KODIK(
        key = "kodik",
        domains = listOf("kodikplayer.com", "kodik.cc", "kodik.info", "kodik.biz", "kodik-hd.com", "aniqit.com"),
        aliases = listOf("kodik"),
    ),
    SIBNET(
        key = "sibnet",
        domains = listOf("sibnet.ru"),
        aliases = listOf("sibnet"),
    ),
    RUTUBE(
        key = "rutube",
        domains = listOf("rutube.ru"),
        aliases = listOf("rutube"),
    ),

    // «VK Видео» — реальное имя источника из живого ответа API; голый ключ `vkvideo`
    // в него не входит подстрокой, из-за чего старый матчинг ронял хост в UNKNOWN.
    // Кириллические алиасы матчатся на русскоязычное имя источника из ответа API, не на
    // UI-текст — тот же случай, что уже решён в `ReleaseMapper.kt` (см. P2.T10).
    @Suppress("ForbiddenCyrillicStringLiteral")
    VK_VIDEO(
        key = "vkvideo",
        domains = listOf("vk.com", "vkvideo.ru", "userapi.com"),
        aliases = listOf("vkvideo", "vk видео", "vk video", "вк видео", "vk"),
    ),

    @Suppress("ForbiddenCyrillicStringLiteral")
    OK_RU(
        key = "okru",
        domains = listOf("ok.ru", "odnoklassniki.ru"),
        aliases = listOf("okru", "ok.ru", "одноклассники"),
    ),
    MAIL_RU(
        key = "mailru",
        domains = listOf("my.mail.ru", "mail.ru"),
        aliases = listOf("mailru", "mail.ru"),
    ),
    MYVI(
        key = "myvi",
        domains = listOf("myvi.ru", "myvi.tv", "myvi.top"),
        aliases = listOf("myvi"),
    ),
    ALLVIDEO(
        key = "allvideo",
        domains = listOf("allvideo.su"),
        aliases = listOf("allvideo"),
    ),
    ANILIBRIA(
        key = "anilibria",
        domains = listOf("libria.fun", "anilibria.tv", "anilib.me"),
        // Живьём источник называется «Libria» или «Liberty» — слова `anilibria` в имени нет.
        aliases = listOf("anilibria", "libria", "liberty"),
    ),
    SOVET_ROMANTICA(
        key = "sovetromantica",
        domains = listOf("sovetromantica.com"),
        // Живьём встречается как «Sovet» и «Sovet (не работает)».
        aliases = listOf("sovetromantica", "sovet romantica", "sovet"),
    ),
    STUDIO_MIR(
        key = "studiomir",
        domains = listOf("studiomir.club"),
        // Живьём источник называется «TSM» (The Studio Mir).
        aliases = listOf("studiomir", "studio mir", "tsm"),
    ),
    TORLOOK(
        key = "torlook",
        domains = listOf("torlook.info"),
        aliases = listOf("torlook"),
    ),
    UNKNOWN("unknown"),
    ;

    companion object {
        /**
         * Основной способ опознания хоста — по домену реального `url` из `episode/target`.
         *
         * Матчинг по [domains] идёт по границе метки домена (`sibnet.ru` матчит
         * `video.sibnet.ru`, но не `notsibnet.ru`), поэтому подстроковых ложных срабатываний,
         * как в старом [fromKey], здесь нет.
         *
         * Возвращает [UNKNOWN], если домен не опознан, — тогда имеет смысл добить [fromKey].
         */
        fun fromUrl(url: String?): VideoHost {
            val host = hostOf(url) ?: return UNKNOWN
            return entries.firstOrNull { entry -> entry.domains.any { host.matchesDomain(it) } } ?: UNKNOWN
        }

        /**
         * Опознание по человекочитаемому имени источника (`Source.name`) — fallback на случай,
         * когда ссылки на серию ещё нет (список источников приходит раньше).
         *
         * Раньше здесь было `entries.firstOrNull { it.key in normalized }` — то есть проверка
         * «является ли машинный ключ enum'а подстрокой имени из API». Из 12 хостов так
         * резолвились только KODIK/SIBNET/RUTUBE, остальные 9 молча улетали в [UNKNOWN]:
         * `VK Видео` != `vkvideo`, `Libria` != `anilibria`, `TSM` != `studiomir`,
         * `Sovet (не работает)` != `sovetromantica`. Теперь матчим по явному списку [aliases]
         * по границе слова, а не по подстроке.
         */
        fun fromKey(key: String?): VideoHost {
            val normalized = key?.lowercase()?.trim()
            return if (normalized.isNullOrBlank()) {
                UNKNOWN
            } else {
                // Точное совпадение с ключом/алиасом — самое надёжное, пробуем первым.
                val exact = entries.firstOrNull { entry -> entry.key == normalized || normalized in entry.aliases }
                val words = normalized.split(NAME_SEPARATORS_REGEX).filter { it.isNotEmpty() }
                exact ?: entries.firstOrNull { entry ->
                    entry.aliases.any { alias -> if (' ' in alias) alias in normalized else alias in words }
                } ?: UNKNOWN
            }
        }

        /**
         * Домен приоритетнее имени: имя источника задаёт человек на стороне Anixart и меняется
         * («Libria» → «Liberty»), домен же — то, куда реально пойдёт запрос.
         */
        fun resolve(
            url: String?,
            name: String?,
        ): VideoHost = fromUrl(url).takeIf { it != UNKNOWN } ?: fromKey(name)

        private val NAME_SEPARATORS_REGEX = Regex("[ ()\\[\\]/,.\\-_:]+")

        /**
         * Хост из URL: `https://video.sibnet.ru/shell.php?...` → `video.sibnet.ru`.
         *
         * Похожая функция `registrableDomainOf` есть в `:shared:player` (`EmbedVideoBridge.kt`) —
         * это не дублирование по недосмотру, а разные задачи в разных модулях без зависимости друг
         * на друга: там нужен именно **registrable domain** (с учётом двухуровневых суффиксов
         * `co.uk`) как граница доверия для фильтрации сообщений JS-моста от произвольных фреймов;
         * здесь — сырой хост для сравнения с фиксированным списком ~12 известных доменов через
         * [matchesDomain] (сравнение по границе метки, не по PSL), двухуровневые суффиксы среди
         * них не встречаются. Если понадобится третье место с той же логикой — тогда стоит
         * выносить в общий модуль, но не раньше.
         */
        private fun hostOf(url: String?): String? {
            if (url.isNullOrBlank()) return null
            val withoutScheme = url.substringAfter("//", url)
            val authority = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
            val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
            return host.ifEmpty { null }
        }

        /** `video.sibnet.ru`.matchesDomain(`sibnet.ru`) == true, `notsibnet.ru` — false. */
        private fun String.matchesDomain(domain: String): Boolean = this == domain || endsWith(".$domain")
    }
}
