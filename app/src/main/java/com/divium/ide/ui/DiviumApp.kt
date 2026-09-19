package com.divium.ide.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.divium.ide.domain.LanguageCatalog
import com.divium.ide.domain.LanguagePack

private val Ink = Color(0xFF181715)
private val Paper = Color(0xFFF7F4EC)
private val Mist = Color(0xFFE8E3D7)
private val Coral = Color(0xFFD96547)
private val Moss = Color(0xFF52664B)

@Composable
fun DiviumApp() {
    var step by rememberSaveable { mutableStateOf(0) }
    var selectedLanguageIds by rememberSaveable { mutableStateOf<Set<String>>(setOf("javascript", "python")) }
    var workspaceMode by rememberSaveable { mutableStateOf("managed") }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Ink,
            onPrimary = Paper,
            secondary = Moss,
            background = Paper,
            onBackground = Ink,
            surface = Color(0xFFFFFCF5),
            onSurface = Ink,
            surfaceVariant = Mist,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Paper) {
            if (step < 2) {
                Onboarding(
                    step = step,
                    selectedLanguageIds = selectedLanguageIds,
                    workspaceMode = workspaceMode,
                    onToggleLanguage = { id ->
                        selectedLanguageIds = if (id in selectedLanguageIds) selectedLanguageIds - id else selectedLanguageIds + id
                    },
                    onWorkspaceMode = { workspaceMode = it },
                    onContinue = { step += 1 },
                )
            } else {
                FoundationHome(selectedLanguageIds, workspaceMode, onRestartSetup = { step = 0 })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Onboarding(
    step: Int,
    selectedLanguageIds: Set<String>,
    workspaceMode: String,
    onToggleLanguage: (String) -> Unit,
    onWorkspaceMode: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(title = {
            Column {
                Text("DIVIUM", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("A serious IDE, shaped for Android", style = MaterialTheme.typography.labelSmall)
            }
        })
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            StepPill(step + 1, 2)
            if (step == 0) LanguageStep(selectedLanguageIds, onToggleLanguage) else WorkspaceStep(workspaceMode, onWorkspaceMode)
            Spacer(Modifier.height(6.dp))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 17.dp)) {
                Text(if (step == 0) "Choose workspace" else "Finish setup", fontWeight = FontWeight.Bold)
            }
            Text(
                "Nothing is downloaded yet. Divium will install only the runtimes you choose after its embedded runtime layer is ready.",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.copy(alpha = 0.68f),
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun LanguageStep(selected: Set<String>, onToggle: (String) -> Unit) {
    Text("What do you build with?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
    Text("Select the toolchains Divium should prepare. You can add or remove languages later.", style = MaterialTheme.typography.bodyLarge)
    LanguageCatalog.starterPacks.forEach { pack -> LanguageCard(pack, pack.id in selected, onToggle) }
    Text(LanguageCatalog.totalSizeFor(selected), style = MaterialTheme.typography.labelLarge, color = Moss, fontWeight = FontWeight.Bold)
}

@Composable
private fun LanguageCard(pack: LanguagePack, selected: Boolean, onToggle: (String) -> Unit) {
    Card(
        onClick = { onToggle(pack.id) },
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFFFEEE8) else Color(0xFFFFFCF5)),
        border = CardDefaults.outlinedCardBorder(enabled = true),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(pack.name, fontWeight = FontWeight.Bold)
                Text(pack.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text("Estimated download · " + pack.estimatedDownload, style = MaterialTheme.typography.labelSmall, color = Moss)
            }
            Spacer(Modifier.width(12.dp))
            FilterChip(selected = selected, onClick = { onToggle(pack.id) }, label = { Text(if (selected) "Selected" else "Add") })
        }
    }
}

@Composable
private fun WorkspaceStep(selected: String, onSelect: (String) -> Unit) {
    Text("Where should projects live?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
    Text("You can start private and fast, or use a folder you already manage on your device.", style = MaterialTheme.typography.bodyLarge)
    WorkspaceOption("Divium storage", "Private, POSIX-first workspaces. Best runtime and Git compatibility.", selected == "managed") { onSelect("managed") }
    WorkspaceOption("Device folder", "Choose a folder through Android’s secure folder picker. Divium will mirror it for development tooling.", selected == "external") { onSelect("external") }
    Card(colors = CardDefaults.cardColors(containerColor = Mist)) {
        Text("You remain in control: external folders are selected explicitly, and extensions are limited to the workspace you open.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WorkspaceOption(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, border = CardDefaults.outlinedCardBorder(enabled = true)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoundationHome(selectedLanguageIds: Set<String>, workspaceMode: String, onRestartSetup: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("DIVIUM", fontWeight = FontWeight.Black, letterSpacing = 2.sp) }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Foundation ready.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("The Android shell is configured. The next shipped layer is the embedded runtime, real terminal and workspace filesystem.")
            StatusCard("Selected languages", LanguageCatalog.totalSizeFor(selectedLanguageIds))
            StatusCard("Workspace preference", if (workspaceMode == "managed") "Divium storage" else "Device folder")
            StatusCard("Current capability", "Onboarding and application shell")
            OutlinedButton(onClick = onRestartSetup, modifier = Modifier.fillMaxWidth()) { Text("Review setup") }
        }
    }
}

@Composable
private fun StatusCard(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF5)), border = CardDefaults.outlinedCardBorder(enabled = true)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Moss)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StepPill(current: Int, total: Int) {
    Box(modifier = Modifier.background(Coral, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text("SETUP · " + current + " / " + total, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}
