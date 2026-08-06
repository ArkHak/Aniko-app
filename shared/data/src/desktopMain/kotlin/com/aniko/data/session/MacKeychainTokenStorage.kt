package com.aniko.data.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Secure-хранилище токена на Desktop (macOS-only — единственный desktop-таргет проекта):
 * системная утилита `security`, сервис `com.aniko.token`, аккаунт `anix`.
 *
 * Почему CLI, а не нативный JVM API: у JVM на macOS нет прямого, стабильного между вендорами
 * (Temurin/Zulu/Corretto) доступа к Keychain — исторический провайдер `Apple` с алиасом
 * `KeychainStore` умеет только сертификаты (`KeyStore.getInstance("KeychainStore")`), не
 * произвольные пароли/секреты, и давно не гарантирован в современных OpenJDK-сборках.
 * Единственный по-настоящему надёжный способ добраться до Keychain с JVM — та же утилита
 * `security`, которой пользуется сама macOS. Для personal single-user приложения (это не
 * серверный сценарий с параллельными процессами и не требует нативного JCA `Provider`)
 * шелл-аут — осознанный прагматичный выбор: минимум кода, максимум совместимости, и это тот
 * же самый бэкенд, что и `Keychain Access.app`.
 *
 * Секрет никогда не передаётся через argv (`security ... -w <secret>`), потому что аргументы
 * процесса видны всем пользователям системы через `ps`/`/proc`. Запись идёт через
 * интерактивный режим `security -i`, где команда с секретом уходит в stdin процесса, а не
 * в argv.
 */
class MacKeychainTokenStorage : SecureTokenStorage {

    override suspend fun get(): String? = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(
            "security", "find-generic-password",
            "-s", SERVICE,
            "-a", ACCOUNT,
            "-w",
        ).start()

        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        when (exitCode) {
            0 -> stdout.trim().ifEmpty { null }
            ERR_ITEM_NOT_FOUND -> null
            else -> error("security find-generic-password завершился с кодом $exitCode: $stderr")
        }
    }

    override suspend fun set(token: String): Unit = withContext(Dispatchers.IO) {
        // Экранируем `\` и `"`: команда для `security -i` парсится как shell-подобная строка,
        // токен передаётся в кавычках, чтобы пробелы/спецсимволы (если появятся) не разбили её.
        val escapedToken = token.replace("\\", "\\\\").replace("\"", "\\\"")
        runSecurityInteractive(
            """add-generic-password -U -a $ACCOUNT -s $SERVICE -w "$escapedToken"""",
        )
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(
            "security", "delete-generic-password",
            "-s", SERVICE,
            "-a", ACCOUNT,
        ).start()

        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        check(exitCode == 0 || exitCode == ERR_ITEM_NOT_FOUND) {
            "security delete-generic-password завершился с кодом $exitCode: $stderr"
        }
    }

    /** Пишет одну команду `security` в stdin интерактивной сессии — секрет не попадает в argv. */
    private fun runSecurityInteractive(command: String) {
        val process = ProcessBuilder("security", "-i")
            .redirectErrorStream(true)
            .start()

        process.outputStream.bufferedWriter().use { writer ->
            writer.write(command)
            writer.newLine()
            writer.write("quit")
            writer.newLine()
            writer.flush()
        }

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        check(exitCode == 0) { "security -i завершился с кодом $exitCode: $output" }
    }

    private companion object {
        const val SERVICE = "com.aniko.token"
        const val ACCOUNT = "anix"

        /** Код возврата `security`, если элемент не найден в Keychain — это не ошибка. */
        const val ERR_ITEM_NOT_FOUND = 44
    }
}
