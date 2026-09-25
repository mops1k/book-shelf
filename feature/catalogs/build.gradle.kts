plugins {
    id("bookshelf.android.feature")
}

android {
    namespace = "io.github.bookshelf.feature.catalogs"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:opds"))
    testImplementation(testFixtures(project(":core:data")))
    testImplementation(project(":core:testing"))
}
