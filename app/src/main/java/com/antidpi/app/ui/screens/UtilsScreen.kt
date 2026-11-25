package com.antidpi.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antidpi.app.R
import com.antidpi.app.tgproxy.CfFailureReason
import com.antidpi.app.tgproxy.CfRoute
import com.antidpi.app.tgproxy.CfTransport
import com.antidpi.app.tgproxy.CfTrouble
import com.antidpi.app.tgproxy.TgProxyCore
import com.antidpi.app.tgproxy.TgProxyError
import com.antidpi.app.tgproxy.TgProxyRunState
import com.antidpi.app.tgproxy.TgProxyService
import com.antidpi.app.tgproxy.TgProxySettings
import com.antidpi.app.tgproxy.TgProxyStatus
import com.antidpi.app.tgproxy.TgWorkerSetup
import com.antidpi.app.tgproxy.TlsStrategies
import com.antidpi.app.tgproxy.cfTrouble
import com.antidpi.app.tgproxy.isLoopbackHost
import com.antidpi.app.tgproxy.runState
import com.antidpi.app.tgproxy.withWorkerDomain
import com.antidpi.app.ui.TgProxyViewModel
import com.antidpi.app.ui.TgSettingsDraft
import com.antidpi.app.ui.theme.AntiDpiColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
fun UtilsScreen(vm: TgProxyViewModel) {
    val context = LocalContext.current
    val status by vm.status.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val stalled by vm.stalled.collectAsStateWithLifecycle()
    val cfCheck by vm.cfCheck.collectAsStateWithLifecycle()

    var showLogs by rememberSaveable { mutableStateOf(false) }
    var showWorkerSetup by rememberSaveable { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }

    val runState = status.runState
    val isRunning = runState == TgProxyRunState.RUNNING
    val isActive = status.running

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AntiDpiColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header + Logs button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TG WS PROXY",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { showLogs = true },
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, AntiDpiColors.Border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AntiDpiColors.TextBody),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(text = stringResource(R.string.tg_logs), fontSize = 12.sp)
            }
        }

        // Status & Control Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = AntiDpiColors.Surface,
            border = BorderStroke(1.dp, if (isRunning) AntiDpiColors.TextPrimary else AntiDpiColors.Border)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) AntiDpiColors.TextPrimary else AntiDpiColors.TextSecondary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val statusLabel = when (runState) {
                        TgProxyRunState.STOPPED -> stringResource(R.string.tg_status_stopped)
                        TgProxyRunState.STARTING -> stringResource(R.string.tg_status_starting)
                        TgProxyRunState.RUNNING -> stringResource(R.string.tg_status_running)
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isRunning) AntiDpiColors.TextPrimary else AntiDpiColors.TextBody,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = vm.savedAddress(),
                        style = MaterialTheme.typography.bodySmall,
                        color = AntiDpiColors.TextSecondary
                    )
                }

                if (isRunning && saved.cfproxy) {
                    val way = stringResource(cfTransportLabel(saved.cfTransport))
                    Text(
                        text = if (saved.cfAuto) {
                            stringResource(R.string.tg_cf_line_auto, way)
                        } else {
                            stringResource(R.string.tg_cf_line_fixed, way)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AntiDpiColors.TextSecondary
                    )
                }

                if (isRunning) {
                    val trouble = cfTrouble(status.cfRecent, status.workerTrouble)
                    if (trouble != null) {
                        val reason = stringResource(cfReasonRes(trouble))
                        Text(
                            text = stringResource(
                                if (trouble.route == CfRoute.WORKER) R.string.tg_cf_trouble_worker
                                else R.string.tg_cf_trouble_cf,
                                reason
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = AntiDpiColors.TextPrimary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (isActive) TgProxyService.stop(context)
                            else TgProxyService.start(context)
                        },
                        shape = RoundedCornerShape(6.dp),
                        colors = if (isActive) {
                            ButtonDefaults.buttonColors(
                                containerColor = AntiDpiColors.SurfaceElevated,
                                contentColor = AntiDpiColors.TextPrimary
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = AntiDpiColors.InvertedBg,
                                contentColor = AntiDpiColors.InvertedText
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(if (isActive) R.string.tg_stop_proxy else R.string.tg_start_proxy),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = { TgProxyService.start(context, restart = true) },
                        enabled = isActive,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, AntiDpiColors.Border),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AntiDpiColors.TextPrimary,
                            disabledContentColor = AntiDpiColors.TextSecondary
                        )
                    ) {
                        Text(text = stringResource(R.string.tg_restart), fontSize = 13.sp)
                    }
                }

                if (isRunning) {
                    HorizontalDivider(color = AntiDpiColors.Border)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatChip(
                            label = "WS ${status.ws}",
                            active = status.ws > 0,
                            modifier = Modifier.weight(1f)
                        )
                        StatChip(
                            label = "CF ${status.cf}",
                            active = status.cf > 0,
                            modifier = Modifier.weight(1f)
                        )
                        StatChip(
                            label = "${stringResource(R.string.tg_stat_active)} ${status.active}",
                            active = status.active > 0,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = "↓ ${Formatter.formatShortFileSize(context, status.bytesDown)}  ·  ↑ ${Formatter.formatShortFileSize(context, status.bytesUp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AntiDpiColors.TextSecondary
                    )
                }
            }
        }

        // Error / Diagnostic Alerts
        status.error?.let { err ->
            TgAlertCard(
                title = stringResource(R.string.tg_error_title),
                body = err.readableMessage(context),
                actionLabel = err.suggestedPort?.takeIf { !status.running }?.let {
                    stringResource(R.string.tg_use_port, it)
                },
                onAction = {
                    err.suggestedPort?.let { p ->
                        vm.usePort(p)
                        TgProxyService.start(context)
                    }
                }
            )
        }

        if (isRunning && stalled) {
            TgAlertCard(
                title = stringResource(R.string.tg_stalled_title),
                body = stringResource(R.string.tg_stalled_body),
                actionLabel = stringResource(R.string.tg_restart),
                onAction = { TgProxyService.start(context, restart = true) }
            )
        }

        if (isRunning && saved.cfproxy && saved.cfAuto) {
            cfCheck?.let { chk ->
                if (chk.chosen == null) {
                    TgAlertCard(
                        title = stringResource(R.string.tg_cf_none_title),
                        body = stringResource(R.string.tg_cf_none_body)
                    )
                } else if (chk.cutOff) {
                    TgAlertCard(
                        title = stringResource(R.string.tg_cf_cutoff_title),
                        body = stringResource(R.string.tg_cf_cutoff_body)
                    )
                }
            }
        }

        // Telegram Connect Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val link = vm.link() ?: return@Button
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.tg_not_found, Toast.LENGTH_LONG).show()
                    }
                },
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AntiDpiColors.InvertedBg,
                    contentColor = AntiDpiColors.InvertedText
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.tg_open_telegram),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = {
                    val link = vm.link() ?: return@OutlinedButton
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm?.setPrimaryClip(ClipData.newPlainText("tg-proxy", link))
                    Toast.makeText(context, R.string.tg_link_copied, Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, AntiDpiColors.Border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AntiDpiColors.TextPrimary),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.tg_copy_link),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HorizontalDivider(color = AntiDpiColors.Border, modifier = Modifier.padding(vertical = 2.dp))

        // Settings Form
        TgSettingsSection(
            draft = draft,
            saved = saved,
            status = status,
            cfCheck = cfCheck,
            onChange = {
                vm.edit(it)
                feedback = null
            },
            onOpenWorkerSetup = { showWorkerSetup = true }
        )

        if (!feedback.isNullOrBlank()) {
            Text(
                text = feedback.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = AntiDpiColors.TextPrimary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    feedback = when (val res = vm.save()) {
                        TgProxyViewModel.SaveResult.Unchanged -> context.getString(R.string.tg_saved)
                        is TgProxyViewModel.SaveResult.Saved -> context.getString(
                            if (res.restarted) R.string.tg_saved_restarted else R.string.tg_saved
                        )
                        is TgProxyViewModel.SaveResult.Invalid -> context.getString(
                            R.string.tg_invalid_number,
                            context.getString(res.field)
                        )
                        is TgProxyViewModel.SaveResult.BadStrategy -> context.getString(
                            R.string.tg_error_bad_tls_strategy,
                            res.part
                        )
                    }
                },
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AntiDpiColors.InvertedBg,
                    contentColor = AntiDpiColors.InvertedText
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.tg_save_settings),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            OutlinedButton(
                onClick = {
                    vm.resetDefaults()
                    feedback = context.getString(R.string.tg_reset_done)
                },
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, AntiDpiColors.Border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AntiDpiColors.TextSecondary)
            ) {
                Text(text = stringResource(R.string.tg_reset_defaults), fontSize = 13.sp)
            }
        }

        if (status.coreVersion.isNotBlank()) {
            Text(
                text = "tg-ws-proxy core ${status.coreVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = AntiDpiColors.TextSecondary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
    }

    if (showLogs) {
        TgLogDialog(onDismiss = { showLogs = false })
    }

    if (showWorkerSetup) {
        TgWorkerSetupDialog(
            onDismiss = { showWorkerSetup = false },
            onCreated = { domain ->
                vm.edit(draft.edit { withWorkerDomain(domain) })
            }
        )
    }
}

@Composable
private fun StatChip(label: String, active: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = if (active) AntiDpiColors.SurfaceElevated else AntiDpiColors.Background,
        border = BorderStroke(1.dp, if (active) AntiDpiColors.TextPrimary else AntiDpiColors.Border)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (active) AntiDpiColors.TextPrimary else AntiDpiColors.TextSecondary,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TgAlertCard(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = AntiDpiColors.SurfaceElevated,
        border = BorderStroke(1.dp, AntiDpiColors.TextPrimary)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontSize = 13.sp)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = AntiDpiColors.TextSecondary
            )
            if (actionLabel != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onAction) {
                        Text(text = actionLabel, color = AntiDpiColors.TextPrimary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TgSettingsSection(
    draft: TgSettingsDraft,
    saved: TgProxySettings,
    status: TgProxyStatus,
    cfCheck: TgProxyCore.CfCheck?,
    onChange: (TgSettingsDraft) -> Unit,
    onOpenWorkerSetup: () -> Unit
) {
    val s = draft.settings
    fun edit(change: TgProxySettings.() -> TgProxySettings) = onChange(draft.edit(change))

    val tfColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AntiDpiColors.TextPrimary,
        unfocusedBorderColor = AntiDpiColors.Border,
        focusedTextColor = AntiDpiColors.TextPrimary,
        unfocusedTextColor = AntiDpiColors.TextBody,
        focusedLabelColor = AntiDpiColors.TextPrimary,
        unfocusedLabelColor = AntiDpiColors.TextSecondary,
        cursorColor = AntiDpiColors.TextPrimary
    )

    // Connection
    Text(text = stringResource(R.string.tg_section_connection), style = MaterialTheme.typography.labelMedium)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = s.host,
            onValueChange = { v -> edit { copy(host = v) } },
            label = { Text(stringResource(R.string.tg_host), fontSize = 11.sp) },
            singleLine = true,
            colors = tfColors,
            modifier = Modifier.weight(2f)
        )
        OutlinedTextField(
            value = draft.port,
            onValueChange = { onChange(draft.copy(port = it)) },
            label = { Text(stringResource(R.string.tg_port), fontSize = 11.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            colors = tfColors,
            modifier = Modifier.weight(1f)
        )
    }
    if (!isLoopbackHost(s.host)) {
        Text(
            text = stringResource(R.string.tg_host_exposed),
            style = MaterialTheme.typography.bodySmall,
            color = AntiDpiColors.TextPrimary
        )
    }

    var secretVisible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = s.secret,
        onValueChange = { v -> edit { copy(secret = v) } },
        label = { Text(stringResource(R.string.tg_secret), fontSize = 11.sp) },
        singleLine = true,
        visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
        colors = tfColors,
        modifier = Modifier.fillMaxWidth()
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { secretVisible = !secretVisible }) {
            Text(
                text = stringResource(if (secretVisible) R.string.tg_secret_hide else R.string.tg_secret_show),
                color = AntiDpiColors.TextBody,
                fontSize = 12.sp
            )
        }
        TextButton(onClick = { edit { copy(secret = TgProxySettings.newSecret()) } }) {
            Text(text = stringResource(R.string.tg_new_secret), color = AntiDpiColors.TextPrimary, fontSize = 12.sp)
        }
    }

    // DC -> IP & Cloudflare Route
    Text(text = stringResource(R.string.tg_section_route), style = MaterialTheme.typography.labelMedium)
    OutlinedTextField(
        value = draft.dcIp,
        onValueChange = { onChange(draft.copy(dcIp = it)) },
        label = { Text(stringResource(R.string.tg_dc_ip), fontSize = 11.sp) },
        minLines = 2,
        maxLines = 4,
        colors = tfColors,
        modifier = Modifier.fillMaxWidth()
    )

    TgToggle(s.cfproxy, stringResource(R.string.tg_cfproxy)) { v -> edit { copy(cfproxy = v) } }
    TgToggle(s.h2, stringResource(R.string.tg_h2)) { v -> edit { copy(h2 = v) } }
    TgToggle(s.cfUserDomainEnabled, stringResource(R.string.tg_cf_user_enabled)) { v ->
        edit { copy(cfUserDomainEnabled = v) }
    }
    if (s.cfUserDomainEnabled) {
        OutlinedTextField(
            value = s.cfUserDomains,
            onValueChange = { v -> edit { copy(cfUserDomains = v) } },
            label = { Text(stringResource(R.string.tg_cf_user_domains), fontSize = 11.sp) },
            singleLine = true,
            colors = tfColors,
            modifier = Modifier.fillMaxWidth()
        )
    }

    TgToggle(s.cfWorkerEnabled, stringResource(R.string.tg_cf_worker_enabled)) { v ->
        edit { copy(cfWorkerEnabled = v) }
    }
    if (s.cfWorkerEnabled) {
        OutlinedTextField(
            value = s.cfWorkerDomains,
            onValueChange = { v -> edit { copy(cfWorkerDomains = v) } },
            label = { Text(stringResource(R.string.tg_cf_worker_domains), fontSize = 11.sp) },
            placeholder = { Text("andmtproto.account.workers.dev", color = AntiDpiColors.TextSecondary, fontSize = 12.sp) },
            singleLine = true,
            colors = tfColors,
            modifier = Modifier.fillMaxWidth()
        )
    }
    OutlinedButton(
        onClick = onOpenWorkerSetup,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, AntiDpiColors.Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AntiDpiColors.TextPrimary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = stringResource(R.string.tg_worker_setup_open), fontSize = 12.sp)
    }

    if (s.cfproxy || s.cfUserDomainEnabled || s.cfWorkerEnabled) {
        TgCfTransportBlock(s, saved, status, cfCheck, ::edit, tfColors)
    }

    // Advanced / Masking
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { advancedOpen = !advancedOpen }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(if (advancedOpen) R.string.tg_advanced_hide else R.string.tg_advanced_show),
            style = MaterialTheme.typography.labelMedium,
            color = AntiDpiColors.TextBody,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (advancedOpen) "▲" else "▼",
            color = AntiDpiColors.TextSecondary,
            fontSize = 11.sp
        )
    }

    if (advancedOpen) {
        OutlinedTextField(
            value = s.fakeTlsDomain,
            onValueChange = { v -> edit { copy(fakeTlsDomain = v) } },
            label = { Text(stringResource(R.string.tg_fake_tls), fontSize = 11.sp) },
            singleLine = true,
            colors = tfColors,
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.poolSize,
                onValueChange = { onChange(draft.copy(poolSize = it)) },
                label = { Text(stringResource(R.string.tg_pool_size), fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                colors = tfColors,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = draft.bufKb,
                onValueChange = { onChange(draft.copy(bufKb = it)) },
                label = { Text(stringResource(R.string.tg_buf_kb), fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                colors = tfColors,
                modifier = Modifier.weight(1f)
            )
        }
        TgToggle(s.forceTestDc, stringResource(R.string.tg_force_test_dc)) { v -> edit { copy(forceTestDc = v) } }
        TgToggle(s.verbose, stringResource(R.string.tg_verbose)) { v -> edit { copy(verbose = v) } }
    }
}

@Composable
private fun TgCfTransportBlock(
    s: TgProxySettings,
    saved: TgProxySettings,
    status: TgProxyStatus,
    cfCheck: TgProxyCore.CfCheck?,
    edit: (TgProxySettings.() -> TgProxySettings) -> Unit,
    tfColors: androidx.compose.material3.TextFieldColors
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = AntiDpiColors.Surface,
        border = BorderStroke(1.dp, AntiDpiColors.Border)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = stringResource(R.string.tg_cf_transport), style = MaterialTheme.typography.labelMedium)

            Column(Modifier.selectableGroup()) {
                TgRadioRow(
                    title = stringResource(R.string.tg_cf_transport_auto),
                    selected = s.cfAuto,
                    onSelect = { if (!s.cfAuto) edit { withCfAuto() } }
                )
                if (s.cfAuto && status.running && saved.cfproxy) {
                    val nowWay = stringResource(cfTransportLabel(cfCheck?.chosen ?: saved.cfTransport))
                    val detail = when {
                        cfCheck == null -> stringResource(R.string.tg_cf_auto_checking)
                        cfCheck.chosen == null -> stringResource(R.string.tg_cf_auto_none)
                        else -> stringResource(
                            R.string.tg_cf_auto_now,
                            nowWay,
                            cfCheck.networkLabel,
                            DateFormat.getTimeFormat(context).format(Date(cfCheck.atMillis))
                        )
                    }
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = AntiDpiColors.TextSecondary,
                        modifier = Modifier.padding(start = 32.dp, bottom = 4.dp)
                    )
                }

                listOf(
                    CfTransport.BYPASS to R.string.tg_cf_transport_bypass,
                    CfTransport.TLS to R.string.tg_cf_transport_tls,
                    CfTransport.PLAIN to R.string.tg_cf_transport_plain
                ).forEach { (tr, labelRes) ->
                    TgRadioRow(
                        title = stringResource(labelRes),
                        selected = !s.cfAuto && s.cfTransport == tr,
                        onSelect = { edit { withCfTransport(tr) } }
                    )
                }
            }

            if (s.cfAuto || s.cfTransport == CfTransport.BYPASS) {
                val presetLabels = listOf(
                    R.string.tg_tls_strategy_auto,
                    R.string.tg_tls_strategy_default,
                    R.string.tg_tls_strategy_records_oob,
                    R.string.tg_tls_strategy_oob,
                    R.string.tg_tls_strategy_parent
                )
                val presetIdx = TlsStrategies.PRESETS.indexOf(s.tlsStrategy)
                var custom by rememberSaveable { mutableStateOf(presetIdx < 0) }
                var open by remember { mutableStateOf(false) }

                val currentLabel = if (custom || presetIdx < 0) {
                    stringResource(R.string.tg_tls_strategy_custom)
                } else {
                    stringResource(presetLabels[presetIdx])
                }

                Box {
                    OutlinedTextField(
                        value = currentLabel,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.tg_tls_strategy), fontSize = 11.sp) },
                        trailingIcon = { Text(if (open) "▴" else "▾", color = AntiDpiColors.TextSecondary) },
                        colors = tfColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(Modifier.matchParentSize().clickable { open = true })
                    DropdownMenu(
                        expanded = open,
                        onDismissRequest = { open = false },
                        modifier = Modifier.background(AntiDpiColors.SurfaceElevated)
                    ) {
                        TlsStrategies.PRESETS.forEachIndexed { idx, preset ->
                            DropdownMenuItem(
                                text = { Text(stringResource(presetLabels[idx]), color = AntiDpiColors.TextPrimary, fontSize = 13.sp) },
                                onClick = {
                                    open = false
                                    custom = false
                                    edit { copy(tlsStrategy = preset) }
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tg_tls_strategy_custom), color = AntiDpiColors.TextPrimary, fontSize = 13.sp) },
                            onClick = {
                                open = false
                                custom = true
                            }
                        )
                    }
                }

                if (custom || presetIdx < 0) {
                    val bad = remember(s.tlsStrategy) {
                        if (s.tlsStrategy.isBlank()) null else TgProxyCore.badTlsStrategyPart(s.tlsStrategy)
                    }
                    OutlinedTextField(
                        value = s.tlsStrategy,
                        onValueChange = { v -> edit { copy(tlsStrategy = v) } },
                        label = { Text(stringResource(R.string.tg_tls_strategy_field), fontSize = 11.sp) },
                        isError = bad != null,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        colors = tfColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (bad != null) {
                        Text(
                            text = stringResource(R.string.tg_error_bad_tls_strategy, bad),
                            style = MaterialTheme.typography.bodySmall,
                            color = AntiDpiColors.TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TgRadioRow(title: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = AntiDpiColors.TextPrimary,
                unselectedColor = AntiDpiColors.TextSecondary
            ),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TgToggle(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AntiDpiColors.InvertedText,
                checkedTrackColor = AntiDpiColors.InvertedBg,
                uncheckedThumbColor = AntiDpiColors.TextSecondary,
                uncheckedTrackColor = AntiDpiColors.SurfaceElevated
            )
        )
    }
}

private enum class TgLogFilter(val labelRes: Int, val levels: Set<String>?) {
    ALL(R.string.tg_log_all, null),
    WARNINGS(R.string.tg_log_warnings, setOf("WARNING", "ERROR")),
    ERRORS(R.string.tg_log_errors, setOf("ERROR")),
}

private val TG_LOG_LEVEL_RE = Regex("""^(?:\d{4}-\d\d-\d\d )?\d\d:\d\d:\d\d\s+(\w+)""")

private fun filterTgLog(text: String, levels: Set<String>?): String {
    if (levels == null) return text
    var keep = false
    return text.lines().filter { line ->
        val lvl = TG_LOG_LEVEL_RE.find(line)?.groupValues?.get(1)
        if (lvl != null) keep = lvl in levels
        keep
    }.joinToString("\n")
}

@Composable
private fun TgLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf(TgLogFilter.ALL) }
    var refreshes by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }

    LaunchedEffect(refreshes) {
        text = withContext(Dispatchers.IO) { TgProxyCore.readLogTail(context) }
    }
    val shown = filterTgLog(text, filter.levels)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = AntiDpiColors.Surface,
            border = BorderStroke(1.dp, AntiDpiColors.Border)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.tg_logs),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { refreshes++ }) {
                        Text(stringResource(R.string.tg_refresh), color = AntiDpiColors.TextPrimary, fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        TgProxyCore.clearLogs(context)
                        refreshes++
                    }) {
                        Text(stringResource(R.string.action_clear), color = AntiDpiColors.TextSecondary, fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TgLogFilter.entries.forEach { f ->
                        val sel = filter == f
                        Surface(
                            modifier = Modifier.clickable { filter = f },
                            shape = RoundedCornerShape(6.dp),
                            color = if (sel) AntiDpiColors.InvertedBg else AntiDpiColors.SurfaceElevated,
                            border = BorderStroke(1.dp, AntiDpiColors.Border)
                        ) {
                            Text(
                                text = stringResource(f.labelRes),
                                color = if (sel) AntiDpiColors.InvertedText else AntiDpiColors.TextBody,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 360.dp)
                        .background(AntiDpiColors.Background, RoundedCornerShape(6.dp))
                        .border(1.dp, AntiDpiColors.Border, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = shown.ifEmpty { stringResource(R.string.tg_logs_empty) },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm?.setPrimaryClip(ClipData.newPlainText("tg-log", shown))
                        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                    }) {
                        Text(stringResource(R.string.action_copy), color = AntiDpiColors.TextPrimary)
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close), color = AntiDpiColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TgWorkerSetupDialog(onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<TgWorkerSetup.Result?>(null) }
    val created = result as? TgWorkerSetup.Result.Created

    Dialog(onDismissRequest = { if (!working) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = AntiDpiColors.Surface,
            border = BorderStroke(1.dp, AntiDpiColors.Border)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = stringResource(R.string.tg_worker_setup_title), style = MaterialTheme.typography.titleMedium)

                if (created != null) {
                    Text(
                        text = stringResource(R.string.tg_worker_setup_done, created.domain),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = {
                                onCreated(created.domain)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AntiDpiColors.InvertedBg,
                                contentColor = AntiDpiColors.InvertedText
                            )
                        ) {
                            Text(stringResource(R.string.tg_worker_setup_use), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    return@Column
                }

                Text(
                    text = stringResource(R.string.tg_worker_setup_steps),
                    style = MaterialTheme.typography.bodyMedium.copy(color = AntiDpiColors.TextSecondary, fontSize = 12.sp)
                )
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TgWorkerSetup.TOKENS_PAGE)))
                        } catch (_: Exception) {
                        }
                    },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, AntiDpiColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AntiDpiColors.TextPrimary)
                ) {
                    Text(stringResource(R.string.tg_worker_setup_open_tokens), fontSize = 12.sp)
                }

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it; result = null },
                    enabled = !working,
                    singleLine = true,
                    label = { Text(stringResource(R.string.tg_worker_setup_token), fontSize = 11.sp) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AntiDpiColors.TextPrimary,
                        unfocusedBorderColor = AntiDpiColors.Border,
                        focusedTextColor = AntiDpiColors.TextPrimary,
                        unfocusedTextColor = AntiDpiColors.TextBody,
                        cursorColor = AntiDpiColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                (result as? TgWorkerSetup.Result.Failed)?.let { failed ->
                    val errText = when (failed.error) {
                        TgWorkerSetup.Error.BAD_TOKEN -> stringResource(R.string.tg_worker_bad_token)
                        TgWorkerSetup.Error.NO_ACCOUNT -> stringResource(R.string.tg_worker_no_account)
                        TgWorkerSetup.Error.NO_PERMISSION -> stringResource(R.string.tg_worker_no_perm)
                        TgWorkerSetup.Error.NETWORK -> stringResource(R.string.tg_worker_network)
                        TgWorkerSetup.Error.NAME_TAKEN -> stringResource(R.string.tg_worker_name_taken)
                        TgWorkerSetup.Error.API -> stringResource(R.string.tg_worker_api)
                    } + (failed.detail?.let { " ($it)" } ?: "")
                    Text(text = errText, style = MaterialTheme.typography.bodySmall, color = AntiDpiColors.TextPrimary)
                }

                if (working) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = AntiDpiColors.TextPrimary)
                        Text(stringResource(R.string.tg_worker_working), style = MaterialTheme.typography.bodySmall)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(enabled = !working, onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel), color = AntiDpiColors.TextSecondary)
                    }
                    Button(
                        enabled = !working && token.isNotBlank(),
                        onClick = {
                            if (!TgWorkerSetup.isWellFormedToken(token)) {
                                result = TgWorkerSetup.Result.Failed(TgWorkerSetup.Error.BAD_TOKEN)
                                return@Button
                            }
                            working = true
                            scope.launch {
                                result = TgWorkerSetup.create(
                                    TgWorkerSetup.api(token.trim()),
                                    TgWorkerSetup.workerScript(context)
                                )
                                if (result is TgWorkerSetup.Result.Created) token = ""
                                working = false
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AntiDpiColors.InvertedBg,
                            contentColor = AntiDpiColors.InvertedText
                        )
                    ) {
                        Text(stringResource(R.string.tg_worker_create), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun cfTransportLabel(t: CfTransport): Int = when (t) {
    CfTransport.BYPASS -> R.string.tg_cf_transport_bypass
    CfTransport.TLS -> R.string.tg_cf_transport_tls
    CfTransport.PLAIN -> R.string.tg_cf_transport_plain
}

private fun cfReasonRes(t: CfTrouble): Int = when (t.reason) {
    CfFailureReason.TIMEOUT -> R.string.tg_cf_reason_timeout
    CfFailureReason.RESET -> R.string.tg_cf_reason_reset
    CfFailureReason.TLS -> R.string.tg_cf_reason_tls
    CfFailureReason.DNS -> if (t.route == CfRoute.WORKER) R.string.tg_cf_reason_worker_dns else R.string.tg_cf_reason_dns
    CfFailureReason.REFUSED -> R.string.tg_cf_reason_refused
    CfFailureReason.LIMIT -> R.string.tg_cf_reason_limit
    CfFailureReason.FORBIDDEN -> R.string.tg_cf_reason_forbidden
    CfFailureReason.SERVER_ERROR -> R.string.tg_cf_reason_server_error
    CfFailureReason.HTTP -> R.string.tg_cf_reason_http
    CfFailureReason.NETWORK -> R.string.tg_cf_reason_network
    CfFailureReason.WORKER_CLOSED -> R.string.tg_cf_reason_worker_closed
    CfFailureReason.OTHER -> R.string.tg_cf_reason_other
}

private fun TgProxyError.readableMessage(context: Context): String {
    val d = detail.orEmpty()
    return when (code) {
        "port_in_use" -> context.getString(R.string.tg_err_port_in_use, d)
        "permission" -> context.getString(R.string.tg_err_permission, d)
        "address" -> context.getString(R.string.tg_err_address, d)
        "bad_secret" -> context.getString(R.string.tg_err_bad_secret)
        "bad_port" -> context.getString(R.string.tg_err_bad_port, d)
        "bad_dc_ip" -> context.getString(R.string.tg_err_bad_dc_ip, d)
        "bad_fake_tls" -> context.getString(R.string.tg_err_bad_fake_tls, d)
        "bad_tls_strategy" -> context.getString(R.string.tg_error_bad_tls_strategy, d)
        else -> context.getString(R.string.tg_err_unknown, detail ?: code)
    }
}
