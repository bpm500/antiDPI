package com.antidpi.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.antidpi.app.BuildConfig
import com.antidpi.app.R
import com.antidpi.app.core.ReleaseInfo
import com.antidpi.app.model.AppSettings
import com.antidpi.app.model.ConfigEntry
import com.antidpi.app.ui.UpdateUiState
import com.antidpi.app.ui.theme.AntiDpiColors
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(
    settings: AppSettings,
    isIgnoringBattery: Boolean,
    updateState: UpdateUiState,
    onParallelSlotsChange: (Int) -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenAbout: () -> Unit,
    onEnableBackground: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = AntiDpiColors.Surface,
            border = BorderStroke(1.dp, AntiDpiColors.Border)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(14.dp))

                // 1. Parallel test slots (top)
                Text(
                    text = "${stringResource(R.string.settings_parallel_slots)}: ${settings.parallelSlots}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = settings.parallelSlots.toFloat(),
                    onValueChange = { onParallelSlotsChange(it.roundToInt()) },
                    valueRange = 1f..6f,
                    steps = 4,
                    colors = SliderDefaults.colors(
                        thumbColor = AntiDpiColors.TextPrimary,
                        activeTrackColor = AntiDpiColors.TextPrimary,
                        inactiveTrackColor = AntiDpiColors.SurfaceElevated
                    )
                )

                HorizontalDivider(color = AntiDpiColors.Border, modifier = Modifier.padding(vertical = 8.dp))

                // 2. Check update
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_check_update),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        val updateSubtitle = when (updateState) {
                            UpdateUiState.Idle -> stringResource(R.string.update_idle)
                            UpdateUiState.Checking -> stringResource(R.string.update_checking)
                            is UpdateUiState.UpToDate -> stringResource(R.string.update_up_to_date, updateState.version)
                            is UpdateUiState.Available -> stringResource(R.string.update_available_short, updateState.release.tag)
                            is UpdateUiState.Error -> stringResource(R.string.update_error, updateState.message)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = updateSubtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = AntiDpiColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                    OutlinedButton(
                        onClick = onCheckUpdate,
                        enabled = updateState !is UpdateUiState.Checking,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, AntiDpiColors.Border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AntiDpiColors.TextPrimary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "check-update",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                HorizontalDivider(color = AntiDpiColors.Border, modifier = Modifier.padding(vertical = 8.dp))

                // 3. About
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAbout() },
                    shape = RoundedCornerShape(6.dp),
                    color = AntiDpiColors.SurfaceElevated,
                    border = BorderStroke(1.dp, AntiDpiColors.Border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_about),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "https://github.com/bpm500/antiDPI",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = AntiDpiColors.TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Text(
                            text = "↗",
                            color = AntiDpiColors.TextPrimary,
                            fontSize = 14.sp
                        )
                    }
                }

                HorizontalDivider(color = AntiDpiColors.Border, modifier = Modifier.padding(vertical = 8.dp))

                // 4. Background execution
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_background_title),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(
                                if (isIgnoringBattery) R.string.settings_background_enabled
                                else R.string.settings_background_disabled
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = AntiDpiColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                    if (!isIgnoringBattery) {
                        Button(
                            onClick = onEnableBackground,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AntiDpiColors.InvertedBg,
                                contentColor = AntiDpiColors.InvertedText
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_enable),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                HorizontalDivider(color = AntiDpiColors.Border, modifier = Modifier.padding(vertical = 8.dp))

                // 5. Version 1.0 (bottom)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = AntiDpiColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, AntiDpiColors.Border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AntiDpiColors.TextPrimary)
                    ) {
                        Text(text = stringResource(R.string.action_close), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateAvailableDialog(
    release: ReleaseInfo,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = AntiDpiColors.Surface,
            border = BorderStroke(1.dp, AntiDpiColors.Border)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = stringResource(R.string.update_dialog_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.update_dialog_desc,
                        release.tag,
                        BuildConfig.VERSION_NAME
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AntiDpiColors.TextSecondary
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.action_later),
                            color = AntiDpiColors.TextSecondary
                        )
                    }
                    Button(
                        onClick = onDownload,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AntiDpiColors.InvertedBg,
                            contentColor = AntiDpiColors.InvertedText
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.action_download),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigActionDialog(
    config: ConfigEntry,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onCopyArgs: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf("menu") }
    var renameText by remember(config.name) { mutableStateOf(config.name) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = AntiDpiColors.Surface,
            border = BorderStroke(1.dp, AntiDpiColors.Border)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = config.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(12.dp))

                when (mode) {
                    "menu" -> {
                        OutlinedButton(
                            onClick = { mode = "args" },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, AntiDpiColors.Border),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AntiDpiColors.TextPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.action_show_args))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { mode = "rename" },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, AntiDpiColors.Border),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AntiDpiColors.TextPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.action_rename))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                onDelete()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, AntiDpiColors.Border),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AntiDpiColors.TextSecondary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.action_delete))
                        }
                    }

                    "args" -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            color = AntiDpiColors.Background,
                            border = BorderStroke(1.dp, AntiDpiColors.Border)
                        ) {
                            Text(
                                text = config.args,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { onCopyArgs(config.args) }) {
                                Text(stringResource(R.string.action_copy), color = AntiDpiColors.TextPrimary)
                            }
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.action_close), color = AntiDpiColors.TextSecondary)
                            }
                        }
                    }

                    "rename" -> {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AntiDpiColors.TextPrimary,
                                unfocusedBorderColor = AntiDpiColors.Border,
                                focusedTextColor = AntiDpiColors.TextPrimary,
                                unfocusedTextColor = AntiDpiColors.TextBody,
                                cursorColor = AntiDpiColors.TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.action_cancel), color = AntiDpiColors.TextSecondary)
                            }
                            Button(
                                onClick = {
                                    onRename(renameText)
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AntiDpiColors.InvertedBg,
                                    contentColor = AntiDpiColors.InvertedText
                                )
                            ) {
                                Text(stringResource(R.string.action_save))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticsDialog(
    reportText: String,
    onRefresh: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = AntiDpiColors.Surface,
            border = BorderStroke(1.dp, AntiDpiColors.Border)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.diagnostics_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    TextButton(
                        onClick = onRefresh,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.tg_refresh),
                            color = AntiDpiColors.TextPrimary,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = 380.dp)
                        .background(AntiDpiColors.Background, RoundedCornerShape(6.dp))
                        .border(1.dp, AntiDpiColors.Border, RoundedCornerShape(6.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        text = reportText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = AntiDpiColors.TextBody,
                            fontSize = 11.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopy,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, AntiDpiColors.Border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AntiDpiColors.TextPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = stringResource(R.string.action_copy), fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onExport,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, AntiDpiColors.Border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AntiDpiColors.TextPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = stringResource(R.string.diagnostics_export), fontSize = 12.sp)
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close), color = AntiDpiColors.TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
