package com.yasin.vcardly.presentation.followups

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.theme.Tone
import com.yasin.vcardly.core.designsystem.theme.vcColors
import com.yasin.vcardly.domain.model.FollowUpBucket
import com.yasin.vcardly.domain.model.FollowUpStatus
import com.yasin.vcardly.domain.model.FollowUpType

@StringRes
fun FollowUpType.labelRes(): Int = when (this) {
    FollowUpType.CALL -> R.string.followup_type_call
    FollowUpType.WHATSAPP -> R.string.followup_type_whatsapp
    FollowUpType.EMAIL -> R.string.followup_type_email
    FollowUpType.MEETING -> R.string.followup_type_meeting
    FollowUpType.QUOTATION -> R.string.followup_type_quotation
    FollowUpType.PAYMENT -> R.string.followup_type_payment
    FollowUpType.MESSAGE -> R.string.followup_type_message
    FollowUpType.OTHER -> R.string.followup_type_other
}

val FollowUpType.icon: ImageVector
    get() = when (this) {
        FollowUpType.CALL -> Icons.Rounded.Call
        FollowUpType.WHATSAPP -> Icons.AutoMirrored.Rounded.Chat
        FollowUpType.EMAIL -> Icons.Rounded.Email
        FollowUpType.MEETING -> Icons.Rounded.Groups
        FollowUpType.QUOTATION -> Icons.Rounded.RequestQuote
        FollowUpType.PAYMENT -> Icons.Rounded.Payments
        FollowUpType.MESSAGE -> Icons.Rounded.Sms
        FollowUpType.OTHER -> Icons.Rounded.TaskAlt
    }

val FollowUpType.tone: Tone
    @Composable @ReadOnlyComposable get() = when (this) {
        FollowUpType.CALL -> MaterialTheme.vcColors.blue
        FollowUpType.WHATSAPP -> MaterialTheme.vcColors.mint
        FollowUpType.EMAIL -> MaterialTheme.vcColors.lavender
        FollowUpType.MEETING -> MaterialTheme.vcColors.orange
        FollowUpType.QUOTATION -> MaterialTheme.vcColors.navy
        FollowUpType.PAYMENT -> MaterialTheme.vcColors.mint
        FollowUpType.MESSAGE -> MaterialTheme.vcColors.blue
        FollowUpType.OTHER -> MaterialTheme.vcColors.rose
    }

@StringRes
fun FollowUpStatus.labelRes(): Int = when (this) {
    FollowUpStatus.PENDING -> R.string.export_status_pending
    FollowUpStatus.RESCHEDULED -> R.string.followup_status_rescheduled
    FollowUpStatus.COMPLETED -> R.string.followup_completed
    FollowUpStatus.CANCELLED -> R.string.followup_status_cancelled
}

@StringRes
fun FollowUpBucket.labelRes(): Int = when (this) {
    FollowUpBucket.OVERDUE -> R.string.followup_overdue
    FollowUpBucket.TODAY -> R.string.followup_today
    FollowUpBucket.UPCOMING -> R.string.followup_upcoming
    FollowUpBucket.COMPLETED -> R.string.followup_completed
}

@StringRes
fun reminderOffsetLabelRes(minutes: Int): Int = when (minutes) {
    0 -> R.string.reminder_at_time
    15 -> R.string.reminder_15_min
    60 -> R.string.reminder_1_hour
    1440 -> R.string.reminder_1_day
    else -> R.string.reminder_at_time
}
