package fr.sommelle.core.model

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

actual fun systemLanguageCode(): String {
    val first = NSLocale.preferredLanguages.firstOrNull() as? String ?: "fr"
    return first.substringBefore('-').substringBefore('_').lowercase()
}
