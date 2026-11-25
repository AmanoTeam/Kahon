plugins {
    alias(mihonx.plugins.android.library)

    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "mihon.core.archive"
}

dependencies {
    implementation(libs.jsoup)
    implementation(libs.archive)
    implementation(libs.unifile)
}
