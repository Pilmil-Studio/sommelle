package fr.sommelle.shared

import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import platform.Foundation.NSDateComponents

private class IosSommellePlatform : SommellePlatform {
    private val synthesizer = AVSpeechSynthesizer()

    override val platformName: String = "iOS"

    override fun speak(text: String, languageTag: String) {
        stopSpeaking()
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        utterance.voice = AVSpeechSynthesisVoice.voiceWithLanguage(languageTag)
        synthesizer.speakUtterance(utterance)
    }

    override fun stopSpeaking() {
        if (synthesizer.isSpeaking()) {
            synthesizer.stopSpeakingAtBoundary(
                platform.AVFAudio.AVSpeechBoundary.AVSpeechBoundaryImmediate,
            )
        }
    }

    override fun scheduleDailyReminder(hour: Int, minute: Int, title: String, body: String) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
        }
        val components = NSDateComponents().apply {
            setHour(hour.toLong())
            setMinute(minute.toLong())
        }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
            components,
            repeats = true,
        )
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = REMINDER_ID,
            content = content,
            trigger = trigger,
        )
        center.addNotificationRequest(request, withCompletionHandler = null)
    }

    override fun cancelReminders() {
        UNUserNotificationCenter.currentNotificationCenter()
            .removePendingNotificationRequestsWithIdentifiers(listOf(REMINDER_ID))
    }

    private companion object {
        const val REMINDER_ID = "fr.sommelle.daily_reminder"
    }
}

actual fun createSommellePlatform(): SommellePlatform = IosSommellePlatform()
