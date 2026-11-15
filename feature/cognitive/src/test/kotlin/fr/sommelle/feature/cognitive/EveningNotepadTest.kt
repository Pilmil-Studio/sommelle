package fr.sommelle.feature.cognitive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EveningNotepadTest {
    @Test
    fun `effacer l inquietude garde les habitudes cochees`() {
        val keys = setOf("worry", "action", "put_away", "habit_0", "habit_3")
        val cleared = EveningNotepad.keysToClear(keys)
        assertEquals(setOf("worry", "action", "put_away"), cleared.toSet())
        assertFalse(cleared.any { it.startsWith("habit_") })
    }
}
