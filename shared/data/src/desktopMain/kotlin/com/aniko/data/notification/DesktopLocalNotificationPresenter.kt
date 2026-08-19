package com.aniko.data.notification

import java.awt.AWTException
import java.awt.Color
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

/**
 * Desktop-реализация [LocalNotificationPresenter] — **это и есть P10.T5**.
 *
 * `java.awt.SystemTray` + [TrayIcon.displayMessage] — единственный способ показать системное
 * уведомление из JVM-приложения без нативных зависимостей: он работает на всех трёх десктопных ОС
 * (на macOS и Windows отображается настоящим системным баннером, на Linux — средствами DE).
 * Альтернативы отвергнуты: JNA/JNI-биндинги к `NSUserNotificationCenter`/`WinRT` — новая нативная
 * зависимость ради одной функции; вызов внешнего `osascript`/`notify-send` — запуск стороннего
 * процесса из приложения, хрупко и не переносимо.
 *
 * **Деградация вместо падения.** `SystemTray.isSupported()` возвращает `false` на headless-JVM и
 * в окружениях без трея; `SystemTray.add` бросает [AWTException], если трей есть, но занят или
 * недоступен. Оба случая обрабатываются одинаково — [ensurePermission] возвращает `false`, поллер
 * молча пропускает показ. Уведомления — не критичная функциональность, ронять из-за них приложение
 * недопустимо.
 *
 * **Иконка рисуется в памяти**, а не грузится из ресурсов: [TrayIcon] требует непустой `Image`, но
 * в трее эта иконка никогда не окажется видимой сама по себе — она нужна как «отправитель»
 * баннера. Тащить ради этого файл в ресурсы модуля данных незачем.
 *
 * **Один [TrayIcon] на всё время жизни процесса.** Добавление/удаление иконки на каждое
 * уведомление приводит к мигающему трею и на части систем — к потере баннера, поэтому
 * инициализация ленивая и однократная, а сама иконка так и остаётся в трее.
 *
 * **Клик по уведомлению никуда не ведёт** (в отличие от Android). `TrayIcon.addActionListener`
 * срабатывает по клику на самой иконке трея, а поведение клика по баннеру не специфицировано и
 * различается между ОС — построить на этом надёжный переход к тайтлу нельзя. Deep link из
 * [LocalNotification.deepLink] здесь просто игнорируется.
 */
class DesktopLocalNotificationPresenter : LocalNotificationPresenter {
    private var trayIcon: TrayIcon? = null
    private var initializationFailed = false

    override suspend fun ensurePermission(): Boolean = trayIconOrNull() != null

    override suspend fun show(notification: LocalNotification) {
        trayIconOrNull()?.displayMessage(
            notification.title,
            notification.body,
            TrayIcon.MessageType.INFO,
        )
    }

    @Suppress("SwallowedException")
    @Synchronized
    private fun trayIconOrNull(): TrayIcon? {
        trayIcon?.let { return it }
        val unavailable = initializationFailed || !SystemTray.isSupported()

        return if (unavailable) {
            null
        } else {
            try {
                val icon =
                    TrayIcon(renderIcon(), TRAY_TOOLTIP).apply {
                        isImageAutoSize = true
                    }
                SystemTray.getSystemTray().add(icon)
                trayIcon = icon
                icon
            } catch (e: AWTException) {
                // Трей заявлен поддерживаемым, но добавить иконку не дал. Запоминаем отказ, чтобы
                // не повторять попытку на каждом тике синхронизации до конца жизни процесса.
                initializationFailed = true
                null
            }
        }
    }

    /**
     * Простой круг в фирменном `primary` дизайн-системы (`#8B6FF0`). Цвет захардкожен числом, а не
     * взят из токенов `shared/ui`: `shared/data` от него не зависит (и не должен — это Compose),
     * а тащить зависимость ради одного значения в невидимой иконке несоразмерно.
     */
    private fun renderIcon(): BufferedImage {
        val image = BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.color = Color(BRAND_PRIMARY_RGB)
            graphics.fillOval(0, 0, ICON_SIZE, ICON_SIZE)
        } finally {
            graphics.dispose()
        }
        return image
    }

    private companion object {
        const val ICON_SIZE = 16
        const val BRAND_PRIMARY_RGB = 0x8B6FF0
        const val TRAY_TOOLTIP = "Aniko"
    }
}
