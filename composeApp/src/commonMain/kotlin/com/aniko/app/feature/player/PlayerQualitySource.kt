package com.aniko.app.feature.player

/**
 * Откуда [PlayerScreen] берёт список качеств для чипа «Качество» (P16-фикс 2026-09-09,
 * Desktop-доводка переключения качества этой веткой):
 * - **Android/iOS** — `false`: WebView-плеер хоста сам скрейпит реальные качества из своего
 *   quality-dropdown (мост публикует их в `EmbedVideoState.availableQualities`), а пока хост
 *   молчит — `playerEmbedQualities` даёт фолбэк по домену (Kodik: 720p/480p), потому что
 *   переключение там всё равно происходит внутри страницы хоста по ЛЮБОМУ лейблу из его меню;
 * - **Desktop** — `true`: видео играет родной VLCJ по уже резолвнутому прямому потоку, меню
 *   хоста в UI отсутствует вовсе — чип может предлагать ТОЛЬКО то, что `DesktopStreamResolver`
 *   реально отрезолвил и прозондировал (`EmbedVideoController` публикует это в
 *   `availableQualities`). Хардкод-фолбэк по домену тут был бы вреден: он обещает качества,
 *   которые движок переключить не в состоянии (например, пока резолв идёт или для Sibnet).
 *
 * Единственный expect/actual плашки качества — тот же паттерн, что
 * [rememberIsKeyboardShortcutsPlatform] (см. её KDoc).
 */
expect fun isEmbedQualityControllerDriven(): Boolean
