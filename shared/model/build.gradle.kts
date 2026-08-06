plugins {
    id("aniko.kmp.library")
}

android {
    namespace = "com.aniko.model"
}

// :shared:model намеренно без зависимостей кроме stdlib — это чистые domain-модели.
