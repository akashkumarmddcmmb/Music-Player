package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Classification for in-app notifications and status banners across PulsePlay.
 *
 * - [TEMPORARY_INFO], [TEMPORARY_SUCCESS], and [TEMPORARY_WARNING] auto-dismiss after
 *   [InAppNotificationManager.DEFAULT_AUTO_DISMISS_DURATION_MS] (~3.5 seconds) without requiring
 *   manual user interaction.
 * - [PERSISTENT_ERROR] remains visible until resolved or explicitly dismissed by the user.
 */
enum class InAppNotificationType {
    TEMPORARY_INFO,
    TEMPORARY_SUCCESS,
    TEMPORARY_WARNING,
    PERSISTENT_ERROR
}

/**
 * Immutable representation of an in-app notification / banner item.
 *
 * Each emission receives a unique monotonically increasing [id] so displaying the exact same
 * [message] text again triggers a fresh state emission and restarts the auto-dismiss timer.
 */
data class InAppNotification(
    val id: Long = nextNotificationId(),
    val message: String,
    val type: InAppNotificationType = InAppNotificationManager.classifyMessage(message),
    val autoDismiss: Boolean = (type != InAppNotificationType.PERSISTENT_ERROR),
    val durationMs: Long = InAppNotificationManager.DEFAULT_AUTO_DISMISS_DURATION_MS
) {
    companion object {
        private val idGenerator = AtomicLong(1L)
        fun nextNotificationId(): Long = idGenerator.getAndIncrement()
    }
}

/**
 * Shared, lifecycle-aware manager for in-app banners and temporary status notifications across
 * Settings, GitHub Update, Google Play Update, Music Player, Video Player, Studio Editor, and Library.
 *
 * Does NOT touch Android system notifications or MediaSession foreground playback notifications.
 */
class InAppNotificationManager(
    private val scope: CoroutineScope,
    val defaultAutoDismissDurationMs: Long = DEFAULT_AUTO_DISMISS_DURATION_MS
) {
    companion object {
        /**
         * Default auto-dismiss window (3.5 seconds, within the 3–4 second target window) for all
         * temporary, non-critical in-app notifications.
         */
        const val DEFAULT_AUTO_DISMISS_DURATION_MS: Long = 3_500L

        /**
         * Classifies an in-app message into temporary (auto-dismissing) vs persistent (requires
         * user action or explicit dismissal).
         */
        fun classifyMessage(message: String): InAppNotificationType {
            val lower = message.lowercase()
            return when {
                // Important, persistent errors, security blocks, or permission prompts requiring user action
                lower.contains("blocked by updatesourcepolicy") ||
                    lower.contains("incompatible signing certificate") ||
                    lower.contains("sha-256 checksum mismatch") ||
                    lower.contains("package name mismatch") ||
                    lower.contains("downgrade rejected") ||
                    lower.contains("permission required") ||
                    lower.contains("grant 'install unknown apps'") ||
                    lower.contains("uri permission was revoked") ||
                    lower.contains("permanently denied") -> InAppNotificationType.PERSISTENT_ERROR

                // Temporary warnings (including GitHub repository-not-configured warning)
                lower.contains("not configured") ||
                    lower.contains("configuration required") ||
                    lower.contains("warning") ||
                    lower.contains("cancelled") ||
                    lower.contains("unavailable") ||
                    lower.contains("not supported") ||
                    lower.contains("unable to") -> InAppNotificationType.TEMPORARY_WARNING

                // Temporary success & save confirmations
                lower.contains("saved") ||
                    lower.contains("up to date") ||
                    lower.contains("verified") ||
                    lower.contains("created") ||
                    lower.contains("added") ||
                    lower.contains("scanned") ||
                    lower.contains("imported") ||
                    lower.contains("loaded") ||
                    lower.contains("cleared") ||
                    lower.contains("reset to defaults") ||
                    lower.contains("copied") -> InAppNotificationType.TEMPORARY_SUCCESS

                else -> InAppNotificationType.TEMPORARY_INFO
            }
        }
    }

    private val _currentNotification = MutableStateFlow<InAppNotification?>(null)
    val currentNotification: StateFlow<InAppNotification?> = _currentNotification.asStateFlow()

    val currentMessage: StateFlow<String?> = _currentNotification
        .map { it?.message }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private var autoDismissJob: Job? = null

    /**
     * Posts an in-app notification.
     * - Cancels any pending dismissal job from a previous notification.
     * - Assigns a fresh [InAppNotification.id] so repeating the same [message] restarts its timer.
     * - Automatically dismisses temporary notifications after [durationMs] (~3.5s) while keeping
     *   persistent errors visible until resolved or explicitly dismissed.
     */
    fun show(
        message: String,
        type: InAppNotificationType = classifyMessage(message),
        autoDismiss: Boolean = (type != InAppNotificationType.PERSISTENT_ERROR),
        durationMs: Long = defaultAutoDismissDurationMs
    ): InAppNotification {
        autoDismissJob?.cancel()
        autoDismissJob = null

        val notification = InAppNotification(
            id = InAppNotification.nextNotificationId(),
            message = message,
            type = type,
            autoDismiss = autoDismiss,
            durationMs = durationMs
        )
        _currentNotification.value = notification

        if (autoDismiss && durationMs > 0L) {
            val targetId = notification.id
            autoDismissJob = scope.launch {
                delay(durationMs)
                if (_currentNotification.value?.id == targetId) {
                    _currentNotification.value = null
                }
            }
        }
        return notification
    }

    /**
     * Immediately dismisses the active in-app notification and cancels any pending auto-dismiss job.
     */
    fun dismiss() {
        autoDismissJob?.cancel()
        autoDismissJob = null
        _currentNotification.value = null
    }

    /**
     * Dismisses the active notification only if it is a temporary (auto-dismissing) message,
     * preserving any persistent error banner.
     */
    fun dismissIfTemporary() {
        val current = _currentNotification.value ?: return
        if (current.autoDismiss) {
            dismiss()
        }
    }

    /**
     * Cancels any pending coroutine timer when the owning ViewModel or screen is cleared.
     */
    fun cancelPendingJobs() {
        autoDismissJob?.cancel()
        autoDismissJob = null
    }
}

/**
 * Shared, lifecycle-aware Composable banner for displaying [InAppNotification] across all screens.
 *
 * Ensures temporary messages automatically disappear after [InAppNotification.durationMs] even if
 * rendered standalone in a screen or test, restarts the timer when the same message is re-emitted
 * (via [InAppNotification.id]), and cleans up on disposal.
 */
@Composable
fun InAppNotificationBanner(
    notification: InAppNotification?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(notification?.id, notification?.autoDismiss, notification?.durationMs) {
        if (notification != null && notification.autoDismiss && notification.durationMs > 0L) {
            delay(notification.durationMs)
            onDismiss()
        }
    }

    DisposableEffect(notification?.id) {
        onDispose {
            // Coroutine inside LaunchedEffect is automatically cancelled when notification.id changes or screen disposes
        }
    }

    if (notification != null) {
        val containerColor = when (notification.type) {
            InAppNotificationType.PERSISTENT_ERROR -> MaterialTheme.colorScheme.errorContainer
            InAppNotificationType.TEMPORARY_WARNING -> MaterialTheme.colorScheme.tertiaryContainer
            InAppNotificationType.TEMPORARY_SUCCESS -> MaterialTheme.colorScheme.secondaryContainer
            InAppNotificationType.TEMPORARY_INFO -> MaterialTheme.colorScheme.secondaryContainer
        }
        val contentColor = when (notification.type) {
            InAppNotificationType.PERSISTENT_ERROR -> MaterialTheme.colorScheme.onErrorContainer
            InAppNotificationType.TEMPORARY_WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
            InAppNotificationType.TEMPORARY_SUCCESS -> MaterialTheme.colorScheme.onSecondaryContainer
            InAppNotificationType.TEMPORARY_INFO -> MaterialTheme.colorScheme.onSecondaryContainer
        }
        val leadingIcon = when (notification.type) {
            InAppNotificationType.PERSISTENT_ERROR -> Icons.Default.ErrorOutline
            InAppNotificationType.TEMPORARY_WARNING -> Icons.Default.Warning
            InAppNotificationType.TEMPORARY_SUCCESS -> Icons.Default.CheckCircle
            InAppNotificationType.TEMPORARY_INFO -> Icons.Default.Info
        }

        Surface(
            color = containerColor,
            shape = RoundedCornerShape(12.dp),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("status_feedback_banner")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("status_feedback_banner_text")
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("status_feedback_banner_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss message",
                        tint = contentColor
                    )
                }
            }
        }
    }
}
