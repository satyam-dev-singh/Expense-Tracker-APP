package com.left.app.core.domain

import com.left.app.core.utils.Money

/** Pure Phase 7 notification decision rules. Android delivery stays outside domain. */
data class NotificationPlan(
    val shouldNotify: Boolean,
    val title: String,
    val body: String,
)

fun budgetNotificationPlan(
    usagePercent: Double?,
    budgetRemaining: Money?,
): NotificationPlan {
    if (usagePercent == null || budgetRemaining == null) return NotificationPlan(false, "", "")
    return when (evaluateBudgetStatus(usagePercent)) {
        BudgetStatus.ON_TRACK -> NotificationPlan(false, "", "")
        BudgetStatus.WARNING -> NotificationPlan(
            shouldNotify = true,
            title = "Budget warning",
            body = "You’ve used $usagePercent% of this month’s budget.",
        )
        BudgetStatus.EXCEEDED -> NotificationPlan(
            shouldNotify = true,
            title = "Budget exceeded",
            body = "You’re over budget by ${(-budgetRemaining).minorUnits} minor units.",
        )
    }
}

fun monthlySummaryPlan(
    moneyLeft: Money,
    expenses: Money,
    recurring: Money,
): NotificationPlan = NotificationPlan(
    shouldNotify = true,
    title = "Monthly summary",
    body = "Left ${moneyLeft.minorUnits} · spent ${expenses.minorUnits} · recurring ${recurring.minorUnits}",
)
