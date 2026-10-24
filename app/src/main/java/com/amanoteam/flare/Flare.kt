package com.amanoteam.flare

object Flare {
    init {
        System.loadLibrary("flare")
    }

    @JvmStatic
    external fun flareMain(arguments: Array<String>): Int

    /**
     * Finds the content bands of the image at [path], cropping nothing.
     *
     * @return IntArray of [left, top, width, height, bandCount, bandTop, bandHeight, ...], one
     * top/height pair per band of content, or null when the image cannot be decoded.
     */
    @JvmStatic
    external fun findBands(path: String): IntArray?
}
