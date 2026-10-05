package com.yasin.vcardly.presentation.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.PrimaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTextButton
import com.yasin.vcardly.core.designsystem.theme.spacing
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val body: Int,
)

private val pages = listOf(
    OnboardingPage(Icons.Filled.Person, R.string.onboarding_1_title, R.string.onboarding_1_body),
    OnboardingPage(Icons.Filled.Lock, R.string.onboarding_2_title, R.string.onboarding_2_body),
    OnboardingPage(Icons.Filled.Notifications, R.string.onboarding_3_title, R.string.onboarding_3_body),
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex
    val finish = { viewModel.finish(onFinished) }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.sm), horizontalArrangement = Arrangement.End) {
            // On the last page a spacer keeps the layout stable instead of leaving a dead tab stop.
            if (!isLast) VCardlyTextButton(text = stringResource(R.string.onboarding_skip), onClick = finish)
            else Box(Modifier.size(MaterialTheme.spacing.minTouchTarget))
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            PageContent(pages[index])
        }

        val pageDescription = stringResource(R.string.onboarding_page_indicator, pagerState.currentPage + 1, pages.size)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.md)
                .semantics { contentDescription = pageDescription },
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(pages.size) { index ->
                val color = if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant
                Box(
                    Modifier
                        .padding(MaterialTheme.spacing.xs)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }

        PrimaryButton(
            text = stringResource(if (isLast) R.string.onboarding_get_started else R.string.onboarding_next),
            onClick = {
                if (isLast) finish() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.lg).padding(bottom = MaterialTheme.spacing.lg),
        )
    }
}

@Composable
private fun PageContent(page: OnboardingPage) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = MaterialTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(112.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                page.icon,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            stringResource(page.title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MaterialTheme.spacing.lg),
        )
        Text(
            stringResource(page.body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MaterialTheme.spacing.md),
        )
    }
}
