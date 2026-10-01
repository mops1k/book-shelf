plugins {
    id("bookshelf.android.feature")
}

android {
    namespace = "io.github.bookshelf.feature.collections"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    // Полка съёмного носителя приходит из `:core:data` вместе с типом `RemovableVolume`, а
    // `:core:data` держит `:core:storage` закрытым (implementation): объявляем явно, вместо того
    // чтобы открывать SAF-внутренности всем потребителям `:core:data`.
    implementation(project(":core:storage"))
    testImplementation(project(":core:testing"))
}
