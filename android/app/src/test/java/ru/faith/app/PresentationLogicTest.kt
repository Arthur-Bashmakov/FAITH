package ru.faith.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationLogicTest {
    @Test
    fun syntheticScorePercent_clampsAndRoundsModelOutput() {
        assertEquals(0, syntheticScorePercent(-0.2))
        assertEquals(66, syntheticScorePercent(0.6581))
        assertEquals(100, syntheticScorePercent(1.4))
    }

    @Test
    fun modelScoreBand_mapsApiVerdicts() {
        assertEquals(ModelScoreBand.HIGH, modelScoreBand("synthetic"))
        assertEquals(ModelScoreBand.INTERMEDIATE, modelScoreBand("uncertain"))
        assertEquals(ModelScoreBand.LOW, modelScoreBand("human"))
        assertEquals(ModelScoreBand.LOW, modelScoreBand("unknown"))
    }

    @Test
    fun registration_requiresIdentifierAndTenCharacterPassword() {
        assertFalse(isAuthSubmitEnabled("", "1234567890", registerMode = true))
        assertFalse(isAuthSubmitEnabled("user@example.com", "123456789", registerMode = true))
        assertTrue(isAuthSubmitEnabled("user@example.com", "1234567890", registerMode = true))
    }

    @Test
    fun login_acceptsAnyNonBlankPassword() {
        assertFalse(isAuthSubmitEnabled("user@example.com", "", registerMode = false))
        assertTrue(isAuthSubmitEnabled("+79990000000", "x", registerMode = false))
    }
}
