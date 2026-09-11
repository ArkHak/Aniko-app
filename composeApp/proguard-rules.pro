# P12.T2 (docs/REELWAVE_PLAN.md): R8/ProGuard-правила для релизной сборки Android.
#
# Большинство зависимостей (Compose runtime/resources, Ktor, SQLDelight, kotlinx.serialization,
# Koin) несут собственные consumer-rules.pro внутри AAR — R8 подхватывает их автоматически.
# Ниже — только то, что реально потребовалось после живого прогона подписанной release-сборки
# на эмуляторе (см. отчёт задачи в docs/REELWAVE_PLAN.md за 2026-09-11). Не добавлять правила
# "на всякий случай" без воспроизведённого краша — неоправданный keep увеличивает APK и
# маскирует реальные проблемы кода мёртвым весом конфигурации.

# kotlinx.serialization: DTO читаются по значению `@SerialName` — R8 обязан сохранить runtime-
# видимые аннотации, иначе кастомные имена полей теряются при (де)сериализации.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature

# androidx.security-crypto (EncryptedSharedPreferences, LocalAuthStore) тянет Google Tink,
# который ссылается на compile-time-only аннотации errorprone (`@CanIgnoreReturnValue`,
# `@Immutable`, ...) — они не нужны в рантайме, R8 просто не находит их jar (errorprone-
# annotations не входит в APK-зависимости). Известная и документированная особенность связки
# androidx.security-crypto + R8, не специфична для этого проекта.
-dontwarn com.google.errorprone.annotations.**
