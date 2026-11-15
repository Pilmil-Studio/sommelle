package fr.sommelle.feature.export

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfReportTest {

    @Test
    fun `reportNeedsSystemFont active la police systeme uniquement pour le polonais et l ukrainien`() {
        assertTrue(reportNeedsSystemFont("pl"))
        assertTrue(reportNeedsSystemFont("uk"))
        assertFalse(reportNeedsSystemFont("fr"))
        assertFalse(reportNeedsSystemFont("en"))
        assertFalse(reportNeedsSystemFont("de"))
        assertFalse(reportNeedsSystemFont("es"))
    }

    @Test
    fun `prepareExportsDir purge les anciens fichiers temporaires avant ecriture`() {
        val cacheDir = Files.createTempDirectory("sommelle-export-test").toFile()
        try {
            val exportsDir = File(cacheDir, "exports").apply { mkdirs() }
            val stalePdf = File(exportsDir, "old.pdf").apply { writeText("old") }
            val nested = File(exportsDir, "subdir").apply { mkdirs() }
            File(nested, "stale.csv").writeText("stale")

            val prepared = PdfReport.prepareExportsDir(cacheDir)

            assertTrue(prepared.exists())
            assertTrue(prepared.isDirectory)
            assertFalse(stalePdf.exists())
            assertFalse(nested.exists())
            assertEquals(emptyList(), prepared.listFiles()?.toList() ?: emptyList())
        } finally {
            cacheDir.deleteRecursively()
        }
    }
}
