package com.amanoteam.kad

object Kad {
    init {
        System.loadLibrary("kad-jni")
    }

    @JvmStatic
    external fun kadMain(arguments: Array<String>): Int
}
