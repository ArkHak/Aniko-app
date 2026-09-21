package com.aniko.player

import kotlin.math.abs

/*
 * Чистый (без VLC/AWT/сети) автомат смены качества видео — «мозг» Desktop-переключения.
 *
 * Зачем отдельный класс. На Desktop качеством рулим САМИ: у каждого качества свой URL потока
 * (`DesktopStreamResolver.Resolved.qualityStreams`), а «смена качества» = перезапуск libVLC на
 * другом URL. Наивная реализация («запомнить позицию → `media().play(url)` → по событию `playing`
 * сделать `setTime`») имела целый класс дефектов (см. KDoc `EmbedVideoController.desktop.kt`):
 * позиция терялась при быстром повторном выборе, откатов не было, а события старого медиа
 * принимались за события нового. Здесь вся эта политика — в одном тестируемом месте; платформенная
 * часть (VLCJ) лишь исполняет [QualitySwitchEngine] и пересылает сюда события.
 *
 * Контракт потоков: класс НЕ потокобезопасен и ждёт, что все вызовы идут с одного потока
 * (Desktop-контроллер сериализует их через `MediaPlayer.submit`). Так же, как и вызовы
 * [QualitySwitchEngine] — они нативные, и vlcj запрещает дёргать libVLC из его событийного потока.
 */

/**
 * Снимок воспроизведения, снятый ДО смены качества, — то, что нужно вернуть на новом потоке.
 * `null` в поле — «не трогать» (так стартовый запуск не сбрасывает пользовательскую громкость).
 */
internal data class PlaybackSnapshot(
    val positionMs: Long,
    /** Намерение «играть»: `true` и для буферизации/открытия, `false` только на паузе/после конца. */
    val isPlaying: Boolean,
    val rate: Float? = null,
    val volume: Int? = null,
    val isMuted: Boolean? = null,
)

/**
 * Что автомат умеет просить у плеера. Все методы вызываются с того же потока, что и сам автомат.
 */
internal interface QualitySwitchEngine {
    /** Снимок ТЕКУЩЕГО (стабильного) воспроизведения: позиция, играет ли, скорость, громкость. */
    fun snapshot(): PlaybackSnapshot

    /**
     * Запускает [url] сразу с [startPositionMs] и (при [startPaused]) на паузе — опциями старта
     * медиа (`:start-time`/`:start-paused`), а не «стартовать с нуля и перемотать»: так нет
     * видимого мигания началом серии и лишнего проигрывания звука на паузе.
     *
     * @return `false` — плеер не смог даже принять медиа (тогда события не придут вовсе).
     */
    fun load(
        url: String,
        startPositionMs: Long,
        startPaused: Boolean,
    ): Boolean

    /** Текущая позиция нового потока, `null` — плеер её ещё не знает. */
    fun positionMs(): Long?

    fun seekTo(positionMs: Long)

    fun setPaused(paused: Boolean)

    fun setRate(rate: Float)

    fun setVolume(volume: Int)

    fun setMuted(muted: Boolean)

    /** Через [delayMs] позвать [QualitySwitchCoordinator.onTimeout] с этим [token] (в том же потоке). */
    fun scheduleTimeout(
        token: Long,
        delayMs: Long,
    )
}

/**
 * Публикуемое состояние автомата.
 *
 * @param currentQuality качество, которое реально играет (последнее стабильное), `null` — неизвестно
 * либо плеер «мёртв» после неудачного отката. На стартовой загрузке предпочтительного качества —
 * само предпочтительное (см. [QualitySwitchCoordinator.startPlayback]).
 * @param switchingTo цель видимой пользователю смены (показываем индикатор и «замораживаем» время/
 * состояние play-pause на UI); `null` — смены нет. Стартовый запуск с предпочтительным качеством
 * ([QualitySwitchCoordinator.startPlayback]) видимой сменой не считается.
 * @param failure последний сбой смены (см. [QualitySwitchFailure]); `null` — сбоя не было.
 */
internal data class QualitySwitchState(
    val currentQuality: String? = null,
    val switchingTo: String? = null,
    val failure: QualitySwitchFailure? = null,
)

/**
 * Сбой смены качества (Desktop): [requested] не запустилось за [QualitySwitchCoordinator.switchTimeoutMs]
 * либо плеер вернул ошибку. [restoredTo] — качество, на которое автомат откатился (воспроизведение
 * продолжается), либо `null`, если откатываться было некуда/откат тоже не удался (плеер «мёртв»).
 * [id] растёт с каждым сбоем: UI показывает уведомление ровно один раз на [id].
 */
data class QualitySwitchFailure(
    val id: Int,
    val requested: String,
    val restoredTo: String?,
)

// TooManyFunctions: 12 коротких обработчиков событий/шагов — это и есть разложение конечного
// автомата (по одной функции на событие плеера и на шаг политики); их слияние вернуло бы
// ветвление внутрь тел и ухудшило бы читаемость состояний.
/**
 * Автомат смены качества. Жизненный цикл: [startPlayback] (источник отрезолвлен) → сколько угодно
 * [request] → события плеера [onMediaChanged]/[onPlaying]/[onError]/[onTimeout].
 *
 * Гарантии (каждая закреплена тестом в `QualitySwitchCoordinatorTest`):
 * - **позиция/пауза/скорость/громкость** снимаются один раз ДО смены и возвращаются после готовности
 *   нового потока; позиция передаётся в старт медиа, а после `playing` лишь сверяется;
 * - **гонки**: быстрый повторный выбор перезапускает загрузку, но снимок остаётся ПЕРВОНАЧАЛЬНЫМ
 *   (иначе позиция «нового» медиа, которое ещё ничего не сыграло, затёрла бы настоящую) —
 *   побеждает последний выбор; события от предыдущего медиа отсекаются счётчиком `mediaChanged`;
 * - **откат**: при ошибке/таймауте нового качества возвращаем последнее стабильное с той же
 *   позицией и сообщаем о сбое ([QualitySwitchFailure]); если откатываться некуда/откат тоже
 *   упал — сообщаем `restoredTo = null`.
 */
@Suppress("TooManyFunctions")
internal class QualitySwitchCoordinator(
    private val engine: QualitySwitchEngine,
    private val onState: (QualitySwitchState) -> Unit,
    /** Сколько ждём `playing` нового потока до объявления сбоя (и отката). */
    val switchTimeoutMs: Long = DEFAULT_SWITCH_TIMEOUT_MS,
) {
    private var streams: Map<String, String> = emptyMap()
    private var current: String? = null
    private var failure: QualitySwitchFailure? = null
    private var failureCount = 0
    private var nextToken = 0L
    private var attempt: Attempt? = null

    /** Одна «серия» загрузок, объединённых общей целью пользователя, снимком и точкой отката. */
    private class Attempt(
        var target: String,
        /** Последнее стабильное качество ДО всей серии загрузок — сюда откатываемся. */
        val fallback: String?,
        val snapshot: PlaybackSnapshot,
        var isInitial: Boolean,
    ) {
        /** Что пользователь запросил последним — его показываем в отчёте о сбое. */
        var requested: String = target
        var token: Long = 0L

        /** Сколько `load` ещё не подтверждены событием `mediaChanged` (см. [onMediaChanged]). */
        var pendingMediaChanges: Int = 0
        var isRollback: Boolean = false
    }

    /**
     * Источник отрезолвлен: запоминаем [streams] и стартуем поток. Если [preferredQuality] задана,
     * есть в [streams] и отличается от [defaultQuality] — сначала пробуем её, а при провале
     * откатываемся на [defaultQuality] (умолчание резолвера — заведомо прозондированное).
     * Иначе играем [defaultQuality] без всякой «смены».
     */
    fun startPlayback(
        streams: Map<String, String>,
        defaultQuality: String,
        preferredQuality: String?,
    ) {
        val defaultUrl = streams[defaultQuality] ?: return
        this.streams = streams
        current = defaultQuality
        failure = null
        attempt = null
        val target = preferredQuality?.takeIf { it != defaultQuality && it in streams }
        if (target == null) {
            if (!engine.load(defaultUrl, 0L, false)) markDead(defaultQuality)
        } else {
            val initial = Attempt(target, fallback = defaultQuality, snapshot = INITIAL_SNAPSHOT, isInitial = true)
            attempt = initial
            load(initial, streams.getValue(target))
        }
        publish()
    }

    /** Пользователь выбрал [quality]; неизвестное/уже играющее качество — тихий no-op (без публикаций). */
    fun request(quality: String) {
        val url = streams[quality] ?: return
        val active = attempt
        // Уже играет это качество либо уже грузим именно его (в т.ч. как откат) — повторный тап
        // не перезапускает загрузку и не публикует состояние.
        val noop =
            when {
                active == null -> quality == current
                else -> quality == active.target
            }
        if (noop) return
        if (active == null) {
            val next = Attempt(quality, fallback = current, snapshot = engine.snapshot(), isInitial = false)
            attempt = next
            failure = null
            load(next, url)
        } else {
            // Побеждает последний выбор. Снимок и точка отката остаются ПЕРВОНАЧАЛЬНЫМИ.
            active.target = quality
            active.requested = quality
            active.isRollback = false
            active.isInitial = false
            failure = null
            load(active, url)
        }
        publish()
    }

    /** libVLC подтвердил смену медиа: одна из ожидаемых загрузок «дошла» до плеера. */
    fun onMediaChanged() {
        val active = attempt ?: return
        if (active.pendingMediaChanges > 0) active.pendingMediaChanges--
    }

    /** Поток начал играть. События, не относящиеся к идущей смене, игнорируются. */
    fun onPlaying() {
        val active = attempt ?: return
        // Пока не подтверждена смена медиа на ПОСЛЕДНЮЮ загрузку — это «playing» старого потока.
        if (active.pendingMediaChanges > 0) return
        complete(active)
    }

    /**
     * Плеер сообщил об ошибке. `true` — событие относится к идущей смене (обработано либо отброшено как
     * устаревшее), контроллер НЕ должен объявлять плеер мёртвым; `false` — смены нет, ошибка обычная.
     */
    fun onError(): Boolean {
        val active = attempt ?: return false
        if (active.pendingMediaChanges == 0) fail(active)
        return true
    }

    /** Сработал таймаут загрузки [token]; устаревшие токены (загрузку уже заменили) игнорируются. */
    fun onTimeout(token: Long) {
        val active = attempt ?: return
        if (active.token != token) return
        // Подтверждения mediaChanged от зависшей загрузки больше не ждём — иначе откат не примет `playing`.
        active.pendingMediaChanges = 0
        fail(active)
    }

    /** Источник сменился/плеер отцеплен: забываем всё. Счётчик сбоев не сбрасываем — id остаются уникальными. */
    fun reset() {
        streams = emptyMap()
        current = null
        failure = null
        attempt = null
        publish()
    }

    private fun load(
        active: Attempt,
        url: String,
    ) {
        active.token = ++nextToken
        active.pendingMediaChanges++
        val snapshot = active.snapshot
        val accepted = engine.load(url, snapshot.positionMs, startPaused = !snapshot.isPlaying)
        if (accepted) {
            engine.scheduleTimeout(active.token, switchTimeoutMs)
        } else {
            active.pendingMediaChanges--
            fail(active)
        }
    }

    private fun complete(active: Attempt) {
        val snapshot = active.snapshot
        // Позиция передана опцией старта; здесь лишь страховка — некоторые потоки старт-позицию игнорируют.
        val position = engine.positionMs()
        if (snapshot.positionMs > 0L && position != null && abs(position - snapshot.positionMs) > SEEK_TOLERANCE_MS) {
            engine.seekTo(snapshot.positionMs)
        }
        // Скорость/громкость/mute libVLC 3.0.23 на смене медиа сохраняет сам (замер на реальном плеере),
        // но это свойство версии libVLC, а не контракт: возвращаем пользовательские значения идемпотентно
        // (движок пропускает вызов, если значение уже то же), чтобы смена качества не зависела от версии.
        snapshot.rate?.takeIf { it != 1f }?.let(engine::setRate)
        snapshot.volume?.let(engine::setVolume)
        snapshot.isMuted?.let(engine::setMuted)
        // Явная идемпотентная пауза: `:start-paused` игнорируется частью потоков.
        if (!snapshot.isPlaying) engine.setPaused(true)
        attempt = null
        current = active.target
        failure = if (active.isRollback) QualitySwitchFailure(++failureCount, active.requested, active.target) else null
        publish()
    }

    private fun fail(active: Attempt) {
        val fallbackUrl = active.fallback?.let(streams::get)
        if (active.isRollback || fallbackUrl == null) {
            markDead(active.requested)
            return
        }
        active.isRollback = true
        active.target = requireNotNull(active.fallback)
        load(active, fallbackUrl)
        publish()
    }

    /** Ни новое качество, ни откат не запустились — плеер «мёртв»; сообщаем об этом сбоем без отката. */
    private fun markDead(requested: String) {
        attempt = null
        current = null
        failure = QualitySwitchFailure(++failureCount, requested, restoredTo = null)
        publish()
    }

    private fun publish() {
        val active = attempt
        val visibleSwitch = active?.takeUnless { it.isInitial }
        // Стартовая загрузка предпочтительного качества — не «смена»: чип сразу показывает то качество,
        // которое пользователь выбрал в настройках, а не умолчание резолвера на время буферизации.
        val shown = active?.takeIf { it.isInitial }?.target ?: current
        onState(QualitySwitchState(currentQuality = shown, switchingTo = visibleSwitch?.target, failure = failure))
    }

    companion object {
        /** 15 с: Kodik/AniLibria за это время либо отдают первый сегмент, либо не отдадут вовсе. */
        const val DEFAULT_SWITCH_TIMEOUT_MS: Long = 15_000L

        /** Расхождение позиции, после которого считаем, что поток стартовал НЕ с нужного места, и перематываем. */
        const val SEEK_TOLERANCE_MS: Long = 3_000L

        private val INITIAL_SNAPSHOT = PlaybackSnapshot(positionMs = 0L, isPlaying = true)
    }
}
