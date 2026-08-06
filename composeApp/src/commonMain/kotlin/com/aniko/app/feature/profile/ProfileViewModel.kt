package com.aniko.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniko.data.repository.ProfileRepository
import com.aniko.model.AnixError
import com.aniko.model.FriendRequestVisibility
import com.aniko.model.PrivacyVisibility
import com.aniko.model.ProfileDetails
import com.aniko.model.ProfilePrivacy
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: ProfileDetails? = null,
    val privacy: ProfilePrivacy? = null,
    val isLoading: Boolean = true,
    val error: AnixError? = null,
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
 */
class ProfileViewModel(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val profileDeferred = async { profileRepository.myProfile() }
                    val privacyDeferred = async { profileRepository.privacyPreferences() }
                    profileDeferred.await() to privacyDeferred.await()
                }
            }
                .onSuccess { (profile, privacy) ->
                    _uiState.value = _uiState.value.copy(
                        profile = profile,
                        privacy = privacy,
                        isLoading = false,
                        error = null,
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = throwable as? AnixError ?: AnixError.Unknown(throwable),
                    )
                }
        }
    }

    fun retry() {
        load()
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
