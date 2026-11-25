plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.compose)

    alias(libs.plugins.valkyrie)
}

android {
    namespace = "mihon.icons.simpleicons"
}

valkyrie {
    packageName = "mihon.icons.simpleicons"
    generateAtSync = true

    imageVector {
        suppressUnusedReceiverWarning = true
    }

    iconPack {
        name = "SimpleIcons"
        targetSourceSet = "main"
    }
}

dependencies {
    api(libs.androidx.compose.ui)
}
