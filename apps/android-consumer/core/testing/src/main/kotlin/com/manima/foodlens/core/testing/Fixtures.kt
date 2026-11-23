package com.manima.foodlens.core.testing

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Loads the shared, language-neutral test vectors in `packages/fixtures/` (SPEC §4).
 * The directory is passed by the build as the `foodlens.fixtures.dir` system property.
 */
object Fixtures {
    val json = Json { ignoreUnknownKeys = true }

    fun dir(): File {
        val path = System.getProperty(PROPERTY)
            ?: error("System property '$PROPERTY' is not set; run tests through Gradle.")
        return File(path).also { require(it.isDirectory) { "Fixtures dir not found: $it" } }
    }

    fun read(name: String): String = dir().resolve(name).readText()

    fun element(name: String): JsonElement = json.parseToJsonElement(read(name))

    private const val PROPERTY = "foodlens.fixtures.dir"
}
