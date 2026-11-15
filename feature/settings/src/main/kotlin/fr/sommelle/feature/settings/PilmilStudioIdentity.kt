package fr.sommelle.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Emblème « L'Îlot Hors-Ligne » de Pilmil Studio dessiné en vectoriel pur.
 */
@Composable
fun PilmilStudioMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    val bgColor = if (darkTheme) Color(0xFF1E1D1A) else Color(0xFFFAF8F4)
    val inkColor = if (darkTheme) Color(0xFFF3EFE6) else Color(0xFF1E1D1A)
    val archColor = if (darkTheme) Color(0xFF4D9A82) else Color(0xFF285D4D)
    val amberColor = if (darkTheme) Color(0xFFF59E0B) else Color(0xFFD97706)

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val s = w / 256f
        val center = Offset(w / 2f, w / 2f)

        drawCircle(color = bgColor, radius = 114f * s, center = center)
        drawCircle(
            color = inkColor,
            radius = 114f * s,
            center = center,
            style = Stroke(width = 10f * s),
        )
        drawArc(
            color = archColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(62f * s, 80f * s),
            size = Size(132f * s, 132f * s),
            style = Stroke(width = 22f * s, cap = StrokeCap.Round),
        )
        drawLine(
            color = inkColor,
            start = Offset(56f * s, 176f * s),
            end = Offset(200f * s, 176f * s),
            strokeWidth = 14f * s,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = amberColor,
            radius = 22f * s,
            center = Offset(128f * s, 134f * s),
        )
    }
}

@Composable
fun PilmilLegalBlock(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PilmilStudioMark(size = 40.dp)
            Column {
                Text(
                    text = stringResource(R.string.settings_legal_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Alfortville, France",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = "Éditeur : Pilmil Studio (Alfortville, France).\n" +
                "Confidentialité : 100 % hors-ligne, aucune permission réseau, aucun traceur, aucun compte.\n" +
                "Licence : logiciel libre sous licence MPL-2.0.\n" +
                "Contact : pilmil.studio@proton.me · https://relthdrarxash.github.io\n\n" +
                "Un besoin d'outil hors-ligne sans pub ni suivi ? Écrivez-nous à pilmil.studio@proton.me.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
