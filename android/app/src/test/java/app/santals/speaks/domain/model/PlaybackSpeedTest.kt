package app.santals.speaks.domain.model

import org.junit.Test
import kotlin.test.assertEquals

class PlaybackSpeedTest {

    @Test
    fun offersTheSpecifiedSpeeds() {
        assertEquals(listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f), PlaybackSpeed.entries.map { it.factor })
    }

    @Test
    fun mapsFactorsBackAndFallsBackToNormal() {
        assertEquals(PlaybackSpeed.FAST_150, PlaybackSpeed.fromFactor(1.5f))
        assertEquals(PlaybackSpeed.NORMAL, PlaybackSpeed.fromFactor(1.33f))
    }
}
