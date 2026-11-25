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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.LaunchedEffect
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
import com.antidpi.app.R
import com.antidpi.app.ui.theme.AntiDpiColors
import kotlin.math.roundToInt

@Composable
fun CreateScreen(
    targetDiscord: Boolean,
    targetYouTube: Boolean,
    numConfigsToFind: Int,
    isCreating: Boolean,
    logs: List<String>,
    manualError: String?,
    manualValidating: Boolean,
    onTargetDiscordChange: (Boolean) -> Unit,
    onTargetYouTubeChange: (Boolean) -> Unit,
    onNumConfigsChange: (Int) -> Unit,
    onStartCreate: () -> Unit,
    onStopCreate: () -> Unit,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onManualSave: (String, String) -> Unit,
    onClearManualError: () -> Unit
) {
    var manualExpanded by remember { mutableStateOf(false) }
    var manualName by remember { mutableStateOf("") }
    var manualArgs by remember { mutableStateOf("") }

    val logListState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            logListState.animateScrollToItem(logs.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AntiDpiColors.Background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.create_targets),
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(enabled = !isCreating) {
                    onTargetDiscordChange(!targetDiscord)
                }
            ) {
                Checkbox(
                    checked = targetDiscord,
                    onCheckedChange = if (!isCreating) onTargetDiscordChange else null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = AntiDpiColors.TextPrimary,
                        checkmarkColor = AntiDpiColors.InvertedText,
                        uncheckedColor = AntiDpiColors.TextSecondary
                    )
                )
                Text(
                    text = stringResource(R.string.create_target_discord),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(enabled = !isCreating) {
                    onTargetYouTubeChange(!targetYouTube)
                }
            ) {
                Checkbox(
                    checked = targetYouTube,
                    onCheckedChange = if (!isCreating) onTargetYouTubeChange else null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = AntiDpiColors.TextPrimary,
                        checkmarkColor = AntiDpiColors.InvertedText,
                        uncheckedColor = AntiDpiColors.TextSecondary
                    )
                )
                Text(
                    text = stringResource(R.string.create_target_youtube),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.create_num_configs, numConfigsToFind),
            style = MaterialTheme.typography.labelMedium
        )
        Slider(
            value = numConfigsToFind.toFloat(),
            onValueChange = { if (!isCreating) onNumConfigsChange(it.roundToInt()) },
            valueRange = 1f..10f,
            steps = 8,
            enabled = !isCreating,
            colors = SliderDefaults.colors(
                thumbColor = AntiDpiColors.TextPrimary,
                activeTrackColor = AntiDpiColors.TextPrimary,
                inactiveTrackColor = AntiDpiColors.SurfaceElevated
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onStartCreate,
                enabled = !isCreating,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AntiDpiColors.InvertedBg,
                    contentColor = AntiDpiColors.InvertedText,
                    disabledContainerColor = AntiDpiColors.SurfaceElevated,
                    disabledContentColor = AntiDpiColors.TextSecondary
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.create_start),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (isCreating) {
                OutlinedButton(
                    onClick = onStopCreate,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, AntiDpiColors.TextPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AntiDpiColors.TextPrimary),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.create_stop), fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Manual config creation section
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = AntiDpiColors.Surface,
            border = BorderStroke(1.dp, AntiDpiColors.Border)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { manualExpanded = !manualExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.create_manual_title),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (manualExpanded) "▲" else "▼",
                        color = AntiDpiColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }

                if (manualExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val tfColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AntiDpiColors.TextPrimary,
                        unfocusedBorderColor = AntiDpiColors.Border,
                        focusedTextColor = AntiDpiColors.TextPrimary,
                        unfocusedTextColor = AntiDpiColors.TextBody,
                        cursorColor = AntiDpiColors.TextPrimary
                    )
                    OutlinedTextField(
                        value = manualName,
                        onValueChange = {
                            manualName = it
                            onClearManualError()
                        },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.create_manual_name_hint),
                                color = AntiDpiColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        },
                        singleLine = true,
                        colors = tfColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = manualArgs,
                        onValueChange = {
                            manualArgs = it
                            onClearManualError()
                        },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.create_manual_args_hint),
                                color = AntiDpiColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        },
                        singleLine = false,
                        maxLines = 3,
                        colors = tfColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!manualError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = manualError,
                            color = AntiDpiColors.TextPrimary,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onManualSave(manualName, manualArgs) },
                        enabled = !manualValidating,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AntiDpiColors.InvertedBg,
                            contentColor = AntiDpiColors.InvertedText
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.create_manual_validate_save),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.create_log_title),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onCopyLogs,
                enabled = logs.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_copy),
                    color = AntiDpiColors.TextBody,
                    fontSize = 12.sp
                )
            }
            TextButton(
                onClick = onClearLogs,
                enabled = logs.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_clear),
                    color = AntiDpiColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(AntiDpiColors.Surface, RoundedCornerShape(6.dp))
                .border(1.dp, AntiDpiColors.Border, RoundedCornerShape(6.dp))
                .padding(10.dp)
        ) {
            LazyColumn(
                state = logListState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
