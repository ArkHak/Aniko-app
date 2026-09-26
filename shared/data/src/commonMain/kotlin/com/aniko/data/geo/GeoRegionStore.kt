package com.aniko.data.geo

import com.aniko.model.UserRegion
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

/**
 * Локальный кэш сетевого региона пользователя ([UserRegion], geo-IP — см. KDoc модели).
 *
 * Тот же паттерн, что [com.aniko.data.voicepin.LocalVoicePinStore]: обычный [Settings]
 * (plaintext), без secure storage — регион пользователя не секрет, а потеря приводит максимум
 * к одному лишнему geo-запросу. Персистятся сырой код страны (`geo.country_code`, "RU"/"NL"/...)
 * и штамп проверки (`geo.checked_at`, epoch-мillis) — TTL [TTL_MILLIS]: чаще одного раза в
 * сутки сеть за регионом не ходим (у пользователя не прыгает страна несколько раз в день, а
 * у лишних запросов на сторонний geo-хост есть и латентность, и «светимость» IP приложения).
 *
 * Потокобезопасность — как у соседних сторов: [MutableStateFlow] как единственная мутируемая
 * точка, персист — простыми синхронными записями в `Settings` (доступные реализации
 * потокобезопасны для UI-потоков; гонок чтение→запись между UI-корутинами у приложения
 * одного пользователя нет, а в худшем случае ущерб — лишний повторный geo-запрос).
 *
 * Семантика fail-open (решение пользователя 2026-09-26, см. KDoc `UserRegion`): сбой
 * [GeoRegionApi] (`null`) НЕ меняет ни текущее значение, ни персист — пользователь продолжает
 * играть по последнему известному/UNKNOWN региону, а не получает блокировку «по неизвестности».
 */
class GeoRegionStore(
    private val settings: Settings,
    private val api: GeoRegionApi,
    private val clock: Clock = Clock.System,
) {
    private val regionFlow = MutableStateFlow(readPersistedRegion())

    /** Текущий известный регион: стартовое значение — из персиста, иначе [UserRegion.UNKNOWN]. */
    val region: StateFlow<UserRegion> = regionFlow.asStateFlow()

    /**
     * Обновляет регион по сети, ЕСЛИ персист отсутствует или протух по [TTL_MILLIS]; свежий
     * персист — no-op без единого запроса (см. KDoc класса про TTL).
     *
     * Маппинг кода страны: "RU" (регистронезависимо) → [UserRegion.RUSSIA], любой другой
     * непустой код → [UserRegion.OTHER]. `null` от [GeoRegionApi] — ничего не меняем и персист
     * не затираем (fail-open, см. KDoc класса).
     */
    suspend fun refreshIfStale() {
        val checkedAtMs = settings.getLongOrNull(KEY_CHECKED_AT) ?: 0L
        if (clock.now().toEpochMilliseconds() - checkedAtMs < TTL_MILLIS) return
        val code =
            try {
                api.countryCode()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception,
            ) {
                // Защитный слой поверх контракта `GeoRegionApi` (реализация уже возвращает null на
                // любой сбой): неожиданное исключение из фейка/сторонней реализации всё равно не
                // должно ронять экран — поведение то же, что и у сбоя сети (fail-open, см. KDoc).
                null
            }
        if (code.isNullOrBlank()) return
        val normalized = code.trim().uppercase()
        val region = if (normalized == RUSSIA_CODE) UserRegion.RUSSIA else UserRegion.OTHER
        settings.putString(KEY_COUNTRY_CODE, normalized)
        settings.putLong(KEY_CHECKED_AT, clock.now().toEpochMilliseconds())
        regionFlow.value = region
    }

    private fun readPersistedRegion(): UserRegion {
        val code = settings.getStringOrNull(KEY_COUNTRY_CODE)?.trim()?.uppercase()
        return when {
            code.isNullOrEmpty() -> UserRegion.UNKNOWN
            code == RUSSIA_CODE -> UserRegion.RUSSIA
            else -> UserRegion.OTHER
        }
    }

    internal companion object {
        const val KEY_COUNTRY_CODE: String = "geo.country_code"
        const val KEY_CHECKED_AT: String = "geo.checked_at"
        const val RUSSIA_CODE: String = "RU"
        const val TTL_MILLIS: Long = 24L * 60 * 60 * 1000
    }
}
