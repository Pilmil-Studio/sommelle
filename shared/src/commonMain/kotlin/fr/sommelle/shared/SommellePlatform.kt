package fr.sommelle.shared

/**
 * Services dépendants de la plateforme (Android / iOS) pour Sommelle.
 */
interface SommellePlatform {
    val platformName: String
    fun speak(text: String, languageTag: String = "fr-FR")
    fun stopSpeaking()
    fun scheduleDailyReminder(hour: Int, minute: Int, title: String, body: String)
    fun cancelReminders()
}

expect fun createSommellePlatform(): SommellePlatform
