package com.example.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.ui.components.InAppNotificationManager
import kotlinx.coroutines.delay
import java.util.WeakHashMap

/**
 * Remembers the user's explicitly selected [UpdateDistributionChannel] scoped to the current
 * host lifecycle owner so closing and reopening the Settings screen restores the previously
 * selected provider even when invoked standalone without external ViewModel wiring.
 */
internal object UpdateProviderSelectionSessionStore {
    private val selectionByOwner = WeakHashMap<Any, UpdateDistributionChannel>()

    @Synchronized
    fun getSavedChannel(ownerKey: Any): UpdateDistributionChannel? = selectionByOwner[ownerKey]

    @Synchronized
    fun saveChannel(ownerKey: Any, channel: UpdateDistributionChannel) {
        selectionByOwner[ownerKey] = channel
    }

    @Synchronized
    fun clearForTesting() {
        selectionByOwner.clear()
    }
}

/**
 * Dual-Distribution Update Settings UI (`UpdateSettingsScreen`) supporting both:
 * 1. GitHub Releases (HTTPS release metadata, SHA-256 verification, signing certificate continuity,
 *    and FileProvider system package installer).
 * 2. Google Play Store In-App Updates (official Play In-App Updates API with Flexible background
 *    download + install confirmation, Immediate full-screen update flow, resume-on-restart handling,
 *    and official Play Store listing deep link).
 *
 * Uses a single source-of-truth selection state and strict conditional rendering so only the
 * currently selected update provider's panel and controls are visible at any time.
 * Temporary, non-critical status messages (`UpToDate`, `ConfigurationRequired`, `NoUpdateAvailable`,
 * etc.) automatically dismiss back to `Idle` after [autoDismissDurationMs] (~3.5 seconds).
 */
@Composable
fun UpdateSettingsScreen(
    installedVersionName: String = BuildConfig.VERSION_NAME,
    installedVersionCode: Long = BuildConfig.VERSION_CODE.toLong(),
    githubRepositorySlug: String,
    includePrereleaseUpdates: Boolean,
    updateState: UpdateCheckState,
    playStoreState: PlayStoreUpdateState = PlayStoreUpdateState.Idle,
    sourceEvaluation: UpdateSourceEvaluation? = null,
    activeChannel: UpdateDistributionChannel = UpdateDistributionChannel.GITHUB_RELEASES,
    policyBlockMessage: String? = null,
    autoDismissDurationMs: Long = InAppNotificationManager.DEFAULT_AUTO_DISMISS_DURATION_MS,
    onSelectChannel: (UpdateDistributionChannel) -> Unit = {},
    onSetGitHubRepositorySlug: (String) -> Unit,
    onSetIncludePrereleaseUpdates: (Boolean) -> Unit,
    onCheckForUpdatesClick: () -> Unit,
    onDownloadUpdateClick: (GitHubReleaseInfo) -> Unit,
    onCancelUpdateDownloadClick: () -> Unit,
    onInstallVerifiedUpdateClick: () -> Unit,
    onCheckPlayStoreUpdatesClick: () -> Unit = {},
    onStartPlayFlexibleUpdateClick: () -> Unit = {},
    onStartPlayImmediateUpdateClick: () -> Unit = {},
    onCompletePlayFlexibleUpdateClick: () -> Unit = {},
    onOpenPlayStoreListingClick: () -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val resolvedEvaluation = remember(sourceEvaluation) {
        sourceEvaluation ?: UpdateSourcePolicy.evaluateSignals(
            InstallSourceSignals(
                installingPackageName = null,
                initiatingPackageName = null,
                originatingPackageName = null,
                installedSigningCertSha256Set = emptySet(),
                isPlayStoreAppInstalledOnDevice = false
            )
        )
    }

    // Single source-of-truth selected provider state:
    // - Restores any previously selected provider in this session when Settings is reopened.
    // - Synchronizes with external `activeChannel` state updates from UpdateManager / ViewModel.
    var selectedChannel by rememberSaveable {
        val restored = UpdateProviderSelectionSessionStore.getSavedChannel(lifecycleOwner)
        mutableStateOf(restored ?: activeChannel)
    }

    var lastExternalActiveChannel by remember { mutableStateOf(activeChannel) }
    LaunchedEffect(activeChannel) {
        if (activeChannel != lastExternalActiveChannel) {
            lastExternalActiveChannel = activeChannel
            selectedChannel = activeChannel
            UpdateProviderSelectionSessionStore.saveChannel(lifecycleOwner, activeChannel)
        }
    }

    val selectProviderChannel: (UpdateDistributionChannel) -> Unit = { targetChannel ->
        if (selectedChannel != targetChannel) {
            selectedChannel = targetChannel
            UpdateProviderSelectionSessionStore.saveChannel(lifecycleOwner, targetChannel)
            onSelectChannel(targetChannel)
        } else {
            UpdateProviderSelectionSessionStore.saveChannel(lifecycleOwner, targetChannel)
        }
    }

    var repoSlugDraft by remember(githubRepositorySlug) {
        mutableStateOf(githubRepositorySlug)
    }
    var showMigrationGuide by remember { mutableStateOf(false) }

    // Lifecycle-aware auto-dismissal for temporary update check states (~3.5s)
    var gitHubTriggerTick by remember { mutableStateOf(0L) }
    var autoDismissedGitHubState by remember { mutableStateOf<UpdateCheckState?>(null) }
    LaunchedEffect(updateState, gitHubTriggerTick, autoDismissDurationMs) {
        autoDismissedGitHubState = null
        if (updateState.isTemporaryNotificationState && autoDismissDurationMs > 0L) {
            delay(autoDismissDurationMs)
            autoDismissedGitHubState = updateState
        }
    }
    val displayedGitHubState = if (
        autoDismissedGitHubState == updateState && updateState.isTemporaryNotificationState
    ) {
        UpdateCheckState.Idle
    } else {
        updateState
    }

    var playTriggerTick by remember { mutableStateOf(0L) }
    var autoDismissedPlayState by remember { mutableStateOf<PlayStoreUpdateState?>(null) }
    LaunchedEffect(playStoreState, playTriggerTick, autoDismissDurationMs) {
        autoDismissedPlayState = null
        if (playStoreState.isTemporaryNotificationState && autoDismissDurationMs > 0L) {
            delay(autoDismissDurationMs)
            autoDismissedPlayState = playStoreState
        }
    }
    val displayedPlayStoreState = if (
        autoDismissedPlayState == playStoreState && playStoreState.isTemporaryNotificationState
    ) {
        PlayStoreUpdateState.Idle
    } else {
        playStoreState
    }

    val isPlayInstalled = resolvedEvaluation.detectedChannel == InstallChannel.GOOGLE_PLAY ||
        resolvedEvaluation.blockGitHubApkOverwriteOfPlayInstall

    val isGitHubSelected = selectedChannel == UpdateDistributionChannel.GITHUB_RELEASES
    val isPlaySelected = selectedChannel == UpdateDistributionChannel.GOOGLE_PLAY

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("update_settings_screen_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "App Updates (GitHub & Google Play)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Installed Version: v$installedVersionName (versionCode $installedVersionCode)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("installed_version_label")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Installation Source & Signing Fingerprint Badge
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("install_source_badge"),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Install Source: ${resolvedEvaluation.detectedChannel.label} • Recommended: ${resolvedEvaluation.recommendedChannel.label}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Signals: ${resolvedEvaluation.installerPackageSummary} • Signing Key: ${resolvedEvaluation.signingCertificateShortFingerprint}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = resolvedEvaluation.policyExplanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Update Source Provider",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Provider Selection Buttons (Single Source of Truth)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = isGitHubSelected,
                    onClick = { selectProviderChannel(UpdateDistributionChannel.GITHUB_RELEASES) },
                    label = {
                        Text(
                            text = "GitHub Releases",
                            fontWeight = if (isGitHubSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isGitHubSelected) Icons.Default.Check else Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("update_channel_chip_github")
                )
                FilterChip(
                    selected = isPlaySelected,
                    onClick = { selectProviderChannel(UpdateDistributionChannel.GOOGLE_PLAY) },
                    label = {
                        Text(
                            text = "Google Play Store",
                            fontWeight = if (isPlaySelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isPlaySelected) Icons.Default.Check else Icons.Default.Shop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("update_channel_chip_play")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Exclusive Conditional Rendering: Display ONLY the selected provider's panel and controls
            when (selectedChannel) {
                UpdateDistributionChannel.GITHUB_RELEASES -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_update_section"),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "GitHub Releases Distribution",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Checks GitHub Releases over HTTPS, verifies SHA-256 checksum & APK package signature continuity, and launches the Android system package installer via FileProvider.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (!policyBlockMessage.isNullOrBlank() || isPlayInstalled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("play_install_blocks_github_banner"),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = policyBlockMessage
                                            ?: "Google Play installation detected: GitHub APK installation is disabled by UpdateSourcePolicy so a Play Store installation is never overwritten by an external APK.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = repoSlugDraft,
                                onValueChange = { repoSlugDraft = it },
                                label = { Text("GitHub Repository (owner/repo)") },
                                placeholder = {
                                    Text(
                                        if (AppUpdateManager.DEFAULT_GITHUB_REPOSITORY.isNotBlank()) {
                                            AppUpdateManager.DEFAULT_GITHUB_REPOSITORY
                                        } else {
                                            "e.g. your-org/pulseplay"
                                        }
                                    )
                                },
                                singleLine = true,
                                enabled = !isPlayInstalled,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("github_repo_slug_input")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Include Pre-release Builds",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Switch(
                                    checked = includePrereleaseUpdates,
                                    onCheckedChange = onSetIncludePrereleaseUpdates,
                                    enabled = !isPlayInstalled,
                                    modifier = Modifier.testTag("include_prerelease_updates_switch")
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        autoDismissedGitHubState = null
                                        gitHubTriggerTick++
                                        onSetGitHubRepositorySlug(repoSlugDraft)
                                    },
                                    enabled = !isPlayInstalled,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("save_github_repo_slug_button")
                                ) {
                                    Text("Save Repository", maxLines = 1)
                                }
                                Button(
                                    onClick = {
                                        autoDismissedGitHubState = null
                                        gitHubTriggerTick++
                                        onCheckForUpdatesClick()
                                    },
                                    enabled = !isPlayInstalled &&
                                        displayedGitHubState !is UpdateCheckState.Checking &&
                                        displayedGitHubState !is UpdateCheckState.Downloading,
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .testTag("check_for_updates_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (displayedGitHubState is UpdateCheckState.Checking) {
                                            "Checking..."
                                        } else {
                                            "Check for Updates"
                                        },
                                        maxLines = 1
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            GitHubUpdateStatePanel(
                                updateState = displayedGitHubState,
                                isPlayInstalled = isPlayInstalled,
                                onDownloadUpdateClick = onDownloadUpdateClick,
                                onCancelUpdateDownloadClick = onCancelUpdateDownloadClick,
                                onInstallVerifiedUpdateClick = onInstallVerifiedUpdateClick
                            )
                        }
                    }
                }

                UpdateDistributionChannel.GOOGLE_PLAY -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("play_store_update_section"),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Shop,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Google Play In-App Updates (Flexible & Immediate)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Uses the official Google Play In-App Updates API when distributed via Google Play. Supports background Flexible updates and full-screen Immediate updates while preserving local database and settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        autoDismissedPlayState = null
                                        playTriggerTick++
                                        onCheckPlayStoreUpdatesClick()
                                    },
                                    enabled = displayedPlayStoreState !is PlayStoreUpdateState.Checking &&
                                        displayedPlayStoreState !is PlayStoreUpdateState.Installing,
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .testTag("check_play_store_updates_button")
                                ) {
                                    Text(
                                        text = if (displayedPlayStoreState is PlayStoreUpdateState.Checking) {
                                            "Checking Play..."
                                        } else {
                                            "Check Play Store"
                                        },
                                        maxLines = 1
                                    )
                                }
                                OutlinedButton(
                                    onClick = onOpenPlayStoreListingClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("open_play_store_listing_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Play Listing", maxLines = 1)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            PlayStoreUpdateStatePanel(
                                playStoreState = displayedPlayStoreState,
                                onStartFlexibleClick = onStartPlayFlexibleUpdateClick,
                                onStartImmediateClick = onStartPlayImmediateUpdateClick,
                                onCompleteFlexibleClick = onCompletePlayFlexibleUpdateClick
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            TextButton(
                onClick = { showMigrationGuide = !showMigrationGuide },
                modifier = Modifier.testTag("toggle_signing_migration_guide_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (showMigrationGuide) {
                        "Hide Signing & Channel Migration Policy"
                    } else {
                        "View Signing & Channel Migration Policy"
                    }
                )
            }

            AnimatedVisibility(visible = showMigrationGuide) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag("signing_migration_guide_panel"),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = resolvedEvaluation.signingMigrationGuidance,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayStoreUpdateStatePanel(
    playStoreState: PlayStoreUpdateState,
    onStartFlexibleClick: () -> Unit,
    onStartImmediateClick: () -> Unit,
    onCompleteFlexibleClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("play_store_update_state_panel"),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            when (playStoreState) {
                is PlayStoreUpdateState.Idle -> {
                    Text(
                        text = "Play Store Status: Idle • Tap 'Check Play Store' to query Google Play In-App Updates API.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is PlayStoreUpdateState.Checking -> {
                    Text(
                        text = "Querying Google Play In-App Updates API...",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is PlayStoreUpdateState.NoUpdateAvailable -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "No Google Play Update Available",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = playStoreState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is PlayStoreUpdateState.UpdateAvailable -> {
                    Text(
                        text = "Google Play Update Available (versionCode ${playStoreState.availableVersionCode})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = playStoreState.statusMessage,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (playStoreState.isFlexibleAllowed) {
                            Button(
                                onClick = onStartFlexibleClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("play_flexible_update_button")
                            ) {
                                Text("Flexible Update", maxLines = 1)
                            }
                        }
                        if (playStoreState.isImmediateAllowed) {
                            OutlinedButton(
                                onClick = onStartImmediateClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("play_immediate_update_button")
                            ) {
                                Text("Immediate Update", maxLines = 1)
                            }
                        }
                    }
                }

                is PlayStoreUpdateState.FlexibleDownloading -> {
                    Text(
                        text = "Downloading Google Play Update (${playStoreState.progressPercent}%)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (playStoreState.progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("play_flexible_download_progress")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${playStoreState.bytesDownloaded / 1024} KB / ${(playStoreState.totalBytesToDownload / 1024).coerceAtLeast(1)} KB • You can continue using PulsePlay while downloading.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is PlayStoreUpdateState.DownloadedReadyToComplete -> {
                    Text(
                        text = "Google Play Update Ready to Install",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = playStoreState.promptMessage,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onCompleteFlexibleClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("play_complete_flexible_update_button")
                    ) {
                        Text("Complete Update & Restart")
                    }
                }

                is PlayStoreUpdateState.DeveloperTriggeredUpdateInProgress -> {
                    Text(
                        text = "Immediate Update In Progress",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = playStoreState.statusMessage,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onStartImmediateClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("play_resume_immediate_update_button")
                    ) {
                        Text("Resume Immediate Update")
                    }
                }

                is PlayStoreUpdateState.Installing -> {
                    Text(
                        text = "Installing Google Play Update (versionCode ${playStoreState.availableVersionCode})...",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is PlayStoreUpdateState.Installed -> {
                    Text(
                        text = playStoreState.statusMessage,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                is PlayStoreUpdateState.Cancelled -> {
                    Text(
                        text = playStoreState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is PlayStoreUpdateState.PlayStoreUnavailableOrUnpublished -> {
                    Text(
                        text = "Google Play In-App Updates Unavailable",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = playStoreState.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is PlayStoreUpdateState.UpdateFailed -> {
                    Text(
                        text = "Google Play Update Failed",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = playStoreState.reason,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
internal fun GitHubUpdateStatePanel(
    updateState: UpdateCheckState,
    isPlayInstalled: Boolean = false,
    onDownloadUpdateClick: (GitHubReleaseInfo) -> Unit,
    onCancelUpdateDownloadClick: () -> Unit,
    onInstallVerifiedUpdateClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("github_update_state_panel"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            when (updateState) {
                is UpdateCheckState.Idle -> {
                    Text(
                        text = if (isPlayInstalled) {
                            "Status: Disabled for Google Play installations • Switch to Google Play Store to check for updates."
                        } else if (AppUpdateManager.DEFAULT_GITHUB_REPOSITORY.isBlank()) {
                            "Status: Ready • Configure your GitHub 'owner/repo' above and tap Check for Updates."
                        } else {
                            "Status: Ready • Configured default repository: ${AppUpdateManager.DEFAULT_GITHUB_REPOSITORY}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is UpdateCheckState.ConfigurationRequired -> {
                    Text(
                        text = "Repository Configuration Required",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = updateState.message,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                is UpdateCheckState.Checking -> {
                    Text(
                        text = "Querying GitHub Releases API...",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is UpdateCheckState.UpToDate -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "You are running the latest version (v${updateState.installedVersionName}).",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!updateState.latestRemoteTag.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Latest GitHub Release tag checked: ${updateState.latestRemoteTag}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is UpdateCheckState.UpdateAvailable -> {
                    val rel = updateState.releaseInfo
                    Text(
                        text = "New Update Available: ${rel.releaseTitle} (${rel.tagName})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val sizeMb = if (rel.apkAsset.sizeBytes > 0) {
                        "%.2f MB".format(rel.apkAsset.sizeBytes / (1024.0 * 1024.0))
                    } else "APK"
                    Text(
                        text = "Asset: ${rel.apkAsset.name} ($sizeMb) • Installed: v${updateState.installedVersionName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Release Notes:\n${rel.releaseNotes.take(400)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onDownloadUpdateClick(rel) },
                        enabled = !isPlayInstalled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("download_github_update_button")
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download & Verify APK")
                    }
                }

                is UpdateCheckState.Downloading -> {
                    Text(
                        text = "Downloading ${updateState.releaseInfo.apkAsset.name} (${updateState.progressPercent}%)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (updateState.progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${updateState.downloadedBytes / 1024} KB / ${(updateState.totalBytes / 1024).coerceAtLeast(1)} KB",
                            style = MaterialTheme.typography.labelSmall
                        )
                        TextButton(
                            onClick = onCancelUpdateDownloadClick,
                            modifier = Modifier.testTag("cancel_github_update_button")
                        ) {
                            Text("Cancel")
                        }
                    }
                }

                is UpdateCheckState.Verifying -> {
                    Text(
                        text = "Verifying SHA-256 checksum & APK package signature...",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is UpdateCheckState.ReadyToInstall -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "APK Verified & Ready to Install (${updateState.releaseInfo.tagName})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Package: ${updateState.archivePackageName} • Version: ${updateState.archiveVersionName} (${updateState.archiveVersionCode})\nSHA-256: ${updateState.verifiedSha256.take(24)}...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onInstallVerifiedUpdateClick,
                        enabled = !isPlayInstalled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("install_github_update_button")
                    ) {
                        Text("Install Verified Update")
                    }
                }

                is UpdateCheckState.InstallationPending -> {
                    Text(
                        text = if (updateState.requiresUnknownSourcesPermission) {
                            "Permission Required to Install Update"
                        } else {
                            "Installer Launched"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = updateState.statusMessage,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onInstallVerifiedUpdateClick,
                        enabled = !isPlayInstalled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("install_github_update_button")
                    ) {
                        Text(
                            if (updateState.requiresUnknownSourcesPermission) {
                                "Grant Permission / Resume Install"
                            } else {
                                "Re-launch Package Installer"
                            }
                        )
                    }
                }

                is UpdateCheckState.VerificationFailed -> {
                    Text(
                        text = "APK Verification Failed",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = updateState.reason,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                is UpdateCheckState.DownloadFailed -> {
                    Text(
                        text = "Update Download Failed",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = updateState.reason,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                is UpdateCheckState.NetworkError -> {
                    Text(
                        text = "Update Check Failed",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = updateState.reason,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
