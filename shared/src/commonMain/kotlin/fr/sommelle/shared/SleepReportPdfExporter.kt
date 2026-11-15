package fr.sommelle.shared

import fr.sommelle.core.presentation.ClinicianReport

/**
 * Libellés localisés utilisés par [SleepReportPdfExporter] pour rendre un [ClinicianReport] en PDF.
 */
data class SleepReportLabels(
    val title: String,
    val startedPrefix: String,
    val agePrefix: String,
    val pathwayPrefix: String,
    val windowPrefix: String,
    val safetyHeading: String,
    val weeksHeading: String,
    val emptyWeeks: String,
    val colInBed: String,
    val colAsleep: String,
    val colEfficiency: String,
    val colOnset: String,
    val colAwake: String,
    val colQuality: String,
    val colWindowShort: String,
    val outcomeHeading: String,
    val colBaseline: String,
    val colLast7: String,
    val diaryHeading: (Int) -> String,
    val colNight: String,
    val colBed: String,
    val colOut: String,
    val disclaimer: String,
    val generatedOn: (String) -> String,
    val pageNumber: (Int) -> String,
) {
    companion object {
        fun forLanguage(languageCode: String): SleepReportLabels =
            if (languageCode == "fr") FRENCH else ENGLISH

        val FRENCH = SleepReportLabels(
            title = "Bilan sommeil — Sommelle",
            startedPrefix = "Début du programme : ",
            agePrefix = "Tranche d'âge : ",
            pathwayPrefix = "Parcours : ",
            windowPrefix = "Fenêtre actuelle : ",
            safetyHeading = "Points de vigilance",
            weeksHeading = "Synthèse hebdomadaire",
            emptyWeeks = "Aucune semaine complète enregistrée pour le moment.",
            colInBed = "Au lit",
            colAsleep = "Sommeil",
            colEfficiency = "Effic.",
            colOnset = "Endorm.",
            colAwake = "Éveils",
            colQuality = "Qualité",
            colWindowShort = "Fenêtre",
            outcomeHeading = "Évolution (ligne de base vs 7 dernières nuits)",
            colBaseline = "Début",
            colLast7 = "7 dernières",
            diaryHeading = { count -> "Agenda détaillé ($count nuits)" },
            colNight = "Nuit",
            colBed = "Coucher",
            colOut = "Lever",
            disclaimer = "Document généré localement par Sommelle (sans réseau). Ne remplace pas un avis médical.",
            generatedOn = { date -> "Généré le $date." },
            pageNumber = { page -> "Page $page" },
        )

        val ENGLISH = SleepReportLabels(
            title = "Sleep summary — Sommelle",
            startedPrefix = "Program start: ",
            agePrefix = "Age group: ",
            pathwayPrefix = "Pathway: ",
            windowPrefix = "Current window: ",
            safetyHeading = "Safety notes",
            weeksHeading = "Weekly summary",
            emptyWeeks = "No complete week recorded yet.",
            colInBed = "In bed",
            colAsleep = "Asleep",
            colEfficiency = "Effic.",
            colOnset = "Onset",
            colAwake = "Awake",
            colQuality = "Quality",
            colWindowShort = "Window",
            outcomeHeading = "Progress (baseline vs last 7 nights)",
            colBaseline = "Baseline",
            colLast7 = "Last 7",
            diaryHeading = { count -> "Detailed sleep diary ($count nights)" },
            colNight = "Night",
            colBed = "Bed",
            colOut = "Out",
            disclaimer = "Generated locally on device by Sommelle (offline). Does not replace medical advice.",
            generatedOn = { date -> "Generated on $date." },
            pageNumber = { page -> "Page $page" },
        )
    }
}

/**
 * Génère le résumé PDF A4 d'un [ClinicianReport] localement sur l'appareil et retourne le chemin du fichier écrit.
 */
interface SleepReportPdfExporter {
    fun exportPdf(report: ClinicianReport, labels: SleepReportLabels): String
}
