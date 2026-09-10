package com.aniko.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniko.data.repository.AuthRepository
import com.aniko.data.repository.NotificationRepository
import com.aniko.data.repository.ProfileRepository
import com.aniko.model.Achievement
import com.aniko.model.AnixError
import com.aniko.model.FriendRequestVisibility
import com.aniko.model.PrivacyVisibility
import com.aniko.model.ProfileDetails
import com.aniko.model.ProfilePrivacy
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: ProfileDetails? = null,
    val privacy: ProfilePrivacy? = null,
    val isLoading: Boolean = true,
    val error: AnixError? = null,
    /**
     * Гостевой режим (P9.T12): сессии нет — показываем приглашение войти, а не сетевую ошибку.
     * Взаимоисключающе с [error]: [AnixError.Unauthorized] всегда трактуется как «гость».
     */
    val isGuest: Boolean = false,
    /**
     * Уже полученные значки (`profile/preference/badge/all/0`). Грузится отдельным, не
     * блокирующим запросом после успешной загрузки [profile]/[privacy] — при ошибке остаётся
     * пустым списком, секция `AchievementsSection` в этом случае просто не показывается
     * (как `FavoriteGenresSection` при пустых `preferredGenres`), а не роняет весь экран.
     */
    val achievements: List<Achievement> = emptyList(),
    /**
     * Бейдж непрочитанных уведомлений (P16.T18) — [NotificationRepository.unreadBadgeCount], на
     * колокольчике [ProfileScreen.onNotificationsClick]. Обновляется [ProfileViewModel.refreshUnreadNotifications],
     * не частью [load]: экран уведомлений может «увидеть» текущий счётчик
     * ([NotificationRepository.acknowledgeSeen]) уже ПОСЛЕ того, как этот `ViewModel` создан и
     * закэшировал старое значение — нужен способ обновить бейдж при КАЖДОМ возврате на экран
     * профиля, а не один раз при первой загрузке.
     */
    val unreadNotificationsCount: Long = 0,
)

/**
 * ViewModel экрана «Мой профиль» (Фаза 7).
 *
 * [ProfileDetails] и [ProfilePrivacy] тянутся параллельно (`coroutineScope { async { ... } }`) —
 * это два независимых запроса (`profile/<id>` и `profile/preference/my`), пока в проекте это
 * первый ViewModel, которому нужно больше одного запроса на первичную загрузку.
 *
 * Privacy-мутации (`updatePrivacy*`/[toggleIncognito]) — оптимистичные, тем же паттерном, что
 * [com.aniko.app.feature.library.LibraryViewModel.toggleFavorite]: локальный `privacy` в
 * [uiState] обновляется сразу, а при ошибке сети откатывается на предыдущее значение.
 *
 * Фаза 9 (P9.T12) добавила гостевой режим. Штатно экран профиля недостижим без авторизации —
 * `AnixSessionGate` в `App.kt` показывает `LoginScreen` на всём приложении, — но состояние
 * «сессии нет» на этом экране всё равно достижимо: `ProfileRepository.myProfile()` бросает
 * [AnixError.Unauthorized], если в `SessionStore` нет `profileId` (токен есть, id потерян), и
 * тот же [AnixError.Unauthorized] прилетает при 401/403 от бэкенда. До Фазы 9 оба случая
 * показывали общий «не удалось загрузить профиль» — теперь это явный гостевой экран с кнопкой
 * входа ([signOut] сбрасывает остатки сессии, после чего гейт в `App.kt` сам уводит на логин).
 */
@Suppress("TooManyFunctions") // P16.T18: refreshUnreadNotifications добавлен к уже плотному набору privacy-мутаций.
class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, isGuest = false)
        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val profileDeferred = async { profileRepository.myProfile() }
                    val privacyDeferred = async { profileRepository.privacyPreferences() }
                    profileDeferred.await() to privacyDeferred.await()
                }
            }.onSuccess { (profile, privacy) ->
                _uiState.value =
                    _uiState.value.copy(
                        profile = profile,
                        privacy = privacy,
                        isLoading = false,
                        error = null,
                        isGuest = false,
                    )
                loadAchievements()
            }.onFailure { throwable ->
                val error = throwable as? AnixError ?: AnixError.Unknown(throwable)
                _uiState.value =
                    if (error is AnixError.Unauthorized) {
                        // Сессии больше нет — сбрасываем состояние целиком, а не только флаг:
                        // иначе под гостевым экраном остались бы висеть данные прошлого
                        // пользователя, которые снова показались бы при любом обновлении стейта.
                        ProfileUiState(isLoading = false, isGuest = true)
                    } else {
                        _uiState.value.copy(isLoading = false, error = error)
                    }
            }
        }
    }

    fun retry() {
        load()
    }

    /**
     * Обновляет бейдж непрочитанных уведомлений — см. KDoc `ProfileUiState.unreadNotificationsCount`
     * про то, почему это отдельный публичный метод, а не часть [load]. Вызывается из
     * `LaunchedEffect(Unit)` `ProfileScreen` при каждой рекомпозиции экрана (в т.ч. при возврате
     * с экрана уведомлений), поэтому не должно быть дорогим — один `GET notification/count`.
     */
    fun refreshUnreadNotifications() {
        viewModelScope.launch {
            runCatching { notificationRepository.unreadBadgeCount() }
                .onSuccess { count -> _uiState.value = _uiState.value.copy(unreadNotificationsCount = count) }
        }
    }

    /**
     * Отдельный, не блокирующий основной экран запрос за значками. Намеренно не участвует в
     * `runCatching` из [load]: ошибка/пустой ответ здесь означает «не показываем секцию», а не
     * «профиль не загрузился» — в отличие от `profile`/`privacy`, без которых экран непоказуем.
     */
    private fun loadAchievements() {
        viewModelScope.launch {
            runCatching { profileRepository.achievements() }
                .onSuccess { achievements -> _uiState.value = _uiState.value.copy(achievements = achievements) }
        }
    }

    /**
     * Сбрасывает локальную сессию — единственный способ из экрана профиля попасть на логин:
     * навигация на него не маршрут, а следствие `sessionState` (см. `AnixSessionGate`).
     */
    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    fun updatePrivacyStats(value: PrivacyVisibility) {
        updatePrivacyOptimistic(
            apply = { it.copy(stats = value) },
            request = { profileRepository.updatePrivacyStats(value) },
        )
    }

    fun updatePrivacyCounts(value: PrivacyVisibility) {
        updatePrivacyOptimistic(
            apply = { it.copy(counts = value) },
            request = { profileRepository.updatePrivacyCounts(value) },
        )
    }

    fun updatePrivacySocial(value: PrivacyVisibility) {
        updatePrivacyOptimistic(
            apply = { it.copy(social = value) },
            request = { profileRepository.updatePrivacySocial(value) },
        )
    }

    fun updatePrivacyFriendRequests(value: FriendRequestVisibility) {
        updatePrivacyOptimistic(
            apply = { it.copy(friendRequests = value) },
            request = { profileRepository.updatePrivacyFriendRequests(value) },
        )
    }

    fun toggleIncognito() {
        updatePrivacyOptimistic(
            apply = { it.copy(isIncognito = !it.isIncognito) },
            request = { profileRepository.toggleIncognito() },
        )
    }

    /**
     * Общий каркас оптимистичной privacy-мутации: применяет [apply] к текущему `privacy` сразу,
     * запускает [request] и откатывает на снимок до изменения, если запрос упал. Если `privacy`
     * ещё не загружен (маловероятно — экран блокирует UI до успешной [load]), мутация — no-op.
     */
    private inline fun updatePrivacyOptimistic(
        crossinline apply: (ProfilePrivacy) -> ProfilePrivacy,
        crossinline request: suspend () -> Unit,
    ) {
        val previous = _uiState.value.privacy ?: return
        _uiState.value = _uiState.value.copy(privacy = apply(previous))
        viewModelScope.launch {
            runCatching { request() }
                .onFailure { _uiState.value = _uiState.value.copy(privacy = previous) }
        }
    }
}
