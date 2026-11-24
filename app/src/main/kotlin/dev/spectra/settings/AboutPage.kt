package dev.spectra.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraLogo
import dev.spectra.components.SpectraPage
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization

/** What Spectra is and why: the hero, the ethos and the story. */
@Composable
internal fun AboutPage() {
    SpectraPage(title = AppLocalization.string("About Spectra")) {
        AboutHero()
        SpectraCard {
            Column(
                modifier = Modifier.padding(SpectraLayout.Space.l),
                verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
            ) {
                Text(AppLocalization.string("settings.aboutEthosTitle"), style = MaterialTheme.typography.titleMedium)
                AppLocalization.string("settings.aboutEthosLines").split("\n").forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                        Box(
                            Modifier
                                .padding(top = SpectraLayout.Space.s)
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), CircleShape),
                        )
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = secondaryText)
                    }
                }
            }
        }
        SpectraCard {
            Column(
                modifier = Modifier.padding(SpectraLayout.Space.l),
                verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
            ) {
                Text(AppLocalization.string("settings.aboutNarrativeTitle"), style = MaterialTheme.typography.titleMedium)
                AppLocalization.string("settings.aboutNarrativeParagraphs").split("\n\n").forEach { paragraph ->
                    Text(paragraph, style = MaterialTheme.typography.bodyMedium, color = secondaryText)
                }
            }
        }
    }
}

/**
 * The logo on a slowly turning spectrum. The animation belongs to the
 * page's composition, so it stops when the page is left.
 */
@Composable
private fun AboutHero() {
    val rotation by rememberInfiniteTransition(label = "about-hero").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 18_000, easing = LinearEasing), RepeatMode.Restart),
        label = "about-hero-rotation",
    )
    SpectraCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SpectraLayout.Space.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(220.dp).rotate(rotation).blur(26.dp, BlurredEdgeTreatment.Unbounded)) {
                    drawCircle(Brush.sweepGradient(HERO_SPECTRUM))
                }
                Box(
                    Modifier
                        .size(178.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f), CircleShape),
                )
                SpectraLogo(size = 96.dp)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
            ) {
                Text(
                    AppLocalization.string("settings.aboutTitle"),
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )
                Text(
                    AppLocalization.string("settings.aboutSubtitle"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryText,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The spectrum behind the logo: artwork with its own palette, not the theme's. */
private val HERO_SPECTRUM = listOf(
    Color(0xFFFF3B30).copy(alpha = 0.85f),
    Color(0xFFFF9500).copy(alpha = 0.92f), // design-tokens: artwork
    Color(0xFFFFCC00).copy(alpha = 0.9f),
    Color(0xFF34C759).copy(alpha = 0.82f),
    Color(0xFF0A84FF).copy(alpha = 0.82f),
    Color(0xFF5856D6).copy(alpha = 0.82f),
    Color(0xFFFF2D55).copy(alpha = 0.88f),
    Color(0xFFFF3B30).copy(alpha = 0.85f),
)
