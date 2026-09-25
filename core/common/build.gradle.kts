plugins {
    id("bookshelf.android.library")
    id("bookshelf.android.hilt")
}

android {
    namespace = "io.github.bookshelf.core.common"
}

dependencies {
    // coroutines отдаём наружу через api: Flow/StateFlow есть в публичных подписях
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    // @Qualifier для Hilt-аннотаций и @IntDef/@Keep
    api(libs.javax.inject)
    api(libs.androidx.annotation)
}
