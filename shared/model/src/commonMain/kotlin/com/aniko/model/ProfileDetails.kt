package com.aniko.model

/**
 * Фаза 7 — «Профиль»: детальная карточка своего профиля.
 *
 * MVP-подмножество полей decompiled `database/entity/profile/Profile.java` (у него ~80 полей) —
 * сюда взято только то, что нужно для просмотра своего профиля (аватар/ник/статистика/статус
 * бана-спонсорства). Осознанно не включены темы (`theme*`), соцсети (`vkPage`/`tgPage`/...),
 * превью-списки (`friendsPreview`/`commentsPreview`/...) и `friendStatus` — вне объёма Фазы 7 MVP.
 *
 * Фаза 9 (P9.T8/T9/T11) добавила три поля, приходящие тем же ответом `profile/{id}` и до этого
 * не мапившиеся: [watchDynamics], [preferredGenres] и [recentlyWatched]. Живая проверка
 * 2026-08-18 (`GET https://api-s.anixsekai.com/profile/1000001`, без токена) подтвердила все три —
 * см. KDoc соответствующих DTO в `:shared:data`. Отдельного запроса под них не нужно.
 */
data class ProfileDetails(
    val id: Long,
    val login: String,
    val avatarUrl: String?,
    val status: String?,
    val isSponsor: Boolean,
    val sponsorshipExpires: Long?,
    val isBanned: Boolean,
    val isPermBanned: Boolean,
    val banReason: String?,
    val banExpires: Long?,
    val privilegeLevel: Int,
    val ratingScore: Int,
    val badgeName: String?,
    val badgeUrl: String?,
    val watchingCount: Int,
    val planCount: Int,
    val completedCount: Int,
    val holdOnCount: Int,
    val droppedCount: Int,
    val favoriteCount: Int,
    val commentCount: Int,
    val collectionCount: Int,
    val videoCount: Int,
    val friendCount: Int,
    val watchedEpisodeCount: Int,
    val watchedTime: Long,
    val registerDate: Long,
    val lastActivityTime: Long,
    val isOnline: Boolean,
    val isVerified: Boolean,
    /** Дневная динамика просмотра, отсортированная по [WatchDynamicsPoint.timestamp] по возрастанию. */
    val watchDynamics: List<WatchDynamicsPoint> = emptyList(),
    /** Топ жанров пользователя с процентами — считает сам сервер, локального расчёта нет. */
    val preferredGenres: List<PreferredGenre> = emptyList(),
    /** «Недавно смотрели» — последние просмотренные релизы из того же ответа `profile/{id}`. */
    val recentlyWatched: List<Release> = emptyList(),
    /**
     * P16.T13 «профиль-витрина, обложка»: URL фоновой картинки витрины (`theme_background_url` —
     * см. KDoc `ProfileDetailsDto.themeBackgroundUrl`). `null` — самый частый случай (пользователь
     * не настроил тему витрины); [ProfileHeader] в этом случае рисует обычную шапку без фона.
     */
    val coverUrl: String? = null,
) {
    /** Часы просмотра — [watchedTime] приходит в минутах (см. вердикт P0.T4 плана). */
    val watchedHours: Int get() = (watchedTime / MINUTES_PER_HOUR).toInt()

    /**
     * Последние [days] точек динамики просмотра — ровно то, что рисует недельный график макета
     * (7 столбиков). Список уже отсортирован по времени, поэтому это простой `takeLast`.
     */
    fun recentWatchDynamics(days: Int = DEFAULT_ACTIVITY_DAYS): List<WatchDynamicsPoint> = watchDynamics.takeLast(days)

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val DEFAULT_ACTIVITY_DAYS = 7
    }
}

/**
 * Одна точка дневной активности (`watch_dynamics`).
 *
 * [day] — число месяца (1..31), [count] — сколько серий просмотрено за этот день, [timestamp] —
 * unix-секунды момента, когда сервер записал эту точку. Живьём массив приходит как кольцевой
 * буфер на 31 слот (по слоту на число месяца) в произвольном порядке — единственный надёжный
 * порядок «по времени» даёт [timestamp], поэтому сортирует по нему маппер, а не потребитель.
 */
data class WatchDynamicsPoint(
    val day: Int,
    val count: Int,
    val timestamp: Long,
)

/** Любимый жанр пользователя: имя + доля в процентах (`preferred_genres`). */
data class PreferredGenre(
    val name: String,
    val percentage: Int,
)
