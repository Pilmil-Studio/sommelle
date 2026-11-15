package fr.sommelle.feature.diary

import android.content.Context
import kotlinx.datetime.LocalDate

/** Brouillons de l'agenda dans les préférences de l'appli (jamais envoyés ailleurs, effacés avec les données). */
class PreferencesDraftStore(context: Context) : DraftStore {
    private val prefs = context.getSharedPreferences("diary_drafts", Context.MODE_PRIVATE)

    override fun load(nightOf: LocalDate): String? = prefs.getString(key(nightOf), null)

    override fun save(nightOf: LocalDate, draft: String) {
        prefs.edit().putString(key(nightOf), draft).apply()
    }

    override fun clear(nightOf: LocalDate) {
        prefs.edit().remove(key(nightOf)).apply()
    }

    private fun key(nightOf: LocalDate) = "draft_$nightOf"
}
