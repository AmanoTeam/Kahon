package com.amanoteam.kad

import android.content.Context
import java.io.File

object Kad {
    init {
        System.loadLibrary("kad")
    }

    @JvmStatic
    external fun kadMain(arguments: Array<String>): Int

    fun caBundle(context: Context): File {
        val file = File(context.filesDir, "cert.pem")
        if (!file.exists()) {
            context.assets.open("cert.pem").use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return file
    }
}
