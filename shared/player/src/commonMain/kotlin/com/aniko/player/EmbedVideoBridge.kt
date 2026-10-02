package com.aniko.player

/*
 * Протокол моста «Kotlin ↔ <video> внутри чужой embed-страницы».
 *
 * Топология, подтверждённая живым спайком на Android (P8, эмулятор, Sibnet + Kodik):
 * `EmbedPlayerView` грузит embed-страницу так, что реальный `<video>` всегда оказывается
 * в CROSS-ORIGIN подфрейме относительно главного документа (у Kodik — из-за нашей же
 * HTML-обёртки с `<iframe>`, у остальных хостов — из-за их собственной вёрстки).
 * Следствие: обычный `WebView.evaluateJavascript` / `WKWebView.evaluateJavaScript` до видео
 * НЕ достаёт — они исполняются только в главном фрейме. Работает единственная связка:
 *
 * - Android — `WebViewCompat.addDocumentStartJavaScript` (инжект в КАЖДЫЙ фрейм до его
 *   собственных скриптов) + `WebViewCompat.addWebMessageListener` (двусторонний канал);
 * - iOS — `WKUserScript(injectionTime = AtDocumentStart, forMainFrameOnly = false)` +
 *   `WKScriptMessageHandler` (JS → Kotlin) + `evaluateJavaScript(inFrame = ...)` (Kotlin → JS).
 *
 * Обе платформы гоняют один и тот же JS ([embedBridgeScript]) и один и тот же текстовый
 * протокол, поэтому и скрипт, и парсер живут здесь, в commonMain.
 */

/** Имя канала: и `jsObjectName` на Android, и имя `messageHandlers.<name>` на iOS. */
internal const val EMBED_BRIDGE_CHANNEL = "AnikoEmbedBridge"

/** Версия протокола — первое поле каждого сообщения JS → Kotlin. */
internal const val EMBED_BRIDGE_PROTOCOL = "v1"

/** Глобальная JS-функция, через которую iOS доставляет команды в нужный фрейм. */
internal const val EMBED_BRIDGE_EXEC_FN = "__anikoEmbedExec"

/** Команды Kotlin → JS. Плоские строки: JSON тут не нужен, а зависимость на сериализацию — тем более. */
internal object EmbedVideoCommand {
    const val PLAY = "play"
    const val PAUSE = "pause"

    fun seek(positionMs: Long): String = "seek:$positionMs"

    fun seekBy(deltaMs: Long): String = "seekBy:$deltaMs"

    fun rate(rate: Float): String = "rate:$rate"

    fun quality(quality: String): String = "quality:$quality"
}

/**
 * Разбирает сообщение JS → Kotlin вида
 * `v1|<найдено 0/1>|<играет 0/1>|<позиция мс>|<длительность мс или `-`>|<скорость>`, затем
 * опционально `|<качества csv>|<текущее>|<буферизация 0/1>|<реклама 0/1>`.
 *
 * `-` в поле длительности — это `duration = NaN` до события `loadedmetadata`
 * (подтверждено спайком: сразу после загрузки страницы `duration=NaN`, `readyState=0`).
 * Именно поэтому [EmbedVideoState.durationMs] нулябельный, а не `0L`: прогресс-бар нельзя
 * включать, пока длительность неизвестна.
 *
 * @return `null`, если сообщение не наше/битое — тогда состояние не трогаем.
 */
internal fun parseEmbedVideoState(raw: String): EmbedVideoState? {
    val parts = raw.split('|')
    if (parts.size < EMBED_BRIDGE_FIELDS || parts[0] != EMBED_BRIDGE_PROTOCOL) return null
    return EmbedVideoState(
        isVideoFound = parts[1] == "1",
        isPlaying = parts[2] == "1",
        currentTimeMs = parts[3].toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
        durationMs = parts[4].toLongOrNull()?.takeIf { it > 0L },
        playbackRate = parts[5].toFloatOrNull()?.takeIf { it > 0f } ?: 1f,
        availableQualities =
            parts
                .getOrNull(EMBED_BRIDGE_QUALITIES_FIELD)
                .orEmpty()
                .split(',')
                .map(String::trim)
                .filter { it.isNotEmpty() },
        currentQuality = parts.getOrNull(EMBED_BRIDGE_CURRENT_QUALITY_FIELD)?.trim()?.takeIf { it.isNotEmpty() },
        isBuffering = parts.getOrNull(EMBED_BRIDGE_BUFFERING_FIELD) == "1",
        isAdPlaying = parts.getOrNull(EMBED_BRIDGE_AD_FIELD) == "1",
    )
}

private const val EMBED_BRIDGE_FIELDS = 6

/** Индекс опционального поля «качества через запятую» в payload моста (после базовых шести). */
private const val EMBED_BRIDGE_QUALITIES_FIELD = 6

/** Индекс опционального поля «текущее качество» в payload моста. */
private const val EMBED_BRIDGE_CURRENT_QUALITY_FIELD = 7

/** Индекс опционального поля «видео буферизуется» (`1`/`0`) в payload моста. */
private const val EMBED_BRIDGE_BUFFERING_FIELD = 8

/** Индекс опционального поля «хост показывает рекламу» (`1`/`0`) в payload моста. */
private const val EMBED_BRIDGE_AD_FIELD = 9

/**
 * Маркер отладочного сообщения моста — второе поле payload, сразу после [EMBED_BRIDGE_PROTOCOL].
 *
 * Формат: `v1|dbgchrome|<entries>`. Сообщение не является state-апдейтом: у него меньше полей,
 * чем [EMBED_BRIDGE_FIELDS], поэтому [parseEmbedVideoState] честно возвращает `null` и состояние
 * плеера не затирается. Включается только вручную (флаг `window.__anikoDebugChrome` в
 * [embedBridgeScript]) и служит живой отладке скрытия чужого chrome — см. [parseEmbedChromeDebug].
 */
internal const val EMBED_BRIDGE_DEBUG_MARKER = "dbgchrome"

/**
 * Разбирает отладочный отчёт моста о видимых chrome-элементах хоста, перекрывающих `<video>`.
 *
 * Когда в страницу плеера вручную включён `window.__anikoDebugChrome`, мост на каждом `scan()`
 * собирает описания видимых узлов, геометрически перекрывающих `<video>` (не являющихся его
 * предками), и шлёт их этим сообщением. Реальные классы просочившегося UI берутся из отчёта, а не
 * угадываются — это источник селекторов для [CHROME_HIDE_CSS]-подобных правил.
 *
 * По умолчанию хук выключен: обычные сообщения моста сюда не попадают и разбор вернёт пустой
 * список.
 *
 * @return список описаний вида `tag#id.class@zIndex` (пустые части сокращаются);
 *         пустой список, если [raw] — не отладочное сообщение.
 */
internal fun parseEmbedChromeDebug(raw: String): List<String> {
    val prefix = "$EMBED_BRIDGE_PROTOCOL|$EMBED_BRIDGE_DEBUG_MARKER|"
    if (!raw.startsWith(prefix)) return emptyList()
    return raw.removePrefix(prefix).split(';').filter { it.isNotBlank() }
}

/**
 * Фильтр «сообщение действительно из фрейма видеохоста, а не из рекламного/аналитического».
 *
 * Мост по построению получают ВСЕ фреймы страницы — спайк вживую поймал, как команду перехватил
 * фрейм `mc.yandex.ru`. Поэтому и Android (`sourceOrigin` из `WebMessageListener`), и iOS
 * (`WKScriptMessage.frameInfo.securityOrigin`) обязаны прогонять origin через этот фильтр,
 * прежде чем принять состояние или запомнить фрейм как адресата команд.
 *
 * Сравнение идёт по registrable domain, а не по полному хосту: страница плеера легко живёт на
 * `video.sibnet.ru`, а видео — на `st.sibnet.ru`, и это по-прежнему тот же хост.
 */
internal class EmbedOriginFilter(
    embedUrl: String,
) {
    private val expected: Set<String> =
        buildSet {
            registrableDomainOf(embedUrl)?.let(::add)
            // Kodik размазан по семейству доменов (kodikplayer.com отдаёт страницу, а плеер
            // внутри может сидеть на aniqit.com) — принимаем всю группу целиком.
            if (isKodikEmbedUrl(embedUrl)) {
                KODIK_EMBED_HOSTS.forEach { host -> registrableDomainOf(host)?.let(::add) }
            }
        }

    fun accepts(origin: String?): Boolean {
        if (origin.isNullOrBlank() || expected.isEmpty()) return false
        // Opaque origin (`null`, `about:blank`, data:) — не наш фрейм, отбрасываем.
        val isOpaque = origin == "null" || origin.startsWith("about:") || origin.startsWith("data:")
        val domain = if (isOpaque) null else registrableDomainOf(origin)
        return domain != null && domain in expected
    }
}

/**
 * `https://video.sibnet.ru/shell.php?v=1` → `sibnet.ru`, `anixart.libria.fun` → `libria.fun`.
 *
 * Полноценный Public Suffix List сюда не тянем: среди хостов Anixart двухуровневых суффиксов
 * нет, а [TWO_LEVEL_SUFFIXES] закрывает те, что теоретически могут появиться, — без него
 * `foo.co.uk` схлопнулось бы в `co.uk` и совпало бы с любым британским доменом.
 *
 * Похожая функция `hostOf` есть в `:shared:model` (`VideoHost.fromUrl`, `Episode.kt`) — модули не
 * связаны зависимостью, и назначение разное: здесь результат — граница доверия для фильтрации
 * сообщений JS-моста от посторонних фреймов (обязана схлопывать поддомены), там — сырой хост под
 * сравнение с фиксированным списком известных доменов без PSL-редукции. Не сливать без переноса в
 * общий модуль.
 */
internal fun registrableDomainOf(urlOrOrigin: String): String? {
    val withoutScheme = urlOrOrigin.substringAfter("//", urlOrOrigin)
    val authority = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
    val labels = host.split('.').filter { it.isNotEmpty() }
    if (labels.size < DOMAIN_LABEL_COUNT) return null
    val lastTwo = labels.takeLast(DOMAIN_LABEL_COUNT).joinToString(".")
    return if (labels.size > DOMAIN_LABEL_COUNT && lastTwo in TWO_LEVEL_SUFFIXES) {
        labels.takeLast(TWO_LEVEL_SUFFIX_LABEL_COUNT).joinToString(".")
    } else {
        lastTwo
    }
}

/** Минимум меток для двухуровневого домена (`sibnet.ru` → `["sibnet", "ru"]`). */
private const val DOMAIN_LABEL_COUNT = 2

/** Меток для трёхуровневого домена при совпадении с [TWO_LEVEL_SUFFIXES] (`foo.co.uk`). */
private const val TWO_LEVEL_SUFFIX_LABEL_COUNT = 3

private val TWO_LEVEL_SUFFIXES =
    setOf("co.uk", "org.uk", "com.ua", "co.jp", "co.kr", "com.br", "com.tr", "org.ru", "net.ru", "com.ru")

/**
 * JS-мост, инжектируемый в каждый фрейм на document-start.
 *
 * Кроме моста скрипт теперь выполняет best-effort скрытие хостового chrome, чтобы остался
 * единственный рабочий UI — наш оверлей. Два слоя:
 * - слой A (generic): `video.controls = false` + `removeAttribute('controls')` в `attach(v)` и
 *   периодическом `scan()`, плюс `<style>` со `::-webkit-media-controls*` на document-start.
 *   Это убирает нативные HTML5-контролы без per-host селекторов;
 * - слой B (per-host): статическая таблица `hostSuffix → CSS-правило`, применяемая только в
 *   фрейме, чей `location.host` совпал с суффиксом по границе метки. Селекторы кастомных
 *   плееров (Kodik, Sibnet, AniLibria) получаем из живых DOM-дампов, а не выдумываем, поэтому
 *   правила для хоста с неизвестной разметкой отсутствуют. Скрытие — ТОЛЬКО через
 *   `display:none !important`: узел остаётся в DOM, и мост по-прежнему читает из него качества
 *   и кликает по скрытым пунктам меню. Добавление правила — одна строка в [CHROME_HIDE_CSS].
 *
 * Почему это безопасно для команд моста: `play/pause/seek/rate` управляют непосредственно
 * `<video>` и не зависят от атрибута `controls`, поэтому отключение chrome не ломает
 * воспроизведение, позицию и скорость. Per-frame применение CSS безопасно: слой A использует
 * селекторы только внутри `<video>`, а слой B срабатывает только при совпадении домена фрейма,
 * поэтому рекламные/аналитические фреймы (`mc.yandex.ru`) остаются нетронутыми. Fallback при
 * неудаче скрытия — status quo: наш перехватывающий оверлей по-прежнему побеждает тапы.
 *
 * Осознанные решения, каждое — следствие конкретной находки спайка:
 * - слушаем нативные DOM-события элемента, а НЕ резолв промиса `play()`: `play()` штатно
 *   отклоняется с `AbortError: The play() request was interrupted by a call to pause()`
 *   даже когда воспроизведение реально стартовало (собственный JS хоста перехватывает первый
 *   вызов), поэтому промис — негодный источник истины про «играет/на паузе»;
 * - `loadedmetadata`/`durationchange` — единственный момент, когда `duration` перестаёт быть
 *   `NaN`; до него отдаём `-`;
 * - периодический `scan()` вместо однократного поиска: `<video>` у большинства хостов
 *   создаётся уже после document-start, а у некоторых пересоздаётся при смене качества;
 * - дедупликация по [lastPayload] — `timeupdate` летит ~4 раза в секунду, гонять одинаковые
 *   сообщения через мост незачем.
 *
 * `@Suppress("LongMethod")` — это один большой JS-литерал, не Kotlin-логика: разбивать его на
 * функции значило бы резать цельный скрипт на куски ради метрики, не ради читаемости.
 */
@Suppress("LongMethod")
internal fun embedBridgeScript(): String =
    """
    (function () {
      if (window.__anikoBridgeInstalled) { return; }
      window.__anikoBridgeInstalled = true;

      var CH = '$EMBED_BRIDGE_CHANNEL';
      var V = '$EMBED_BRIDGE_PROTOCOL';
      var video = null;
      var lastPayload = '';

      /**
       * Per-host CSS rules for hiding a host's custom player chrome.
       * Format: { suffix: 'kodikplayer.com', css: '.selector { display:none !important; }' }.
       * Boundary-label match: host === suffix || host.endsWith('.' + suffix).
       *
       * Live DOM dumps (2026-09-09, CDP over WebView remote-debugging; evidence files in chat):
       * - video.sibnet.ru/shell.php — VideoJS player: <video id="video_html5_wrapper_html5_api"
       *   class="vjs-tech"> inside #video_html5_wrapper.video-js inside #player_container
       *   (class videojs_player). Persistent chrome = .vjs-control-bar, logo
       *   #vjs-logobrand-image, .vjs-share-button, .vjs-related-carousel-button. The big play
       *   button (.vjs-big-play-button) and poster are deliberately NOT hidden: before the bridge
       *   finds <video> our overlay releases the frame, and the host's own play affordance must
       *   stay tappable for first activation.
       * - kodikplayer.com: player mounts into div.player_box. Live sessions (2026-09-09/10, CDP):
       *   Kodik overlays our UI with a resume plate (.resume-button), copy-code popups
       *   (#get_code_window/#for-copy) and the flowplayer HUD (.fp-controls/.fp-quality/...).
       *   Kodik renames its classes between releases, so the Kodik rule prefers stable wildcard
       *   selectors (endscreen/next-episode/share/logo/...) over exact class names.
       */
      var CHROME_HIDE_CSS = [
        {
          suffix: 'sibnet.ru',
          css: [
            '#player_container .vjs-control-bar',
            '#player_container #vjs-logobrand-image',
            '#player_container .vjs-share-button',
            '#player_container .vjs-related-carousel-button',
          ].join(', ') + ' { display:none !important; }',
        },
      ];

      // Kodik migrates the player page across its domain family (kodik.cc/kodik.info/aniqit.com/
      // ...) between releases — the skin is the same everywhere, so one CSS block is registered
      // for every KODIK_EMBED_HOSTS suffix (kept in sync with EmbedPlayer.kt).
      var KODIK_HOST_SUFFIXES = [
        'kodik.cc',
        'kodik.info',
        'kodik-hd.com',
        'kodik.biz',
        'aniqit.com',
        'kodikplayer.com',
        'anixmirai.com'
      ];

      var KODIK_CSS = [
        // Live session 2026-09-09 (CDP): on top of our UI Kodik shows its own overlays —
        // resume plate (.resume-button inside .main-box) and copy-code popups
        // (#get_code_window/#for-copy). The <video>/player frame itself stays untouched;
        // quality-dropdown remains in DOM (our setQuality clicks its items).
        '.main-box .resume-button',
        '.resume-button.active',
        '#get_code_window',
        '#for-copy',
        // Flowplayer HUD of Kodik (live DOM 2026-09-10): .fp-ui/.fp-controls with the
        // quality selector (.fp-quality '360p'), logo/brand and seek bar. Hide the whole
        // persistent chrome, but NOT .fp-play/.fp-ui itself: the big center play is
        // required for first activation until the bridge finds <video> (same as Sibnet).
        '.fp-controls',
        '.fp-controls-main',
        '.fp-controls-row',
        '.fp-quality',
        '.fp-quality-menu',
        '.fp-quality-dropdown',
        '.fp-dropdown',
        '.fp-playlist',
        '.fp-prev-next',
        '.fp-timeline',
        '.fp-progress',
        '.fp-buffer',
        '.fp-volume',
        '.fp-volumebar',
        '.fp-fullscreen',
        '.fp-speed',
        '.fp-subtitles',
        '.fp-settings',
        '.fp-playback-settings',
        '.fp-logo',
        '.fp-brand',
        '.fp-embed',
        '.fp-share',
        '.fp-download',
        '.movie-panel',
        '.movie-translations-box',
        '.preview-icons',
        // End-of-episode screen / «next episode» upsell — Kodik's class names for it changed
        // repeatedly, so match by stable substrings. display:none keeps the node in DOM; none
        // of these substrings occur on the <video> or its layout containers (.player/.fp-engine).
        '[class*="endscreen"]',
        '[id*="endscreen"]',
        '[class*="end-screen"]',
        '[id*="end-screen"]',
        '[class*="end_screen"]',
        '[id*="end_screen"]',
        '[class*="next-ser"]',
        '[id*="next-ser"]',
        '[class*="next_ser"]',
        '[id*="next_ser"]',
        '[class*="nextser"]',
        '[id*="nextser"]',
        '[class*="next-ep"]',
        '[id*="next-ep"]',
        '[class*="next_ep"]',
        '[id*="next_ep"]',
        '[class*="nextep"]',
        '[id*="nextep"]',
        '[class*="next-video"]',
        '[id*="next-video"]',
        '[class*="next_video"]',
        '[id*="next_video"]',
        '[class*="nextvideo"]',
        '[id*="nextvideo"]',
        '[class*="fp-next"]',
        '[id*="fp-next"]',
        '[class*="autonext"]',
        '[id*="autonext"]',
        '[class*="auto-next"]',
        '[id*="auto-next"]',
        // Leftover chrome: share/social buttons, brand logos, subtitle menus,
        // translation menus, tooltips/popups, context menus, copy/embed-code upsells.
        '[class*="share"]',
        '[id*="share"]',
        '[class*="social"]',
        '[id*="social"]',
        '[class*="logo"]',
        '[id*="logo"]',
        // Subtitles: hide only the MENU, never the caption text itself — a wildcard on
        // "subtitle"/"captions" would also blank soft-subbed voice types (subtitle dubs).
        '.fp-subtitle-menu',
        '[class*="translation"]',
        '[id*="translation"]',
        '[class*="tooltip"]',
        '[id*="tooltip"]',
        '[class*="popup"]',
        '[id*="popup"]',
        '[class*="context-menu"]',
        '[id*="context-menu"]',
        '[class*="get-code"]',
        '[id*="get-code"]',
        '[class*="getcode"]',
        '[id*="getcode"]',
        '[class*="copy-code"]',
        '[id*="copy-code"]',
        // Live DOM 2026-10-01 (Android, CDP): newer flowplayer skin names its center icon
        // .fp-big-play/.fp-play-icon/.fp-pause-icon, adds quick-seek zones .fp-qs-* and a header
        // strip; the big pause bars leaked over our UI. Playback is started via the Kodik
        // postMessage API (see startHostPlayer), so none of these is needed even before start.
        // Live 2026-10-01: Kodik's own floating plates/notifiers over the video, taken from the
        // HTML templates of its player bundle (not guessed): slow-loading banner "having
        // playback problems?" (.slow-video-block), modal dialogs, center tap animation,
        // volume notifier, skip button, error message and its own buffering spinner (ours is
        // drawn by the app from the bridge's buffering flag). Ad UI (.adv_*, .skip_adv_block)
        // is deliberately NOT hidden: the user must always be able to skip/close an ad.
        '.slow-video-block',
        '.player-modal',
        '.fp-player-modal',
        '.fp-toggle-animation',
        '.fp-volume-notifier',
        '.fp-skip-button',
        '.fp-waiting',
        '.fp-x-waiting',
        '.fp-unload',
        '.fp-big-play',
        '.fp-play-icon',
        '.fp-pause-icon',
        '.fp-qs-left',
        '.fp-qs-right',
        '.fp-header',
        '.fp-menu',
        '.fp-overlay',
        '.fp-info',
        '.fp-message',
        '.fp-error',
        '.fp-help',
        '.fp-flag',
        '.fp-email',
        '.serial-bottom',
        // Kodik's lazy-load play button: the app starts playback itself through the Kodik API
        // (see startHostPlayer) and draws its own spinner/play, so it is never needed. The
        // poster (.play_background) stays as a loading backdrop until <video> is found.
        '.play_button',
        // ...and the spinner its loader swaps it for while fetching links (p() in the player
        // bundle: .play_button -> .play_button_loading + .play_loading); ours is drawn instead.
        '.play_button_loading',
        '.play_loading',
        'html.aniko-video-found .play_background',
        'html.aniko-video-found .fp-play',
        'html.aniko-video-found .fp-pause',
        'html.aniko-video-found .fp-splash',
        'html.aniko-video-found .is-splash'
      ].join(', ') + ' { display:none !important; }';

      for (var ki = 0; ki < KODIK_HOST_SUFFIXES.length; ki++) {
        CHROME_HIDE_CSS.push({ suffix: KODIK_HOST_SUFFIXES[ki], css: KODIK_CSS });
      }

      function hostMatches(host, suffix) {
        return host === suffix || host.substring(host.length - suffix.length - 1) === '.' + suffix;
      }

      function hostChromeCss(host) {
        var out = [];
        for (var i = 0; i < CHROME_HIDE_CSS.length; i++) {
          if (hostMatches(host, CHROME_HIDE_CSS[i].suffix)) { out.push(CHROME_HIDE_CSS[i].css); }
        }
        return out.join('\n');
      }

      function refreshHostChromeStyle() {
        var host = location.host;
        var css = hostChromeCss(host);
        var id = '__anikoHostChromeHide';
        var style = document.getElementById(id);
        if (!css) {
          if (style && style.parentNode) { style.parentNode.removeChild(style); }
          return;
        }
        if (!style) {
          style = document.createElement('style');
          style.id = id;
          (document.head || document.documentElement).appendChild(style);
        }
        style.textContent = css;
      }

      function injectGlobalChromeHide() {
        var id = '__anikoGlobalChromeHide';
        if (document.getElementById(id)) { return; }
        var style = document.createElement('style');
        style.id = id;
        style.textContent =
          'video::-webkit-media-controls, video::-webkit-media-controls-enclosure { display:none !important; }';
        (document.head || document.documentElement).appendChild(style);
      }

      function post(text) {
        try {
          var obj = window[CH];
          if (obj && typeof obj.postMessage === 'function') { obj.postMessage(text); return; }
          if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers[CH]) {
            window.webkit.messageHandlers[CH].postMessage(text);
          }
        } catch (e) {}
      }

      // Host-advertised video qualities (P16 2026-09-10): flowplayer/Kodik render a quality
      // dropdown; its items are the source of truth, so we never hardcode the list. Selectors of
      // several player skins are probed; labels must be plain "<N>p".
      var QUALITY_ITEM_SELECTORS = [
        '.fp-quality-dropdown div',
        '.fp-quality-menu div',
        '.fp-quality-list div',
        '[class*="quality-dropdown"] div',
        '[class*="quality"] li'
      ];

      function collectQualities() {
        var out = [];
        for (var i = 0; i < QUALITY_ITEM_SELECTORS.length; i++) {
          var items = document.querySelectorAll(QUALITY_ITEM_SELECTORS[i]);
          for (var j = 0; j < items.length; j++) {
            var t = (items[j].textContent || '').trim();
            if (t.length <= 6 && /^[0-9]{3,4}p$/i.test(t) && out.indexOf(t) < 0) { out.push(t); }
          }
        }
        return out;
      }

      function currentQualityLabel() {
        var nodes = document.querySelectorAll('.fp-quality, [class*="quality-current"], .quality-dropdown .current');
        for (var i = 0; i < nodes.length; i++) {
          var m = ((nodes[i].textContent || '').trim()).match(/[0-9]{3,4}p/i);
          if (m) { return m[0]; }
        }
        return '';
      }

      // Host ad break (Kodik VAST, live 2026-10-02 on iOS: a pre-roll with "skip in N s"). Classes
      // come from the Kodik player bundle. While an ad is on screen the app hides its own controls
      // and stops intercepting taps, so the user can press the ad's own skip/close button.
      var AD_ACTIVE_SELECTORS = [
        '.creative-player.active',
        '.vast-html.active',
        '.vast-image.active',
        '.vast.active',
        '.skip_adv_block',
        '.adv_close',
        '.adv_resume'
      ];

      // Kodik walks an ad waterfall: each candidate creative mounts/unmounts .creative-player
      // (live iOS log: vpaid "loading" -> none -> loading -> vast within seconds). Without a grace
      // period the flag flickered and the app kept toggling its controls/tap layer. "Ad started"
      // is immediate, "ad over" only after AD_END_GRACE_MS without any ad element.
      var AD_END_GRACE_MS = 2500;
      var adLastSeenAt = 0;

      function isAdActive() {
        for (var i = 0; i < AD_ACTIVE_SELECTORS.length; i++) {
          var nodes = document.querySelectorAll(AD_ACTIVE_SELECTORS[i]);
          for (var j = 0; j < nodes.length; j++) {
            var n = nodes[j];
            if ((n.offsetWidth || n.offsetHeight) && window.getComputedStyle(n).display !== 'none') {
              adLastSeenAt = Date.now();
              return true;
            }
          }
        }
        return adLastSeenAt > 0 && Date.now() - adLastSeenAt < AD_END_GRACE_MS;
      }

      // The main <video>, never the ad one (an ad creative may bring its own <video>).
      function findMainVideo() {
        var all = document.querySelectorAll('video');
        for (var i = 0; i < all.length; i++) {
          var v = all[i];
          if (v.closest && v.closest('.creative-player, [class*="vast"], [class*="adv_"]')) { continue; }
          return v;
        }
        return null;
      }

      function send(force) {
        var payload;
        var qualities = collectQualities().join(',');
        var current = currentQualityLabel();
        var ad = isAdActive() ? '1' : '0';
        if (!video) {
          payload = V + '|0|0|0|-|1|' + qualities + '|' + current + '|0|' + ad;
        } else {
          var d = video.duration;
          var dur = (typeof d === 'number' && isFinite(d) && d > 0) ? Math.round(d * 1000) : '-';
          var playing = (!video.paused && !video.ended) ? '1' : '0';
          var t = Math.round((video.currentTime || 0) * 1000);
          var r = video.playbackRate || 1;
          // HAVE_FUTURE_DATA = 3: below it a playing video is stalled waiting for data.
          var buffering = (!video.paused && !video.ended && video.readyState < 3) ? '1' : '0';
          payload = V + '|1|' + playing + '|' + t + '|' + dur + '|' + r + '|' + qualities + '|' + current + '|' +
                    buffering + '|' + ad;
        }
        if (!force && payload === lastPayload) { return; }
        lastPayload = payload;
        post(payload);
      }

      var EVENTS = ['play', 'playing', 'pause', 'ended', 'timeupdate', 'loadedmetadata',
                    'durationchange', 'ratechange', 'seeked', 'emptied', 'loadstart', 'canplay',
                    'waiting', 'stalled', 'seeking'];

      // Quality-switch state guard (Part B, default-video-quality). The host (Kodik/flowplayer)
      // switches quality by rebuilding the source; depending on the skin it may forget where we
      // were (restart from 0), reset playbackRate/volume, or resume a paused video. We snapshot
      // the state right before clicking the host's quality item and re-apply whatever the host
      // did NOT preserve once the new source has metadata (tolerances keep us from fighting a host
      // that restores the position itself). A snapshot lives at most QUALITY_RESTORE_TTL_MS so a
      // later unrelated reload (next episode) can never receive stale state.
      var QUALITY_RESTORE_TTL_MS = 10000;
      var QUALITY_RESTORE_TOLERANCE_S = 2;
      var qualityRestore = null;

      function captureQualityRestore() {
        if (!video) { return null; }
        return {
          t: video.currentTime || 0,
          paused: !!video.paused,
          rate: video.playbackRate || 1,
          vol: video.volume,
          muted: !!video.muted,
          reloading: false,
          until: Date.now() + QUALITY_RESTORE_TTL_MS
        };
      }

      function applyQualityRestore(final) {
        var r = qualityRestore;
        if (!r || !video) { return; }
        if (Date.now() > r.until) { qualityRestore = null; return; }
        var d = video.duration;
        if (!(typeof d === 'number' && isFinite(d) && d > 0)) { return; }
        try {
          if (r.t > 0 && Math.abs((video.currentTime || 0) - r.t) > QUALITY_RESTORE_TOLERANCE_S) {
            video.currentTime = r.t;
          }
          if (Math.abs((video.playbackRate || 1) - r.rate) > 0.01) { video.playbackRate = r.rate; }
          if (typeof r.vol === 'number' && Math.abs(video.volume - r.vol) > 0.01) { video.volume = r.vol; }
          if (video.muted !== r.muted) { video.muted = r.muted; }
          if (r.paused && !video.paused) {
            video.pause();
          } else if (!r.paused && video.paused) {
            var p = video.play();
            if (p && typeof p['catch'] === 'function') { p['catch'](function () {}); }
          }
        } catch (e) {}
        if (final) { qualityRestore = null; }
      }

      function onVideoEvent(type) {
        var r = qualityRestore;
        if (!r) { return; }
        if (type === 'emptied' || type === 'loadstart') { r.reloading = true; return; }
        if (!r.reloading) { return; }
        if (type === 'loadedmetadata') { applyQualityRestore(false); }
        else if (type === 'canplay' || type === 'playing') { applyQualityRestore(true); }
      }

      function attach(v) {
        if (!v || v === video) { return; }
        video = v;
        // Layer A: strip native HTML5 controls. play/pause/seek/rate work regardless of the
        // controls attribute, so the bridge keeps controlling the video.
        v.controls = false;
        v.removeAttribute('controls');
        for (var i = 0; i < EVENTS.length; i++) {
          v.addEventListener(EVENTS[i], function (e) { onVideoEvent(e && e.type); send(false); }, true);
        }
        // The host may have replaced the <video> element while switching quality: a new element
        // is by itself proof of a reload, and it may already have its metadata.
        if (qualityRestore) {
          qualityRestore.reloading = true;
          applyQualityRestore(false);
        }
        send(true);
      }

      function syncHostChromeState() {
        try {
          var root = document.documentElement;
          if (!root) { return; }
          if (video) { root.classList.add('aniko-video-found'); }
          else { root.classList.remove('aniko-video-found'); }
        } catch (e) {}
      }

      // Debug hook for live chrome-hide checks: set window.__anikoDebugChrome = true in the
      // page (e.g. via chrome://inspect console) and the bridge reports which visible nodes
      // still overlap <video> — Kotlin logs them (see parseEmbedChromeDebug). Off by default.
      var lastDebugPayload = '';

      function chromeDebugEnabled() {
        try { return !!window['__anikoDebugChrome']; } catch (e) { return false; }
      }

      function describeChromeNode(n) {
        var cls = String(n.className && n.className.baseVal !== undefined ? n.className.baseVal : n.className || '');
        cls = cls.split(/\s+/).join('.');
        var z = '';
        try {
          var zi = parseInt(window.getComputedStyle(n).zIndex, 10);
          if (isFinite(zi)) { z = '@' + zi; }
        } catch (e) {}
        return ((n.tagName || '?').toLowerCase()) + (n.id ? '#' + n.id : '') +
               (cls ? '.' + cls : '') + z;
      }

      function collectChromeOverVideo() {
        var out = [];
        if (!video) { return out; }
        var vr = video.getBoundingClientRect();
        if (!vr.width || !vr.height) { return out; }
        var nodes = document.querySelectorAll('body *');
        for (var i = 0; i < nodes.length; i++) {
          var n = nodes[i];
          // Ancestors of <video> only size its layout; leaks are siblings/overlays.
          if (n === video || n.contains(video)) { continue; }
          if (!(n.offsetWidth || n.offsetHeight || n.getClientRects().length)) { continue; }
          var r = n.getBoundingClientRect();
          if (r.left >= vr.right || r.right <= vr.left || r.top >= vr.bottom || r.bottom <= vr.top) { continue; }
          var d = describeChromeNode(n).replace(/[;|\n\r]/g, ' ');
          if (out.indexOf(d) < 0) { out.push(d); }
          if (out.length >= 40) { break; }
        }
        return out;
      }

      function sendChromeDebug() {
        var payload = V + '|$EMBED_BRIDGE_DEBUG_MARKER|' + collectChromeOverVideo().join(';');
        if (payload === lastDebugPayload) { return; }
        lastDebugPayload = payload;
        post(payload);
      }

      // Mid-roll guard (live 2026-10-02, iOS): after an ad break Kodik rebuilds the source and the
      // episode restarts from 0 although it was at 9:17 before the ad. Remember the last position
      // seen outside an ad and restore it once the ad is over and the video jumped back.
      var AD_RESTORE_MIN_S = 5;
      var lastMainTime = 0;
      var adWasActive = false;
      var adRestorePending = false;

      function trackAdBreak() {
        var ad = isAdActive();
        if (ad && !adWasActive) { adRestorePending = lastMainTime > AD_RESTORE_MIN_S; }
        adWasActive = ad;
        if (ad || !video) { return; }
        var t = video.currentTime || 0;
        var d = video.duration;
        if (adRestorePending && typeof d === 'number' && isFinite(d) && d > 0) {
          adRestorePending = false;
          if (t < lastMainTime - AD_RESTORE_MIN_S) {
            try { video.currentTime = lastMainTime; } catch (e) {}
            return;
          }
        }
        if (!video.paused && t > 0) { lastMainTime = t; }
      }

      function scan() {
        trackAdBreak();
        // Layer B: refresh per-host styles on every pass — the host may rebuild the DOM or
        // overwrite the style node, and the frame is already matched by domain, so re-asserting
        // textContent keeps the hide rules winning over the host's own styles.
        refreshHostChromeStyle();
        if (chromeDebugEnabled()) { sendChromeDebug(); }
        if (qualityRestore && Date.now() > qualityRestore.until) { qualityRestore = null; }
        if (video && !document.contains(video)) { video = null; }
        if (video) {
          // Layer A: periodically re-check whether the host's own JS restored controls.
          video.controls = false;
          video.removeAttribute('controls');
          return;
        }
        var v = findMainVideo();
        if (v) { attach(v); }
        syncHostChromeState();
      }

      function switchQuality(q) {
        function isVisible(e) {
          return !!(e && (e.offsetWidth || e.offsetHeight || e.getClientRects().length));
        }
        var item = null;
        var all = document.querySelectorAll('*');
        for (var i = 0; i < all.length; i++) {
          var n = all[i];
          if (n.children.length > 0) { continue; }
          var t = (n.innerText || n.textContent || '').trim();
          if (t === q) { item = n; break; }
        }
        if (!item) { return false; }
        if (isVisible(item)) {
          try { item.click(); } catch (e) {}
          return true;
        }
        // Item inside a hidden dropdown: click its container-opener first (classes
        // quality/fp-quality), then re-click the item once the menu is open.
        var menu = item;
        while (menu && menu !== document.body) {
          var cls = String(menu.className && menu.className.baseVal !== undefined ? menu.className.baseVal : menu.className || '');
          if (/quality|fp-|dropdown/i.test(cls) && !isVisible(menu)) { break; }
          menu = menu.parentElement;
        }
        var opener = menu || item;
        try { opener.click(); } catch (e) {}
        setTimeout(function () {
          var items = document.querySelectorAll('*');
          for (var j = 0; j < items.length; j++) {
            var m = items[j];
            if (m.children.length > 0) { continue; }
            if ((m.innerText || m.textContent || '').trim() === q) { try { m.click(); } catch (e2) {} break; }
          }
        }, 450);
        return true;
      }

      // Host start (P16 2026-09-10, reworked 2026-10-01): before the host creates <video> our
      // own play must start it. Kodik ignores synthetic clicks (isTrusted check) but honours its
      // official postMessage API ({key:'kodik_player_api', value:{method:'play'}}) — verified
      // live: the first message boots flowplayer, a later one starts playback without any user
      // gesture. The message is posted to the frame's OWN window (the API listener does not
      // check the sender) and only once the page is ready: an early message (vInfo not yet
      // defined) made Kodik's loader set its "already clicked" flag and then crash with
      // "vInfo is not defined", never starting again (live 2026-10-01). Synthetic clicks stay
      // as a fallback for other hosts, under the same readiness gate.
      var HOST_START_SELECTORS = [
        '.fp-play',
        '.fp-ui .fp-play',
        '.play_button',
        '.play_background',
        '[class*="big-play"]',
        '[class*="play-button"]',
        '.is-splash',
        '.fp-splash',
        '.fp-player',
        '.fp-ui',
        '.player_box',
        '.main-player'
      ];

      function synthClick(el) {
        try { el.click(); } catch (e) {}
        try {
          var ev = new MouseEvent('click', { bubbles: true, cancelable: true, view: window });
          el.dispatchEvent(ev);
        } catch (e) {}
      }

      function kodikApiPlay() {
        try { window.postMessage({ key: 'kodik_player_api', value: { method: 'play' } }, '*'); } catch (e) {}
      }

      function isKodikFrame() {
        for (var i = 0; i < KODIK_HOST_SUFFIXES.length; i++) {
          if (hostMatches(location.host, KODIK_HOST_SUFFIXES[i])) { return true; }
        }
        return false;
      }

      // The host page must have finished parsing (and, for Kodik, defined its vInfo) before any
      // start attempt — see the comment above about the early-message crash.
      function hostReadyToStart() {
        if (document.readyState === 'loading') { return false; }
        if (isKodikFrame() && typeof window['vInfo'] === 'undefined') { return false; }
        return true;
      }

      var hostStartTimer = null;

      function startHostPlayer() {
        if (hostStartTimer) { return; }
        var attempt = 0;
        var ticks = 0;
        hostStartTimer = setInterval(function () {
          scan();
          // Hard cap (~30 s) for a page that never becomes ready (e.g. the Kodik wrapper frame).
          if (++ticks > 45) { clearInterval(hostStartTimer); hostStartTimer = null; return; }
          // Never poke the player during an ad break: a play/click could break or re-trigger it.
          if (isAdActive()) { return; }
          if (!video && !hostReadyToStart()) { return; }
          attempt++;
          if (video && !video.paused) { clearInterval(hostStartTimer); hostStartTimer = null; return; }
          if (video) {
            var p = playVideo();
            if (p && typeof p['catch'] === 'function') { p['catch'](function () {}); }
          }
          kodikApiPlay();
          if (attempt <= 3) {
            for (var i = 0; i < HOST_START_SELECTORS.length; i++) {
              var nodes = document.querySelectorAll(HOST_START_SELECTORS[i]);
              for (var j = 0; j < nodes.length; j++) { synthClick(nodes[j]); }
            }
          }
          if (attempt >= 15) { clearInterval(hostStartTimer); hostStartTimer = null; }
        }, 700);
      }

      function playVideo() {
        try { return video.play(); } catch (e) { return null; }
      }

      function exec(cmd) {
        if (typeof cmd !== 'string') { return; }
        // An explicit pause wins over a pending start loop (otherwise it would resume playback).
        if (cmd === 'pause' && hostStartTimer) { clearInterval(hostStartTimer); hostStartTimer = null; }
        scan();
        if (!video) {
          if (cmd === 'play') { startHostPlayer(); }
          return;
        }
        try {
          if (cmd === 'play') {
            var p = playVideo();
            // play() promise rejects (AbortError) even though playback actually started —
            // swallow it to avoid an unhandled rejection; state still arrives via events.
            if (p && typeof p['catch'] === 'function') { p['catch'](function () {}); }
          } else if (cmd === 'pause') {
            video.pause();
          } else if (cmd.indexOf('seek:') === 0) {
            video.currentTime = Math.max(0, parseFloat(cmd.slice(5)) / 1000);
          } else if (cmd.indexOf('seekBy:') === 0) {
            video.currentTime = Math.max(0, (video.currentTime || 0) + parseFloat(cmd.slice(7)) / 1000);
          } else if (cmd.indexOf('rate:') === 0) {
            var rate = parseFloat(cmd.slice(5));
            if (isFinite(rate) && rate > 0) { video.playbackRate = rate; }
          } else if (cmd.indexOf('quality:') === 0) {
            // P16 fix 2026-09-09: client-side host quality switch (Kodik/flowplayer
            // quality-dropdown). Safe no-op when no menu exists; the player rebuilds the
            // source, the bridge keeps tracking <video> via scan().
            // Snapshot BEFORE the click; dropped at once if the host has no such menu item, so a
            // no-op switch can never leave a stale snapshot behind (see qualityRestore above).
            qualityRestore = captureQualityRestore();
            if (!switchQuality(cmd.slice(8))) { qualityRestore = null; }
          }
        } catch (e) {}
        send(true);
      }

      // iOS: commands arrive via evaluateJavaScript(inFrame:) straight into this function.
      window.$EMBED_BRIDGE_EXEC_FN = exec;

      // Android: reverse channel via WebMessageListener. Injection order of our script vs.
      // the channel object isn't guaranteed, so we re-wire on a timer too.
      function wire() {
        try {
          var obj = window[CH];
          if (obj && !obj.__anikoWired) {
            obj.__anikoWired = true;
            obj.onmessage = function (event) { exec(event && event.data); };
          }
        } catch (e) {}
      }

      // Layer A: inject native webkit-control hiding at document-start.
      injectGlobalChromeHide();

      wire();
      scan();
      setInterval(function () { wire(); scan(); send(false); }, 500);
      if (document.addEventListener) {
        document.addEventListener('DOMContentLoaded', function () { wire(); scan(); }, false);
      }
    })();
    """.trimIndent()
