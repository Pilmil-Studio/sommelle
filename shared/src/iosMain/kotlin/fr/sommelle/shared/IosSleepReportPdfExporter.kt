package fr.sommelle.shared

import fr.sommelle.core.presentation.ClinicianReport
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.NSStringDrawingUsesLineFragmentOrigin
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIGraphicsPDFRendererContext
import platform.UIKit.boundingRectWithSize
import platform.UIKit.drawInRect

/**
 * Génération locale du bilan PDF A4 sur iOS via [UIGraphicsPDFRenderer], sans accès réseau.
 */
@OptIn(ExperimentalForeignApi::class)
class IosSleepReportPdfExporter : SleepReportPdfExporter {

    override fun exportPdf(report: ClinicianReport, labels: SleepReportLabels): String {
        val renderer = UIGraphicsPDFRenderer(bounds = CGRectMake(0.0, 0.0, PAGE_WIDTH, PAGE_HEIGHT))
        val data = renderer.PDFDataWithActions { context ->
            val writer = PdfWriter(requireNotNull(context), labels.pageNumber)
            writer.title(labels.title)
            writer.paragraph(
                listOf(
                    "${labels.startedPrefix}${report.programStart} · ${report.programWeek}",
                    "${labels.agePrefix}${report.ageBand}",
                    "${labels.pathwayPrefix}${report.pathway}",
                ) + listOfNotNull(report.currentWindow?.let { "${labels.windowPrefix}$it" }),
            )

            if (report.safetyNotes.isNotEmpty()) {
                writer.heading(labels.safetyHeading)
                writer.paragraph(report.safetyNotes.map { "• $it" })
            }

            writer.heading(labels.weeksHeading)
            if (report.weeks.isEmpty()) {
                writer.paragraph(listOf(labels.emptyWeeks))
            } else {
                writer.table(
                    header = listOf(
                        "",
                        labels.colInBed,
                        labels.colAsleep,
                        labels.colEfficiency,
                        labels.colOnset,
                        labels.colAwake,
                        labels.colQuality,
                        labels.colWindowShort,
                    ),
                    weights = listOf(1.6, 0.9, 0.9, 0.9, 1.1, 0.9, 0.9, 1.4),
                    rows = report.weeks.map {
                        listOf(
                            "${it.label} (${it.nights})",
                            it.timeInBed,
                            it.totalSleep,
                            it.efficiency,
                            it.sleepOnset,
                            it.awake,
                            it.quality,
                            it.window,
                        )
                    },
                )
            }

            if (report.outcome.isNotEmpty()) {
                writer.heading(labels.outcomeHeading)
                writer.table(
                    header = listOf("", labels.colBaseline, labels.colLast7),
                    weights = listOf(2.0, 1.0, 1.0),
                    rows = report.outcome.map { listOf(it.metric.label, it.before, it.after) },
                )
            }

            if (report.nights.isNotEmpty()) {
                writer.heading(labels.diaryHeading(report.nights.size))
                writer.table(
                    header = listOf(
                        labels.colNight,
                        labels.colBed,
                        labels.colOut,
                        labels.colOnset,
                        labels.colAwake,
                        labels.colAsleep,
                        labels.colEfficiency,
                        labels.colQuality,
                    ),
                    weights = listOf(1.1, 0.9, 0.9, 0.9, 1.2, 0.9, 0.8, 0.8),
                    rows = report.nights.map {
                        listOf(
                            it.night,
                            it.bedTime,
                            it.outOfBed,
                            it.sleepOnset,
                            it.awake,
                            it.totalSleep,
                            it.efficiency,
                            it.quality,
                        )
                    },
                )
            }

            writer.footerNote("${labels.disclaimer} ${labels.generatedOn(report.generatedOn)}")
            writer.finish()
        }

        val path = NSTemporaryDirectory() + "sommelle-resume.pdf"
        data.writeToFile(path, atomically = true)
        return path
    }

    private class PdfWriter(
        private val context: UIGraphicsPDFRendererContext,
        private val pageLabel: (Int) -> String,
    ) {
        private val contentWidth = PAGE_WIDTH - MARGIN * 2.0
        private var pageNumber = 0
        private var y = MARGIN

        private val titleAttrs = textAttrs(UIFont.boldSystemFontOfSize(18.0), NIGHT_BLUE)
        private val headingAttrs = textAttrs(UIFont.boldSystemFontOfSize(12.0), NIGHT_BLUE)
        private val bodyAttrs = textAttrs(UIFont.systemFontOfSize(10.0), INK)
        private val headerAttrs = textAttrs(UIFont.boldSystemFontOfSize(8.5), MUTED)
        private val cellAttrs = textAttrs(UIFont.systemFontOfSize(8.5), INK)
        private val mutedAttrs = textAttrs(UIFont.systemFontOfSize(8.5), MUTED)

        init {
            newPage()
        }

        fun title(text: String) {
            y += drawText(text, MARGIN, y, contentWidth, titleAttrs) + 12.0
        }

        fun heading(text: String) {
            ensureSpace(28.0)
            y += 10.0
            y += drawText(text, MARGIN, y, contentWidth, headingAttrs) + 6.0
        }

        fun paragraph(lines: List<String>) {
            lines.forEach { line ->
                val height = measureHeight(line, contentWidth, bodyAttrs)
                ensureSpace(height + 4.0)
                drawText(line, MARGIN, y, contentWidth, bodyAttrs)
                y += height + 4.0
            }
            y += 4.0
        }

        fun table(header: List<String>, weights: List<Double>, rows: List<List<String>>) {
            val totalWeight = weights.sum()
            val colWidths = weights.map { (it / totalWeight) * contentWidth }

            fun drawRow(cells: List<String>, attrs: Map<Any?, *>) {
                val cellHeights = cells.mapIndexed { idx, cell ->
                    measureHeight(cell, colWidths[idx] - 4.0, attrs)
                }
                val rowHeight = (cellHeights.maxOrNull() ?: 12.0) + 6.0
                ensureSpace(rowHeight + 2.0)
                var x = MARGIN
                cells.forEachIndexed { idx, cell ->
                    drawText(cell, x, y, colWidths[idx] - 4.0, attrs)
                    x += colWidths[idx]
                }
                y += rowHeight
            }

            drawRow(header, headerAttrs)
            drawHorizontalRule(y - 2.0)
            y += 2.0
            rows.forEach { row -> drawRow(row, cellAttrs) }
            y += 6.0
        }

        fun footerNote(text: String) {
            val height = measureHeight(text, contentWidth, mutedAttrs)
            ensureSpace(height + 14.0)
            y += 8.0
            drawHorizontalRule(y)
            y += 6.0
            drawText(text, MARGIN, y, contentWidth, mutedAttrs)
            y += height
        }

        fun finish() {
            drawPageFooter()
        }

        private fun newPage() {
            if (pageNumber > 0) {
                drawPageFooter()
            }
            context.beginPage()
            pageNumber++
            y = MARGIN
        }

        private fun ensureSpace(needed: Double) {
            if (y + needed > PAGE_HEIGHT - MARGIN - 20.0) {
                newPage()
            }
        }

        private fun drawPageFooter() {
            drawText(
                pageLabel(pageNumber),
                MARGIN,
                PAGE_HEIGHT - MARGIN + 4.0,
                contentWidth,
                mutedAttrs,
            )
        }

        private fun drawHorizontalRule(lineY: Double) {
            LINE_COLOR.setStroke()
            val path = UIBezierPath()
            path.lineWidth = 0.75
            path.moveToPoint(platform.CoreGraphics.CGPointMake(MARGIN, lineY))
            path.addLineToPoint(platform.CoreGraphics.CGPointMake(PAGE_WIDTH - MARGIN, lineY))
            path.stroke()
        }

        private fun measureHeight(text: String, width: Double, attrs: Map<Any?, *>): Double {
            val nsText = NSString.create(string = text)
            val rect = nsText.boundingRectWithSize(
                size = CGSizeMake(width, 1000.0),
                options = NSStringDrawingUsesLineFragmentOrigin,
                attributes = attrs,
                context = null,
            )
            return rect.useContents { size.height }
        }

        private fun drawText(text: String, x: Double, topY: Double, width: Double, attrs: Map<Any?, *>): Double {
            val height = measureHeight(text, width, attrs)
            val nsText = NSString.create(string = text)
            nsText.drawInRect(CGRectMake(x, topY, width, height + 2.0), withAttributes = attrs)
            return height
        }

        private fun textAttrs(font: UIFont, color: UIColor): Map<Any?, *> = mapOf(
            NSFontAttributeName to font,
            NSForegroundColorAttributeName to color,
        )
    }

    private companion object {
        const val PAGE_WIDTH = 595.0
        const val PAGE_HEIGHT = 842.0
        const val MARGIN = 44.0

        val NIGHT_BLUE = UIColor.colorWithRed(0x2F / 255.0, 0x3A / 255.0, 0x6B / 255.0, 1.0)
        val MUTED = UIColor.colorWithRed(0x4A / 255.0, 0x4F / 255.0, 0x63 / 255.0, 1.0)
        val INK = UIColor.colorWithRed(0x1C / 255.0, 0x1F / 255.0, 0x2B / 255.0, 1.0)
        val LINE_COLOR = UIColor.colorWithRed(0xE2 / 255.0, 0xDD / 255.0, 0xD3 / 255.0, 1.0)
    }
}
