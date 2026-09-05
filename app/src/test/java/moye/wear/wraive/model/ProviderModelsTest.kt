package moye.wear.wraive.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderModelsTest {
    @Test
    fun normalizeModelCapabilities_convertsReleaseDeserializedStrings() {
        val releaseValues = setOf("TEXT", "VISION", "UNKNOWN")

        assertEquals(
            setOf(ModelCapability.TEXT, ModelCapability.VISION),
            normalizeModelCapabilities(releaseValues)
        )
    }
}
