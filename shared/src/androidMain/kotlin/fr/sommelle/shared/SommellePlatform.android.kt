package fr.sommelle.shared

private class AndroidSommellePlatformStub : SommellePlatform {
    override val platformName: String = "Android"
    override fun speak(text: String, languageTag: String) = Unit
    override fun stopSpeaking() = Unit
    override fun scheduleDailyReminder(hour: Int, minute: Int, title: String, body: String) = Unit
    override fun cancelReminders() = Unit
}

actual fun createSommellePlatform(): SommellePlatform = AndroidSommellePlatformStub()
