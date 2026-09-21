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
 * `v1|<найдено 0/1>|<играет 0/1>|<позиция мс>|<длительность мс или `-`>|<скорость>[|<качества csv>|<текущее>]`.
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
    )
}

private const val EMBED_BRIDGE_FIELDS = 6

/** Индекс опционального поля «качества через запятую» в payload моста (после базовых шести). */
private const val EMBED_BRIDGE_QUALITIES_FIELD = 6

/** Индекс опционального поля «текущее качество» в payload моста. */
private const val EMBED_BRIDGE_CURRENT_QUALITY_FIELD = 7

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
 *   фрейме, чей `location.host` совпал с суффиксом по границе метки. Таблица пока пуста:
 *   селекторы кастомных плееров (Kodik, Sibnet, AniLibria) получаем из живых DOM-дампов,
 *   а не выдумываем. Добавление правила — одна строка в [CHROME_HIDE_CSS].
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
       * - kodikplayer.com: player mounts into div.player_box; the player page did not initialize
       *   during the dump session (host-side failure) so its internal chrome selectors are still
       *   unknown — rule stays pending a working session.
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
        {
          suffix: 'kodikplayer.com',
          css: [
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
            // Kodik big play/poster stay visible until first start (trust gesture: the player
            // ignores synthetic clicks) and get hidden by the class the bridge puts on <html>
            // once <video> is found (see syncHostChromeState).
            'html.aniko-video-found .play_button',
            'html.aniko-video-found .play_background',
            'html.aniko-video-found .fp-play'
          ].join(', ') + ' { display:none !important; }',
        },
      ];

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

      function send(force) {
        var payload;
        var qualities = collectQualities().join(',');
        var current = currentQualityLabel();
        if (!video) {
          payload = V + '|0|0|0|-|1|' + qualities + '|' + current;
        } else {
          var d = video.duration;
          var dur = (typeof d === 'number' && isFinite(d) && d > 0) ? Math.round(d * 1000) : '-';
          var playing = (!video.paused && !video.ended) ? '1' : '0';
          var t = Math.round((video.currentTime || 0) * 1000);
          var r = video.playbackRate || 1;
          payload = V + '|1|' + playing + '|' + t + '|' + dur + '|' + r + '|' + qualities + '|' + current;
        }
        if (!force && payload === lastPayload) { return; }
        lastPayload = payload;
        post(payload);
      }

      var EVENTS = ['play', 'playing', 'pause', 'ended', 'timeupdate', 'loadedmetadata',
                    'durationchange', 'ratechange', 'seeked', 'emptied', 'loadstart', 'canplay'];

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

      function scan() {
        // Layer B: refresh per-host styles on every pass — the host may rebuild the DOM,
        // and the frame already matched by domain, so the injection is safe.
        refreshHostChromeStyle();
        if (qualityRestore && Date.now() > qualityRestore.until) { qualityRestore = null; }
        if (video && !document.contains(video)) { video = null; }
        if (video) {
          // Layer A: periodically re-check whether the host's own JS restored controls.
          video.controls = false;
          video.removeAttribute('controls');
          return;
        }
        var v = document.querySelector('video');
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

      // Host start fallback (P16 2026-09-10): we hide the WHOLE host HUD, including its big play,
      // so when the host has not created the <video> yet its own controls must be pressed
      // programmatically. Synthetic click works on display:none elements, so the chrome can stay
      // hidden while our overlay stays the only visible UI.
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

      function startHostPlayer() {
        var attempt = 0;
        var timer = setInterval(function () {
          attempt++;
          for (var i = 0; i < HOST_START_SELECTORS.length; i++) {
            var nodes = document.querySelectorAll(HOST_START_SELECTORS[i]);
            for (var j = 0; j < nodes.length; j++) { synthClick(nodes[j]); }
          }
          scan();
          if (video || attempt >= 4) { clearInterval(timer); }
        }, 400);
      }

      function playVideo() {
        try { return video.play(); } catch (e) { return null; }
      }

      function exec(cmd) {
        if (typeof cmd !== 'string') { return; }
        scan();
        if (!video) {
          if (cmd === 'play') { startHostPlayer(); scan(); video && playVideo(); }
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
