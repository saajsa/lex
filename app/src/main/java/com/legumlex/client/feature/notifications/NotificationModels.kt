package com.legumlex.client.feature.notifications

import java.time.LocalDateTime

/** Severity tiers — shared with the Home action queue so a "critical" looks
 *  identical wherever it surfaces. Drives the leading stripe + tinted tile. */
enum class NotifSeverity { CRITICAL, WARNING, INFO, POSITIVE }

/** Notification kind — selects the leading icon and the deep-link target. */
enum class NotifType {
    HEARING, INVOICE, PAYMENT, AHC, DOCREQUEST, SIGNATURE,
    APPOINTMENT, TASK, PROJECT, MESSAGE, DOCUMENT, CHAMBER, GENERAL,
    /** An enquiry or Agreement Check whose next step is the client's (the portal drawer's "Requires Your Attention"). */
    ATTENTION
}

/** One presentation-ready notification (shaped by the customers_api backend). */
data class AppNotification(
    val id: String,
    val type: NotifType,
    val severity: NotifSeverity,
    val title: String,
    val subtitle: String,
    val cta: String,
    val link: String,
    val createdAt: LocalDateTime?,
    val timeLabel: String,
    val isRead: Boolean,
)

/** A time band (Today / Yesterday / …) with its rows + unread count. */
data class NotifBand(
    val title: String,
    val items: List<AppNotification>,
    val newCount: Int,
)

sealed interface NotificationsUiState {
    data object Loading : NotificationsUiState
    data class Ready(
        val bands: List<NotifBand>,
        val unreadCount: Int,
        val total: Int,
        val offline: Boolean = false,
    ) : NotificationsUiState
    data class Error(val message: String) : NotificationsUiState
}
