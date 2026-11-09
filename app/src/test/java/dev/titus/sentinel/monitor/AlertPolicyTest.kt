package dev.titus.sentinel.monitor

import org.junit.Assert.*
import org.junit.Test

class AlertPolicyTest {
    @Test fun overnightQuietWindowHasExclusiveEndAndAllDayOption() {
        val policy=AlertPolicy(quiet=true,startHour=22,endHour=8)
        assertTrue(policy.quietAt(22));assertTrue(policy.quietAt(0));assertTrue(policy.quietAt(7))
        assertFalse(policy.quietAt(8));assertFalse(policy.quietAt(21))
        assertTrue(policy.copy(startHour=8,endHour=8).quietAt(15))
        assertFalse(policy.copy(quiet=false).quietAt(0))
    }
    @Test fun sameDayWindowAndCooldownBoundary() {
        val policy=AlertPolicy(quiet=true,startHour=8,endHour=17,cooldownSeconds=60)
        assertTrue(policy.quietAt(8));assertFalse(policy.quietAt(17))
        assertFalse(policy.canHandoff(18,60_999,1000));assertTrue(policy.canHandoff(18,61_000,1000))
        assertFalse(policy.canHandoff(10,500_000,null))
        assertTrue(policy.canHandoff(18,0,null))
    }
}
