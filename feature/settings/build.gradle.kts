plugins {
    id("bookshelf.android.feature")
}

android {
    namespace = "io.github.bookshelf.feature.settings"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))
    implementation(project(":core:storage"))
    implementation(project(":core:readerlink"))
    implementation(project(":core:eink"))
    implementation(project(":core:opds"))
    testImplementation(testFixtures(project(":core:data")))
    testImplementation(project(":core:testing"))
}
