package com.coinepro.feature.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProColors

@Composable
fun ToolsScreen(
    onOpenConnections: () -> Unit,
    onOpenNews: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Trader Tools", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Execution, market intelligence and risk context live behind explicit source contracts.", color = CoineProColors.TextSecondary)

        ToolCard(
            title = "Market Intelligence",
            description = "Structured market news with explicit source time, impact and sentiment.",
            button = "Open News",
            onClick = onOpenNews,
        )
        ToolCard(
            title = "Economic Calendar",
            description = "Low / Medium / High / Unknown impact with actual, forecast and previous values when supplied.",
            button = "Open Calendar",
            onClick = onOpenCalendar,
        )
        ToolCard(
            title = "Connections",
            description = "Connections are used only for executing CoinePro signals.",
            button = "MT5 & LBank Connections",
            onClick = onOpenConnections,
        )
    }
}

@Composable
private fun ToolCard(
    title: String,
    description: String,
    button: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CoineProColors.SurfaceElevated),
        border = BorderStroke(1.dp, CoineProColors.Border),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, color = CoineProColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(button) }
        }
    }
}
