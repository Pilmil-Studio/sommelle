package fr.sommelle.core.model

import java.util.Locale

actual fun systemLanguageCode(): String = Locale.getDefault().language
