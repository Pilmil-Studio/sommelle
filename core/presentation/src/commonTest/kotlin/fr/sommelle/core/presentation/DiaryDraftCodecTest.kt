package fr.sommelle.core.presentation

import fr.sommelle.core.model.PrefillSource
import fr.sommelle.core.model.SleepQuality
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiaryDraftCodecTest {
    private val night = LocalDate.of(2026, 11, 3)

    @Test
    fun `un brouillon fait l aller-retour`() {
        val draft = DiaryDraft.prefilled(night, null)
            .withTime(TimeField.BED_TIME, LocalTime.of(23, 15))
            .withSleepOnset(30)
            .withAwakeningCount(2)
            .withAwakeMinutes(20)
            .withQuality(SleepQuality.GOOD)
            .copy(prefillSource = PrefillSource.PREVIOUS_NIGHT)
        assertEquals(draft, DiaryDraft.decode(night, draft.encode()))
    }

    @Test
    fun `un texte illisible est ignore`() {
        assertNull(DiaryDraft.decode(night, ""))
        assertNull(DiaryDraft.decode(night, "a;b;c"))
        assertNull(DiaryDraft.decode(night, "23:00;23:15;x;1;5;06:30;06:45;GOOD;"))
    }
}
