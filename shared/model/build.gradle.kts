plugins {
    id("aniko.kmp.library")
    id("aniko.lint")
}

android {
    namespace = "com.aniko.model"
}

// :shared:model намеренно без зависимостей кроме stdlib — это чистые domain-модели.
