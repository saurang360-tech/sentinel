package dev.titus.sentinel.monitor

import org.junit.Assert.*
import org.junit.Test

class SustainedHealthTest {
    private fun ram(value: Double?)=listOf(HealthReading("ram","RAM",value,10.0,15.0,true))
    @Test fun spikeDoesNotTriggerAndHysteresisPreventsChatter() {
        val engine=SustainedHealth()
        assertTrue(engine.observe(ram(9.0),0).isEmpty())
        assertTrue(engine.observe(ram(12.0),30_000).isEmpty())
        assertTrue(engine.observe(ram(9.0),60_000).isEmpty())
        assertTrue(engine.observe(ram(9.0),90_000).isEmpty())
        assertFalse(engine.observe(ram(9.0),120_000).single().recovered)
        assertTrue(engine.observe(ram(12.0),150_000).isEmpty())
        assertTrue(engine.observe(ram(16.0),180_000).isEmpty())
        assertTrue(engine.observe(ram(16.0),210_000).isEmpty())
        assertTrue(engine.observe(ram(16.0),240_000).single().recovered)
        assertTrue(engine.observe(ram(16.0),270_000).isEmpty())
    }
    @Test fun missingSensorOrConnectionGapDoesNotCountTowardDwell() {
        val engine=SustainedHealth()
        engine.observe(ram(9.0),0)
        engine.observe(ram(null),30_000)
        assertTrue(engine.observe(ram(9.0),60_000).isEmpty())
        assertTrue(engine.observe(ram(9.0),90_000).isEmpty())
        assertEquals(1,engine.observe(ram(9.0),120_000).size)
        engine.observe(ram(16.0),150_000)
        engine.interrupted()
        assertTrue(engine.observe(ram(16.0),300_000).isEmpty())
        engine.observe(ram(16.0),330_000)
        assertTrue(engine.observe(ram(16.0),360_000).single().recovered)
    }
    @Test fun sampleGapAndDisappearingVolumeDoNotConfirmRecovery() {
        val engine=SustainedHealth()
        engine.observe(ram(9.0),0)
        assertTrue(engine.observe(ram(9.0),120_000).isEmpty())
        engine.observe(ram(9.0),150_000)
        assertFalse(engine.observe(ram(9.0),180_000).single().recovered)
        engine.observe(emptyList(),210_000)
        assertTrue(engine.observe(ram(16.0),300_000).isEmpty())
        engine.observe(ram(16.0),330_000)
        assertTrue(engine.observe(ram(16.0),360_000).single().recovered)
    }
    @Test fun highThresholdUsesSeparateRecoveryAndRepeatedEpisodes() {
        val engine=SustainedHealth()
        fun temp(v: Double)=listOf(HealthReading("temp","Temperature",v,90.0,85.0))
        engine.observe(temp(90.0),0);engine.observe(temp(91.0),30_000)
        val first=engine.observe(temp(90.0),60_000).single()
        assertTrue(engine.observe(temp(87.0),90_000).isEmpty())
        engine.observe(temp(84.0),120_000);engine.observe(temp(84.0),150_000)
        assertTrue(engine.observe(temp(84.0),180_000).single().recovered)
        engine.observe(temp(91.0),210_000);engine.observe(temp(91.0),240_000)
        assertNotEquals(first.episode,engine.observe(temp(91.0),270_000).single().episode)
    }
}
