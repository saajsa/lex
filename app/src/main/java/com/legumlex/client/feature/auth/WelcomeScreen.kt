package com.legumlex.client.feature.auth

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.components.LegumPrimaryButton
import com.legumlex.client.ui.illustrations.CalendarIllustration
import com.legumlex.client.ui.illustrations.CaseFileIllustration
import com.legumlex.client.ui.illustrations.ShieldIllustration
import com.legumlex.client.ui.theme.AccentInk
import com.legumlex.client.ui.theme.BorderColor
import com.legumlex.client.ui.theme.BorderSubtle
import com.legumlex.client.ui.theme.LocalSpacing
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ui.theme.Surface2
import com.legumlex.client.ui.theme.TertiaryText
import kotlinx.coroutines.launch

private data class Slide(val title: String, val body: String)

private val slides = listOf(
    Slide(
        title = "Your case, in your pocket.",
        body  = "Hearing dates, documents, and updates from your advocate — all in one calm, private place.",
    ),
    Slide(
        title = "Hearing dates, documents, updates — all in one place.",
        body  = "Track every matter and document without a phone call or an email thread.",
    ),
    Slide(
        title = "Secure, private, always with you.",
        body  = "Sensitive information stays encrypted on your device. Only you and your advocate see it.",
    ),
)

// Screen 02 — handoff §Welcome Carousel.
@Composable
fun WelcomeScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val spacing = LocalSpacing.current

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Top bar with Skip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.s4, vertical = spacing.s3),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onFinish,
                colors = ButtonDefaults.textButtonColors(contentColor = SecondaryText),
            ) {
                Text("Skip", style = MaterialTheme.typography.labelLarge)
            }
        }

        // Pager body
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            SlideContent(slide = slides[page], pageNumber = page + 1, total = slides.size)
        }

        // Dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.s4),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(slides.size) { i ->
                val active = i == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(if (active) 24.dp else 6.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (active) AccentInk else BorderColor)
                        .animateContentSize(),
                )
            }
        }

        // Primary CTA — copy changes on last slide
        Box(modifier = Modifier.padding(horizontal = spacing.s4, vertical = spacing.s4)) {
            val isLast = pagerState.currentPage == slides.lastIndex
            LegumPrimaryButton(
                text = if (isLast) "Get Started" else "Continue",
                onClick = {
                    if (isLast) {
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
            )
        }
    }
}

@Composable
private fun SlideContent(slide: Slide, pageNumber: Int, total: Int) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(spacing.s6))
        // Legal-themed line illustration, one per slide.
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Surface2,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.size(280.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                val illoModifier = Modifier.size(188.dp)
                when (pageNumber) {
                    1    -> CaseFileIllustration(illoModifier)
                    2    -> CalendarIllustration(illoModifier)
                    else -> ShieldIllustration(illoModifier)
                }
            }
        }
        Spacer(Modifier.height(spacing.s6))
        Text(
            "SLIDE $pageNumber OF $total",
            style = MaterialTheme.typography.labelSmall,
            color = TertiaryText,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(spacing.s2))
        Text(
            slide.title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = spacing.s2),
        )
        Spacer(Modifier.height(spacing.s3))
        Text(
            slide.body,
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryText,
        )
    }
}
