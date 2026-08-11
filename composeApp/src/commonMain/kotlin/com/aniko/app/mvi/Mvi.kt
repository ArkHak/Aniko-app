package com.aniko.app.mvi

/** Маркерный интерфейс состояния экрана — immutable snapshot, читается через `StateFlow`. */
interface UiState

/** Маркерный интерфейс команды от UI к ViewModel — обычно `sealed interface`. */
interface UiIntent

/**
 * Маркерный интерфейс одноразового события (снекбар, диалог, scroll-to-top, share) — НЕ для
 * навигации: навигация решается UI-слоем по `AnixWindowSize`/`LocalTitleNavigator` (P5.T3),
 * ViewModel про размер окна не знает и знать не должен.
 */
interface UiEffect
