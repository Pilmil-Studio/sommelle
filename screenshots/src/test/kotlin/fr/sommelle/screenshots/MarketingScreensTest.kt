package fr.sommelle.screenshots

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import fr.sommelle.core.designsystem.SommelleTheme
import fr.sommelle.core.presentation.DiaryStep
import fr.sommelle.feature.diary.DiaryMode
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/**
 * Captures en pleine résolution (1080 x 2340) pour les visuels marketing, une série par langue.
 * Copiées par pilmil-core/marketing/capture.sh dans docs/marketing/captures/<langue>/<méthode>.png.
 */
abstract class MarketingScreens(private val language: String) {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(locale = language),
        theme = "android:Theme.Material.Light.NoActionBar",
        useDeviceResolution = true,
    )

    init {
        // Les textes composés par core:presentation suivent la langue de la JVM, les ressources celle de Paparazzi.
        Locale.setDefault(Locale.forLanguageTag(language))
    }

    private fun snap(night: Boolean = false, content: @Composable () -> Unit) =
        paparazzi.snapshot { SommelleTheme(night = night) { content() } }

    @Test fun bienvenue() = snap { Samples.Welcome() }
    @Test fun questionnaire() = snap { Samples.Question() }
    @Test fun accueil() = snap { Samples.Home() }
    @Test fun agenda_heure() = snap { Samples.Diary(DiaryMode.GUIDED, DiaryStep.BED_TIME) }
    @Test fun agenda_estimation() = snap { Samples.Diary(DiaryMode.GUIDED, DiaryStep.SLEEP_ONSET) }
    @Test fun agenda_resume() = snap { Samples.Diary(DiaryMode.SUMMARY, DiaryStep.BED_TIME) }
    @Test fun bilan() = snap { Samples.Week() }
    @Test fun bilan_programme() = snap { Samples.WeekWithOutcome() }
    @Test fun nuit() = snap(night = true) { Samples.Night() }
    @Test fun reglages() = snap { Samples.Settings() }
    @Test fun resume_medecin() = snap { Samples.Report() }
    @Test fun exercices() = snap { Samples.Exercises() }
}

class MarketingScreensFrTest : MarketingScreens("fr")

class MarketingScreensEnTest : MarketingScreens("en")
