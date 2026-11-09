package com.legumlex.client.feature.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeldDraftTest {
    @Test fun no_draft_arrives_as_a_standing_that_has_not_started_and_is_not_held() {
        // What GET workspace really sends when the contact has no draft.
        val none = mapOf("started" to false, "reopened" to false, "documents" to emptyList<Any>(), "all_uploaded" to false, "codes" to false, "form" to emptyMap<String, Any>())
        assertNull(heldDraft(none))
        assertNull(heldDraft(null))
    }

    @Test fun a_started_draft_is_held_with_its_documents() {
        val d = mapOf("started" to true, "documents" to listOf(mapOf("type" to "bar_council_id", "label" to "Certificate", "uploaded" to true)))
        assertEquals("bar_council_id", heldDraft(d)!!.onb.documents.single().type)
    }
}
