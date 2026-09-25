package com.aniko.app.update

import java.io.File
import java.util.concurrent.TimeUnit

/** Результат синхронной команды: код выхода и объединённый вывод (stdout + stderr). */
internal data class CommandResult(
    val exitCode: Int,
    val output: String,
)

/** Запуск внешних процессов — вынесен за интерфейс, чтобы установщик тестировался без реального `hdiutil`. */
internal interface ProcessRunner {
    /** Синхронно запускает [command] и ждёт завершения (до [TIMEOUT_SECONDS] секунд). */
    fun run(command: List<String>): CommandResult

    /** Запускает [command] так, чтобы процесс пережил завершение JVM; вывод дописывается в [log]. */
    fun startDetached(
        command: List<String>,
        log: File,
    )

    companion object {
        const val TIMEOUT_SECONDS = 120L
    }
}

/** Настоящие процессы ОС. */
internal object SystemProcessRunner : ProcessRunner {
    override fun run(command: List<String>): CommandResult {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val finished = process.waitFor(ProcessRunner.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        return CommandResult(if (finished) process.exitValue() else TIMEOUT_EXIT_CODE, output)
    }

    override fun startDetached(
        command: List<String>,
        log: File,
    ) {
        ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
            .start()
    }

    private const val TIMEOUT_EXIT_CODE = -1
}
