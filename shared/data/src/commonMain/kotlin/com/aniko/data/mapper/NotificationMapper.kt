package com.aniko.data.mapper

import com.aniko.data.dto.NotificationPreferenceResponseDto
import com.aniko.data.dto.ProfileNotificationDto
import com.aniko.data.dto.ReleaseCompactDto
import com.aniko.model.AppNotification
import com.aniko.model.AppNotificationKind
import com.aniko.model.NotificationPreferences

/**
 * `type`-дискриминатор из ответа → [AppNotificationKind].
 *
 * Девять серверных подтипов схлопываются в пять доменных: три комментарийных
 * (`releaseComment`/`collectionComment`/`myCollection`) и два «статейных»
 * (`article`/`articleComment`/`myArticle`) неразличимы для локального уведомления — текст у них
 * один и тот же, а различающая их сущность (коллекция, статья) в приложении не реализована
 * вообще. Хранить различие «на будущее» значило бы завести пять неиспользуемых веток в
 * `NotificationContentFactory`.
 *
 * Незнакомая строка (в том числе `null`, если сервер вдруг перестанет присылать `type`) даёт
 * [AppNotificationKind.UNKNOWN], а не исключение — см. KDoc enum'а про `defaultImpl` у самого
 * Anixart.
 */
private fun kindOf(type: String?): AppNotificationKind =
    when (type) {
        "episode" -> AppNotificationKind.EPISODE
        "relatedRelease" -> AppNotificationKind.RELATED_RELEASE
        "friend" -> AppNotificationKind.FRIEND
        "releaseComment", "collectionComment", "myCollection" -> AppNotificationKind.COMMENT
        "article", "articleComment", "myArticle" -> AppNotificationKind.ARTICLE
        else -> AppNotificationKind.UNKNOWN
    }

/**
 * Релиз, к которому относится уведомление, — он же цель deep link `aniko://release/{id}`.
 *
 * Ищется в трёх местах, потому что у разных подтипов он лежит по-разному:
 * `episode.release` (новая серия), `release` (связанный релиз), `comment.release` (ответ на
 * комментарий к релизу). У `friend`/`article` релиза нет вообще — тогда `null`, и уведомление
 * просто откроет приложение без перехода.
 */
private fun ProfileNotificationDto.releaseOrNull(): ReleaseCompactDto? = episode?.release ?: release ?: comment?.release

/**
 * `id` релиза приходит `Long` (декомпил), а вся навигация приложения оперирует `Int`
 * (`AnixDestination.ReleaseDetails`, `ReleaseApi.release`). Явный `toIntOrNull`-эквивалент через
 * диапазон, а не молчаливый `toInt()`: переполнение дало бы ссылку на чужой тайтл, лучше уж
 * уведомление без ссылки.
 */
private val INT_RANGE = Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()

private fun ReleaseCompactDto.releaseIdOrNull(): Int? = id.takeIf { it in INT_RANGE }?.toInt()

fun ProfileNotificationDto.toDomain(): AppNotification {
    val release = releaseOrNull()
    return AppNotification(
        id = id,
        kind = kindOf(type),
        timestamp = timestamp,
        isNew = isNew,
        releaseId = release?.releaseIdOrNull(),
        releaseTitle = release?.titleRu?.takeIf { it.isNotBlank() },
        episodeName = episode?.name?.takeIf { it.isNotBlank() },
        sourceName = episode?.source?.name?.takeIf { it.isNotBlank() },
        profileLogin = byProfile?.login?.takeIf { it.isNotBlank() },
    )
}

fun NotificationPreferenceResponseDto.toDomain(): NotificationPreferences =
    NotificationPreferences(
        episodes = episodes,
        firstEpisode = firstEpisode,
        relatedReleases = relatedReleases,
        articles = articles,
        comments = comments,
        myCollectionComments = myCollectionComments,
        myArticleComments = myArticleComments,
        reportProcess = reportProcess,
    )
