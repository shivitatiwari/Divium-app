package com.divium.ide

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.divium.core.designsystem.DiviumTheme
import com.divium.core.model.LanguagePack
import com.divium.core.model.WorkspaceMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DiviumTheme {
                DiviumApp(SetupStore(applicationContext))
            }
        }
    }
}

@Composable
private fun DiviumApp(store: SetupStore) {
    val state by store.state.collectAsState(initial = SetupState())
    val scope = rememberCoroutineScope()
    Surface(modifier = Modifier.fillMaxSize()) {
        when {
            !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.completed -> WorkspaceHome(
                state = state,
                onReset = { scope.launch { store.reset() } },
            )
            else -> SetupFlow(store)
        }
    }
}

@Composable
private fun SetupFlow(store: SetupStore) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf(setOf(LanguagePack.JAVASCRIPT, LanguagePack.PYTHON)) }
    var workspaceMode by rememberSaveable { mutableStateOf(WorkspaceMode.DIVIUM) }
    var externalUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            externalUri = uri
            workspaceMode = WorkspaceMode.DEVICE_FOLDER
        }
    }

    SetupScaffold {
        when (step) {
            0 -> WelcomeStep(onContinue = { step = 1 })
            1 -> LanguagesStep(
                selected = selected,
                onToggle = { language ->
                    selected = selected.toMutableSet().apply {
                        if (!add(language)) remove(language)
                    }
                },
                onBack = { step = 0 },
                onContinue = { if (selected.isNotEmpty()) step = 2 },
            )
            else -> WorkspaceStep(
                selected = workspaceMode,
                externalUri = externalUri,
                onInternal = { workspaceMode = WorkspaceMode.DIVIUM; externalUri = null },
                onPickExternal = { folderPicker.launch(null) },
                onBack = { step = 1 },
                onComplete = {
                    scope.launch {
                        store.complete(selected, workspaceMode, externalUri)
                    }
                },
            )
        }
    }
}

@Composable
private fun SetupScaffold(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        content()
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Image(
                painter = painterResource(R.drawable.divium_logo),
                contentDescription = "Divium logo",
                modifier = Modifier.size(132.dp),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(28.dp))
            Text("Divium", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text(
                "A complete development workspace built for Android. Your editor, runtimes, terminal and projects stay together.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Set up Divium")
        }
    }
}

@Composable
private fun LanguagesStep(
    selected: Set<LanguagePack>,
    onToggle: (LanguagePack) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Primary languages", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick what you use. Divium will install only the runtime packs you need.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(22.dp))
        LanguagePack.entries.forEach { language ->
            ChoiceCard(
                title = language.title,
                description = language.description,
                selected = language in selected,
                onClick = { onToggle(language) },
            )
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(onClick = onContinue, enabled = selected.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun WorkspaceStep(
    selected: WorkspaceMode,
    externalUri: Uri?,
    onInternal: () -> Unit,
    onPickExternal: () -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Workspace storage", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Use Divium storage for maximum speed, or choose a folder you control on your device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(22.dp))
        ChoiceCard(
            title = "Divium storage",
            description = "Fast, private app storage. Recommended for active development.",
            selected = selected == WorkspaceMode.DIVIUM,
            onClick = onInternal,
        )
        Spacer(Modifier.height(10.dp))
        ChoiceCard(
            title = "Device folder",
            description = externalUri?.toString() ?: "Choose a folder through Android's secure folder picker.",
            selected = selected == WorkspaceMode.DEVICE_FOLDER,
            onClick = onPickExternal,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(
                onClick = onComplete,
                enabled = selected == WorkspaceMode.DIVIUM || externalUri != null,
                modifier = Modifier.weight(1f),
            ) { Text("Finish") }
        }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(if (selected) "Selected" else "Choose", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun WorkspaceHome(state: SetupState, onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.divium_logo),
                contentDescription = null,
                modifier = Modifier.size(58.dp),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.size(14.dp))
            Column {
                Text("Divium", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Foundation ready", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(30.dp))
        Text("Environment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("Languages")
                Text(
                    state.languages.sortedBy { it.title }.joinToString { it.title },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))
                Text("Workspace")
                Text(
                    if (state.workspaceMode == WorkspaceMode.DIVIUM) "Divium storage"
                    else state.externalWorkspaceUri?.toString() ?: "Device folder",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "The runtime, project explorer and terminal are implemented in the next engineering milestone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("Reset setup")
        }
    }
}
