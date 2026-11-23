package com.manima.foodlens.core.data

/** Provided by :app from BuildConfig so core modules never read BuildConfig directly. */
data class AppConfig(
    /** Origin of the FastAPI backend, e.g. `http://10.0.2.2:8000` (SPEC §16). */
    val apiBaseUrl: String,
    val versionCode: Int,
    val versionName: String,
    /** Enables the in-app API URL override for testing on a physical phone. */
    val isDebug: Boolean
)

fun interface Clock {
    fun now(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}
