import com.aniko.buildlogic.libsCatalog

/**
 * SQLDelight (P4.S0) для модулей, генерирующих типобезопасный SQL-доступ из `.sq`-схем.
 *
 * Единственная база на весь проект — `AnikoDatabase` в `:shared:database`, `.sq`-файлы лежат в
 * `src/commonMain/sqldelight` (общая схема для всех таргетов, платформенные драйверы
 * подключаются отдельно через `expect`/`actual` в самом модуле).
 *
 * `verifyMigrations = true` — миграции (`.sqm`) сверяются со схемой на этапе сборки, чтобы
 * несовпадение между `.sq` и накопленными `.sqm` ловилось в CI, а не в рантайме на устройстве.
 * `generateAsync = false` — проект использует блокирующий JDBC/SQLite-драйвер на всех таргетах
 * (Android/iOS/Desktop), suspend-обёртка живёт на уровне сторов в `:shared:database`, а не
 * генерируется самим SQLDelight.
 *
 * `dialect(sqlite-3-38)`: дефолтный диалект SQLDelight без явного `dialect(...)` — `sqlite:3.18`,
 * который не знает `ON CONFLICT ... DO UPDATE SET` (upsert появился в SQLite 3.24). Кэш-схема
 * (LWW-апсерты в listMembership/episodeProgress, коалесcинг в syncOperation, protective upsert в
 * releaseEntity) сплошь на upsert'ах — без более нового диалекта
 * `generateCommonMainAnikoDatabaseInterface` падает с синтаксической ошибкой на каждом
 * `ON CONFLICT`. Целевые платформенные драйверы (`sqlite-driver`/JDBC на Desktop и в unit-тестах,
 * Android SQLite — начиная с API 24, `android-driver` уже использует bundled SQLite ≥ 3.28, iOS
 * `native-driver` — системный libsqlite3 ≥ 3.32 на актуальных iOS) все умеют upsert, так что
 * поднятие диалекта здесь безопасно и не сужает поддерживаемые таргеты проекта.
 *
 * Сгенерированный accessor `libs` внутри precompiled script plugin недоступен (известное
 * ограничение Gradle) — используем `libsCatalog` из [com.aniko.buildlogic] вместо него.
 */
plugins {
    id("app.cash.sqldelight")
}

sqldelight {
    databases {
        create("AnikoDatabase") {
            packageName.set("com.aniko.database")
            srcDirs.setFrom("src/commonMain/sqldelight")
            verifyMigrations.set(true)
            generateAsync.set(false)
            dialect(libsCatalog.findLibrary("sqldelight-dialect-sqlite338").get())
        }
    }
}
