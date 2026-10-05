package com.yasin.vcardly.presentation.onboarding

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.VCardlyLogo
import com.yasin.vcardly.core.designsystem.component.VCardlyMark
import com.yasin.vcardly.core.designsystem.component.VCardlyPrimaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTextButton
import com.yasin.vcardly.core.designsystem.theme.vcColors
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class OnboardingPage(
    val badge: ImageVector?,
    @StringRes val title: Int,
    @StringRes val body: Int,
)

private val pages = listOf(
    OnboardingPage(null, R.string.onboarding_1_title, R.string.onboarding_1_body),
    OnboardingPage(Icons.Rounded.Lock, R.string.onboarding_2_title, R.string.onboarding_2_body),
    OnboardingPage(Icons.Rounded.NotificationsActive, R.string.onboarding_3_title, R.string.onboarding_3_body),
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    OnboardingContent(onFinish = { viewModel.finish(onFinished) })
}

/** Stateless onboarding (used directly by UI tests). */
@Composable
fun OnboardingContent(onFinish: () -> Unit, pagerState: PagerState = rememberPagerState(pageCount = { pages.size })) {
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        VCardlyLogo(
            appName = stringResource(R.string.app_name),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 28.dp),
        )

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            // Gentle parallax: the illustration fades and shrinks as it moves off-screen.
            val offset = (pagerState.currentPage - index + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            PageContent(pages[index], Modifier.graphicsLayer { alpha = 1f - offset * 0.6f; scaleX = 1f - offset * 0.08f; scaleY = 1f - offset * 0.08f })
        }

        PagerDots(pagerState.currentPage, pages.size, Modifier.align(Alignment.CenterHorizontally))

        VCardlyPrimaryButton(
            text = stringResource(if (isLast) R.string.onboarding_get_started else R.string.onboarding_next),
            onClick = { if (isLast) onFinish() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
            trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 28.dp),
        )
        // Skip keeps its slot on the last page so the layout does not jump.
        Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
            if (!isLast) VCardlyTextButton(text = stringResource(R.string.onboarding_skip), onClick = onFinish, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PagerDots(current: Int, count: Int, modifier: Modifier) {
    val description = stringResource(R.string.onboarding_page_indicator, current + 1, count)
    Row(modifier.semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { index ->
            val width by animateDpAsState(if (index == current) 24.dp else 8.dp, label = "dotWidth")
            Box(
                Modifier.height(8.dp).width(width).clip(CircleShape)
                    .background(if (index == current) MaterialTheme.vcColors.cta else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun PageContent(page: OnboardingPage, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CardStackIllustration(page.badge)
        Text(
            stringResource(page.title),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 36.dp),
        )
        Text(
            stringResource(page.body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

/** Fanned stack of visiting cards with the VCardly mark; an optional badge (lock, bell) floats on the front card. */
@Composable
private fun CardStackIllustration(badge: ImageVector?) {
    val colors = MaterialTheme.vcColors
    Box(Modifier.size(width = 280.dp, height = 210.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(260.dp).clip(CircleShape).background(colors.blue.container.copy(alpha = 0.7f)))
        // Back card
        Box(
            Modifier.size(width = 196.dp, height = 120.dp).offset(x = (-34).dp, y = (-18).dp).rotate(-16f)
                .clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(colors.gradientPurple.map { it.copy(alpha = 0.85f) })),
        )
        // Middle card
        Box(
            Modifier.size(width = 196.dp, height = 120.dp).offset(x = (-12).dp, y = (-4).dp).rotate(-9f)
                .clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
        )
        // Front card
        Box(
            Modifier
                .size(width = 208.dp, height = 128.dp)
                .offset(x = 18.dp, y = 14.dp)
                .rotate(-4f)
                .shadow(18.dp, RoundedCornerShape(18.dp), ambientColor = colors.shadow, spotColor = colors.shadow)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(colors.cardHero))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                    VCardlyMark(size = 20.dp)
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
            Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(width = 84.dp, height = 6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.75f)))
                Box(Modifier.size(width = 60.dp, height = 6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.45f)))
            }
            Icon(Icons.Rounded.Wifi, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.TopEnd).size(18.dp).rotate(90f))
            MiniQr(Modifier.align(Alignment.BottomEnd).size(38.dp))
        }
        if (badge != null) {
            Box(
                Modifier.offset(x = 104.dp, y = (-56).dp).size(56.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(badge, contentDescription = null, tint = colors.blue.accent, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/** Decorative QR-like pattern (not a real code). */
@Composable
private fun MiniQr(modifier: Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(6.dp)).background(Color.White).padding(4.dp)) {
        val n = 7
        val cell = size.width / n
        val pattern = longArrayOf(0b1110111, 0b1010101, 0b1110111, 0b0001000, 0b1101011, 0b0110110, 0b1011101)
        for (r in 0 until n) for (c in 0 until n) {
            if ((pattern[r] shr (n - 1 - c)) and 1L == 1L) {
                drawRect(Color(0xFF14276B), topLeft = androidx.compose.ui.geometry.Offset(c * cell, r * cell), size = androidx.compose.ui.geometry.Size(cell, cell))
            }
        }
    }
}
