plugins {
    id("bookshelf.android.feature")
}

android {
    namespace = "io.github.bookshelf.feature.onboarding"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:storage"))
    // Системный пикер папок ACTION_OPEN_DOCUMENT_TREE. Нужен только этому модулю:
    // выбрать папку SAF умеет лишь онбординг.
    implementation(libs.androidx.activity.compose)
    testImplementation(project(":core:testing"))
}
