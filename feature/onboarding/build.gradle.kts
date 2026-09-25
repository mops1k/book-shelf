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
    testImplementation(testFixtures(project(":core:data")))
    testImplementation(project(":core:testing"))
}
