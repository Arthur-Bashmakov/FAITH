package ru.faith.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerUrlTest {
    @Test
    fun normalizeServerUrl_addsHttpsAndTrailingSlash() {
        assertEquals("https://faith-audio.ru/", "faith-audio.ru".normalizeServerUrl())
    }

    @Test
    fun normalizeServerUrl_preservesExplicitSchemeAndRemovesExtraSlashes() {
        assertEquals("http://10.0.2.2:8000/", " http://10.0.2.2:8000/// ".normalizeServerUrl())
    }
}
