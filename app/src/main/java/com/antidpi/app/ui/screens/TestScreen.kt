package com.antidpi.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antidpi.app.R
import com.antidpi.app.ui.theme.AntiDpiColors

@Composable
fun TestScreen(
    isTesting: Boolean,
    progress: Float,
    logs: List<String>,
    ranking: String,
    hasConfigs: Boolean,
    onTestAll: () -> Unit,
    onTestSelected: () -> Unit,
    onStop: () -> Unit,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit
) {
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onTestAll,
                enabled = !isTesting && hasConfigs,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AntiDpiColors.InvertedBg,
                    contentColor = AntiDpiColors.InvertedText,
                    disabledContainerColor = AntiDpiColors.SurfaceElevated,
                    disabledContentColor = AntiDpiColors.TextSecondary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.test_all),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = onTestSelected,
                enabled = !isTesting && hasConfigs,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, AntiDpiColors.Border),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AntiDpiColors.TextPrimary,
                    disabledContentColor = AntiDpiColors.TextSecondary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.test_selected),
                    fontSize = 13.sp
                )
            }
        }

        if (isTesting) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onStop,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, AntiDpiColors.TextPrimary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AntiDpiColors.TextPrimary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.test_stop), fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LinearProgressIndicator(
            progress = { if (isTesting) progress else if (ranking.isNotEmpty()) 1f else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = AntiDpiColors.TextPrimary,
            trackColor = AntiDpiColors.SurfaceElevated
        )

        if (ranking.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.test_results_title),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                shape = RoundedCornerShape(6.dp),
                color = AntiDpiColors.Surface,
                border = BorderStroke(1.dp, AntiDpiColors.Border)
            ) {
                Text(
                    text = ranking,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.test_console_title),
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
            Spacer(modifier = Modifier.width(4.dp))
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
