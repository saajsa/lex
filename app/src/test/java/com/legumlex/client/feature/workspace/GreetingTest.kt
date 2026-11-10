package com.legumlex.client.feature.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {
    @Test fun nameThenComma() = assertEquals("Asha,", greetingName("Asha"))
    @Test fun noNameIsJustWelcome() { assertEquals("Welcome", greetingName("")); assertEquals("Welcome", greetingName("  ")) }
}
