package com.aniko.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * Регистрирует кастомный ruleset "aniko" (P2.T10). Обнаруживается detekt через
 * `ServiceLoader` — см. `META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider`.
 */
class AnikoRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "aniko"

    override fun instance(config: Config): RuleSet =
        RuleSet(ruleSetId, listOf(ForbiddenCyrillicStringLiteral(config)))
}
