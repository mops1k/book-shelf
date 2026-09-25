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
    testImplementation(testFixtures(project(":core:data")))
    testImplementation(project(":core:testing"))
}
