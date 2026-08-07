package com.aniko.detekt.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * P2.T10 (`docs/REELWAVE_PLAN.md`, Фаза 2): запрет захардкоженных кириллических строковых
 * литералов вне i18n-слоя — чтобы не повторить ситуацию, для исправления которой понадобились
 * P2.T7–P2.T9 (105 хардкод-вхождений русского текста, размазанных по 15 экранным файлам).
 *
 * Правило намеренно НЕ пытается отличить именно Composable UI-контекст от прочего кода —
 * детектить "это Composable" через detekt/PSI сложно и хрупко (нет типовой информации на этом
 * уровне анализа, только синтаксис). Вместо этого — простой и надёжный сигнал: строковый литерал,
 * содержащий кириллицу, в основных source set'ах (`commonMain`/`androidMain`/`desktopMain`/
 * `iosMain` — на них завязаны detekt-таски потребляющих модулей). Тестовые source set'ы
 * (`test`/`commonTest`/`androidTest`/`desktopTest`/`iosTest`) и легитимный i18n-слой (папка
 * `i18n` внутри `shared/ui`, где кириллица — это и есть содержимое переводов) исключены через
 * `excludes` в `config/detekt/detekt.yml`, не в самом правиле — так исключения видны и
 * редактируются в одном месте с остальной конфигурацией detekt. (Осторожно: не пишите в
 * doc-комментариях "слэш-звёздочка-звёздочка" подряд — Kotlin поддерживает вложенные block-
 * комментарии, такая последовательность закрывает его раньше времени.)
 *
 * Литерал проверяется по кириллице ПОСЛЕ отбрасывания `${...}`-подстановок: `"Эпизод $number"`
 * ловится (литеральная часть "Эпизод " кириллическая), а `"$prefix: Episode"` — нет, если сама
 * литеральная часть латиница (интерполированное значение не проверяем — его содержимое неизвестно
 * статически).
 */
class ForbiddenCyrillicStringLiteral(config: Config) : Rule(config) {

    override val issue =
        Issue(
            id = javaClass.simpleName,
            severity = Severity.Style,
            description =
                "Кириллический строковый литерал вне i18n-слоя — используйте Strings/Lyricist " +
                    "(shared/ui/.../i18n) вместо хардкода.",
            debt = Debt.TEN_MINS,
        )

    override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
        super.visitStringTemplateExpression(expression)

        val literalText =
            expression.entries
                .filterIsInstance<KtLiteralStringTemplateEntry>()
                .joinToString(separator = "") { it.text }

        if (CYRILLIC_REGEX.containsMatchIn(literalText)) {
            report(CodeSmell(issue, Entity.from(expression), issue.description))
        }
    }

    private companion object {
        val CYRILLIC_REGEX = Regex("[а-яА-ЯёЁ]")
    }
}
