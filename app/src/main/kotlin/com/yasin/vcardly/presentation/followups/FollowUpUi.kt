package com.yasin.vcardly.presentation.followups

import androidx.annotation.StringRes
import com.yasin.vcardly.R
import com.yasin.vcardly.domain.model.FollowUpBucket
import com.yasin.vcardly.domain.model.FollowUpType

@StringRes
fun FollowUpType.labelRes(): Int = when (this) {
    FollowUpType.CALL -> R.string.followup_type_call
    FollowUpType.EMAIL -> R.string.followup_type_email
    FollowUpType.MEETING -> R.string.followup_type_meeting
    FollowUpType.MESSAGE -> R.string.followup_type_message
    FollowUpType.OTHER -> R.string.followup_type_other
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
