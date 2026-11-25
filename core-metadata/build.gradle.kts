plugins {
    alias(mihonx.plugins.android.library)

    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "tachiyomi.core.metadata"
}

dependencies {
    implementation(projects.sourceApi)

    implementation(libs.bundles.serialization)
}
