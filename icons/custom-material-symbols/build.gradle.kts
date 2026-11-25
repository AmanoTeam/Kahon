plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.compose)

    alias(libs.plugins.valkyrie)
}

android {
    namespace = "mihon.icons.custommaterialsymbols"
}

valkyrie {
    packageName = "mihon.icons.custommaterialsymbols"
    generateAtSync = true

    imageVector {
        suppressUnusedReceiverWarning = true
    }

    iconPack {
        name = "CustomMaterialSymbols"
        targetSourceSet = "main"

        nested {
            name = "Rounded"
            sourceFolder = "rounded"
            autoMirror = false
        }
    }
}

dependencies {
    api(libs.androidx.compose.ui)
}
