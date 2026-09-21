package com.aniko.player

/*
 * Чистая логика «предпочтительного качества видео по умолчанию» (настройка «Воспроизведение»).
 *
 * Живёт в `:shared:player` (а не в `composeApp`), потому что нужна сразу двум сторонам: Desktop-
 * контроллер (VLCJ) выбирает поток ДО первого кадра, а Android/iOS-мосты применяют её к меню хоста
 * ([PreferredQualityApplier]); и только здесь есть `commonTest`.
 */

// MagicNumber: это и есть доменные константы (высоты кадра стриминговых качеств) — выносить
// `1080`/`720`/… в именованные константы ради самого выноса только размножило бы шум.
/**
 * Высоты кадра (px), которые пользователь может выбрать «качеством по умолчанию» в настройках
 * (помимо «Авто» — там ничего не переопределяется). Это ровно тот набор, который знают резолверы
 * источников: `KodikDirectLinkResolver`/`AniLibriaDirectLinkResolver` ранжируют кандидатов по
 * `1080 → 720 → 480 → 360`, а хостовое меню Kodik (`.fp-quality`) отдаёт `360p/480p/720p`
 * (иногда `1080p`). Порядок — от лучшего к худшему, это же порядок чипов в настройках.
 */
@Suppress("MagicNumber")
val PREFERRED_QUALITY_HEIGHTS: List<Int> = listOf(1080, 720, 480, 360)

private val QUALITY_LABEL_REGEX = Regex("""^\s*(\d{3,4})\s*[pP]?\s*$""")

/**
 * Высота кадра из подписи качества: `"720p"`/`"720P"`/`"720"` → `720`; всё нераспознаваемое
 * (`"auto"`, `"HD"`, пустая строка) → `null`.
 */
fun qualityHeightOf(label: String): Int? =
    QUALITY_LABEL_REGEX
        .find(label)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()

/**
 * Подбирает из [available] подпись качества под предпочтение [preferredHeight]:
 * 1. точное совпадение либо ближайшее НИЖНЕЕ (не превышаем выбор пользователя — экономия трафика
 *    и декодера важнее, чем «чуть чётче»);
 * 2. если нижних нет — ближайшее ВЕРХНЕЕ (лучше проиграть хоть как-то, чем отказаться).
 *
 * `null` — когда переопределять нечего: [preferredHeight] == `null` («Авто» — поведение хоста по
 * умолчанию), либо ни одна подпись из [available] не распознаётся ([qualityHeightOf]).
 * Возвращается подпись в том виде, как её отдал источник (`"720p"`), не нормализованная — по ней
 * дальше ищут URL/пункт меню.
 */
fun pickQualityForPreference(
    preferredHeight: Int?,
    available: Collection<String>,
): String? {
    if (preferredHeight == null) return null
    val parsed = available.mapNotNull { label -> qualityHeightOf(label)?.let { height -> label to height } }
    val chosen =
        parsed.filter { (_, height) -> height <= preferredHeight }.maxByOrNull { (_, height) -> height }
            ?: parsed.minByOrNull { (_, height) -> height }
    return chosen?.first
}

/**
 * Применение предпочтения к хостовому меню качества (Android/iOS, где качеством рулит страница
 * embed-плеера, а не мы): ровно ОДИН раз на источник, как только мост сообщил, что видео найдено и
 * меню качеств непустое.
 *
 * Почему «один раз»: последующий ручной выбор в плеере действует на текущую серию и НЕ должен
 * перебиваться настройкой — иначе любое сообщение моста (они идут раз в 500 мс) откатывало бы
 * выбор пользователя. Почему не раньше `isVideoFound`: до создания `<video>` хост не обработает
 * клик по пункту меню.
 *
 * Не потокобезопасен — вызывается только из колбэка моста (главный поток платформы).
 */
internal class PreferredQualityApplier {
    private var preferredHeight: Int? = null
    private var applied = false

    /** Задаёт предпочтение; не сбрасывает «уже применено» (смена настройки посреди серии не должна перебивать её). */
    fun setPreferred(heightPx: Int?) {
        preferredHeight = heightPx
    }

    /** Новый источник (серия/озвучка) — предпочтение снова нужно применить. */
    fun reset() {
        applied = false
    }

    /**
     * По очередному [state] моста возвращает подпись качества, на которое надо переключить меню
     * хоста, либо `null` — ничего делать не нужно (не готовы / «Авто» / уже применено / уже на нём).
     */
    fun onState(state: EmbedVideoState): String? {
        val height = preferredHeight
        val ready = height != null && !applied && state.isVideoFound && state.availableQualities.isNotEmpty()
        // `applied` ставим при готовности, даже если подходящей подписи не нашлось: иначе мост будет
        // переспрашивать выбор на каждом своём тике (каждые 500 мс) до конца серии.
        applied = applied || ready
        val target = if (ready) pickQualityForPreference(height, state.availableQualities) else null
        return target?.takeUnless { it.equals(state.currentQuality, ignoreCase = true) }
    }
}
