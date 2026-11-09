package com.legumlex.client.feature.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstStepsTest {
    private fun fs(balance: Int, trial: Int = 100, given: Boolean = true) = FirstSteps(true, 0, balance, trial, given)

    @Test fun the_trial_credits_step_when_the_balance_is_what_the_trial_gave() {
        val s = creditsStep(fs(100))
        assertTrue(s.done); assertEquals("Credits In Hand", s.title)
        assertEquals("Your 100 trial credits are in your account. Credits pay for the morning eCourts check.", s.body)
    }

    @Test fun any_other_balance_says_how_many_credits() {
        assertEquals("You have 84 credits in your account. Credits pay for the morning eCourts check.", creditsStep(fs(84)).body)
        // A trial that was not given here (a second add, or held for the reviewer) never claims it.
        assertEquals("You have 100 credits in your account. Credits pay for the morning eCourts check.", creditsStep(fs(100, given = false)).body)
        assertTrue(creditsStep(fs(84)).done)
    }

    @Test fun no_credits_makes_it_the_second_step_to_do() {
        val s = creditsStep(fs(0))
        assertFalse(s.done); assertEquals("Add Credits", s.title); assertEquals("Credits pay for the morning eCourts check.", s.body)
    }

    @Test fun the_ring_is_half_when_credits_are_in_hand() {
        assertEquals(0.5f, firstStepsProgress(fs(100)), 0f)
        assertEquals(0f, firstStepsProgress(fs(0)), 0f)
    }

    @Test fun show_decides_and_an_absent_block_is_todays_home() {
        val shown = parseFirstSteps(mapOf("show" to true, "cases" to 0, "balance" to 100, "trial_credits" to 100, "trial_given" to true))!!
        assertEquals(100, shown.balance); assertTrue(shown.trialGiven)
        assertNull(parseFirstSteps(mapOf("show" to false, "balance" to 100)))
        assertNull(parseFirstSteps(null))
        assertNull(parseFirstSteps(emptyMap<String, Any?>()))
    }

    @Test fun home_carries_the_block_only_when_the_server_shows_it() {
        fun home(extra: Map<String, Any?>) = parseHome(mapOf("profile" to mapOf("name" to "X"), "score" to mapOf("next" to null), "tiles" to emptyMap<String, Any?>()) + extra)
        assertNull(home(emptyMap()).firstSteps)
        assertNull(home(mapOf("first_steps" to mapOf("show" to false))).firstSteps)
        assertEquals(0, home(mapOf("first_steps" to mapOf("show" to true, "balance" to 0))).firstSteps!!.balance)
    }

    @Test fun no_em_dash_in_the_words() {
        listOf(fs(100), fs(84), fs(0)).forEach { assertFalse(creditsStep(it).body.contains("—")) }
    }
}
