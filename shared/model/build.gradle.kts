plugins {
    id("anix.kmp.library")
}

android {
    namespace = "com.anixkmp.model"
}

// :shared:model намеренно без зависимостей кроме stdlib — это чистые domain-модели.
