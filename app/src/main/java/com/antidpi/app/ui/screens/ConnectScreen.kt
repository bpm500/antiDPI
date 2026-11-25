package com.antidpi.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antidpi.app.R
import com.antidpi.app.model.ConfigEntry
import com.antidpi.app.model.VpnState
import com.antidpi.app.ui.theme.AntiDpiColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConnectScreen(
    configs: List<ConfigEntry>,
    selectedConfigName: String?,
    currentNetworkType: String?,
    vpnState: VpnState,
    showBgPrompt: Boolean,
    onPowerClick: () -> Unit,
    onSelectConfig: (String) -> Unit,
    onLongPressConfig: (ConfigEntry) -> Unit,
    onOpenSettings: () -> Unit,
    onEnableBackground: () -> Unit,
    onDismissBgPrompt: () -> Unit
) {
    val isConnected = vpnState is VpnState.Connected
    val isConnecting = vpnState is VpnState.Connecting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AntiDpiColors.Background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (showBgPrompt) {
            BackgroundPromptCard(
                onEnable = onEnableBackground,
                onDismiss = onDismissBgPrompt
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Top row with Settings gear
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = AntiDpiColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Power button + state
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val circleBg = if (isConnected) AntiDpiColors.InvertedBg else AntiDpiColors.Background
            val iconTint = if (isConnected) AntiDpiColors.InvertedText else AntiDpiColors.TextPrimary
            val borderColor = when {
                isConnected -> AntiDpiColors.TextPrimary
                isConnecting -> AntiDpiColors.TextBody
                else -> AntiDpiColors.TextPrimary
            }

            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .background(circleBg)
                    .border(width = 2.dp, color = borderColor, shape = CircleShape)
                    .clickable(enabled = !isConnecting) { onPowerClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Power",
                    tint = iconTint,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            val statusTitle = when (vpnState) {
                is VpnState.Connected -> stringResource(R.string.status_connected, vpnState.configName)
                is VpnState.Connecting -> stringResource(R.string.status_connecting, vpnState.configName)
                is VpnState.Error -> stringResource(R.string.status_error, vpnState.message)
                VpnState.Disconnected -> stringResource(R.string.status_disconnected)
            }

            Text(
                text = statusTitle,
                style = MaterialTheme.typography.titleMedium,
                color = if (isConnected) AntiDpiColors.TextPrimary else AntiDpiColors.TextBody,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = selectedConfigName ?: stringResource(R.string.no_config_selected),
                style = MaterialTheme.typography.bodyMedium,
                color = AntiDpiColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val selectedEntry = configs.firstOrNull { it.name == selectedConfigName }
            if (selectedEntry?.networkType != null &&
                currentNetworkType != null &&
                selectedEntry.networkType != currentNetworkType
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.network_mismatch_hint),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = AntiDpiColors.TextSecondary,
                        fontSize = 11.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.configs_title),
            style = MaterialTheme.typography.labelMedium,
            color = AntiDpiColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(configs, key = { it.name }) { cfg ->
                val selected = cfg.name == selectedConfigName
                val bg = if (selected) AntiDpiColors.SurfaceElevated else AntiDpiColors.Surface
                val border = if (selected) AntiDpiColors.TextPrimary else AntiDpiColors.Border

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .combinedClickable(
                            onClick = { onSelectConfig(cfg.name) },
                            onLongClick = { onLongPressConfig(cfg) }
                        ),
                    shape = RoundedCornerShape(6.dp),
                    color = bg,
                    border = BorderStroke(1.dp, border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) AntiDpiColors.TextPrimary
                                    else AntiDpiColors.Border
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = cfg.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) AntiDpiColors.TextPrimary else AntiDpiColors.TextBody
                            ),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (cfg.lastOk != null && cfg.lastTotal != null && cfg.lastTotal > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${cfg.lastOk}/${cfg.lastTotal}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (cfg.lastOk == cfg.lastTotal) {
                                        AntiDpiColors.TextPrimary
                                    } else {
                                        AntiDpiColors.TextSecondary
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundPromptCard(
    onEnable: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = AntiDpiColors.Surface,
        border = BorderStroke(1.dp, AntiDpiColors.Border)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = AntiDpiColors.TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.bg_prompt_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                    color = AntiDpiColors.TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.bg_prompt_desc),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = AntiDpiColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, AntiDpiColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AntiDpiColors.TextSecondary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(text = stringResource(R.string.action_later), fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onEnable,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AntiDpiColors.InvertedBg,
                        contentColor = AntiDpiColors.InvertedText
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_enable),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
