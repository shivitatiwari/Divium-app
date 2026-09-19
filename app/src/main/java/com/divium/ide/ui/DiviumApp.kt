package com.divium.ide.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.divium.ide.codex.CodexService
import com.divium.ide.domain.LanguageCatalog
import com.divium.ide.domain.LanguagePack
import com.divium.ide.editor.EditorPane
import com.divium.ide.extensions.ExtensionManager
import com.divium.ide.files.FileNode
import com.divium.ide.files.FileService
import com.divium.ide.git.GitService
import com.divium.ide.preview.PreviewPane
import com.divium.ide.project.ProjectAction
import com.divium.ide.project.ProjectIntelligence
import com.divium.ide.runtime.ProcessManager
import com.divium.ide.runtime.ProcessSnapshot
import com.divium.ide.runtime.RuntimeEnvironment
import com.divium.ide.settings.UserPreferences
import com.divium.ide.terminal.DiviumTerminal
import com.divium.ide.theme.DiviumThemeTokens
import com.divium.ide.theme.DiviumThemes
import com.divium.ide.workspace.Workspace
import com.divium.ide.workspace.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Coral = Color(0xFFD96547)
private val Moss = Color(0xFF52664B)

private enum class IdePanel(val label: String) {
    FILES("Files"),
    EDITOR("Editor"),
    SEARCH("Search"),
    GIT("Git"),
    TERMINAL("Terminal"),
    PREVIEW("Preview"),
    MORE("More"),
}

@Composable
fun DiviumApp() {
    val context = LocalContext.current
    val preferences = remember { UserPreferences(context) }
    var setupComplete by rememberSaveable { mutableStateOf(preferences.setupComplete) }
    var activeWorkspace by remember { mutableStateOf<Workspace?>(null) }
    var selectedTheme by remember {
        mutableStateOf(DiviumThemes.builtIns.firstOrNull { it.id == preferences.themeId } ?: DiviumThemes.Paper)
    }

    DiviumTheme(selectedTheme) {
        Surface(Modifier.fillMaxSize(), color = selectedTheme.background) {
            when {
                !setupComplete -> SetupFlow(
                    initialLanguages = preferences.selectedLanguages,
                    initialWorkspaceMode = preferences.workspaceMode,
                    onComplete = { languages, mode ->
                        preferences.selectedLanguages = languages
                        preferences.workspaceMode = mode
                        preferences.setupComplete = true
                        setupComplete = true
                    },
                )
                activeWorkspace == null -> WorkspaceHub(
                    onOpen = { activeWorkspace = it },
                    onResetSetup = {
                        preferences.setupComplete = false
                        setupComplete = false
                    },
                )
                else -> IdeScreen(
                    workspace = activeWorkspace!!,
                    theme = selectedTheme,
                    onThemeChanged = {
                        selectedTheme = it
                        preferences.themeId = it.id
                    },
                    onClose = { activeWorkspace = null },
                )
            }
        }
    }
}

@Composable
private fun DiviumTheme(tokens: DiviumThemeTokens, content: @Composable () -> Unit) {
    val colors = if (tokens.dark) {
        androidx.compose.material3.darkColorScheme(
            primary = tokens.accent,
            background = tokens.background,
            surface = tokens.surface,
            onBackground = tokens.foreground,
            onSurface = tokens.foreground,
        )
    } else {
        androidx.compose.material3.lightColorScheme(
            primary = tokens.accent,
            background = tokens.background,
            surface = tokens.surface,
            onBackground = tokens.foreground,
            onSurface = tokens.foreground,
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupFlow(
    initialLanguages: Set<String>,
    initialWorkspaceMode: String,
    onComplete: (Set<String>, String) -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var selectedLanguages by rememberSaveable { mutableStateOf(initialLanguages) }
    var workspaceMode by rememberSaveable { mutableStateOf(initialWorkspaceMode) }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("DIVIUM", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("Your development environment, on Android", style = MaterialTheme.typography.labelSmall)
                }
            })
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            StepPill(step + 1, 2)
            if (step == 0) {
                Text("What do you build with?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("Divium uses this to prepare the toolchains and language servers you need.")
                LanguageCatalog.starterPacks.forEach { pack ->
                    LanguageChoice(pack, pack.id in selectedLanguages) {
                        selectedLanguages = if (pack.id in selectedLanguages) selectedLanguages - pack.id else selectedLanguages + pack.id
                    }
                }
                Button(
                    onClick = { step = 1 },
                    enabled = selectedLanguages.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) { Text("Choose workspace") }
            } else {
                Text("Where should projects live?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("You can change this per project later.")
                WorkspaceChoice(
                    "managed",
                    "Divium storage",
                    "Fast, private POSIX workspaces. Recommended for compilers, Git and language servers.",
                    workspaceMode,
                ) { workspaceMode = it }
                WorkspaceChoice(
                    "external",
                    "Device folder",
                    "Use a folder you choose through Android. Divium mirrors it safely for development tooling.",
                    workspaceMode,
                ) { workspaceMode = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { step = 0 }, modifier = Modifier.weight(1f)) { Text("Back") }
                    Button(
                        onClick = { onComplete(selectedLanguages, workspaceMode) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Finish setup") }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun LanguageChoice(pack: LanguagePack, selected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, border = CardDefaults.outlinedCardBorder(true)) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(pack.name, fontWeight = FontWeight.Bold)
                Text(pack.description, style = MaterialTheme.typography.bodySmall)
                Text(pack.estimatedDownload, style = MaterialTheme.typography.labelSmall, color = Moss)
            }
            FilterChip(selected = selected, onClick = onClick, label = { Text(if (selected) "Selected" else "Add") })
        }
    }
}

@Composable
private fun WorkspaceChoice(id: String, title: String, detail: String, selected: String, onSelect: (String) -> Unit) {
    Card(onClick = { onSelect(id) }, border = CardDefaults.outlinedCardBorder(true)) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            RadioButton(selected == id, onClick = { onSelect(id) })
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceHub(
    onOpen: (Workspace) -> Unit,
    onResetSetup: () -> Unit,
) {
    val context = LocalContext.current
    val manager = remember { WorkspaceManager(context) }
    val scope = rememberCoroutineScope()
    var workspaces by remember { mutableStateOf(manager.list()) }
    var showCreate by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val externalPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { manager.attachExternal(uri) }
                    .onSuccess {
                        workspaces = manager.list()
                        onOpen(it)
                    }
                    .onFailure { error = it.message }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("DIVIUM", fontWeight = FontWeight.Black, letterSpacing = 2.sp) }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(18.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Projects", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showCreate = true }, modifier = Modifier.weight(1f)) { Text("New project") }
                OutlinedButton(
                    onClick = { externalPicker.launch(null) },
                    modifier = Modifier.weight(1f),
                ) { Text("Open folder") }
            }

            if (workspaces.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("No projects yet", fontWeight = FontWeight.Bold)
                        Text("Create a Divium project or choose a folder from your device.")
                    }
                }
            } else {
                Text("Recent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                workspaces.forEach { workspace ->
                    Card(onClick = { onOpen(workspace) }, border = CardDefaults.outlinedCardBorder(true)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(workspace.name, fontWeight = FontWeight.Bold)
                            Text(
                                if (workspace.externalTreeUri == null) "Divium storage" else "Device folder",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onResetSetup) { Text("Run setup again") }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New project") },
            text = {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    singleLine = true,
                    label = { Text("Project name") },
                )
            },
            confirmButton = {
                Button(onClick = {
                    runCatching { manager.createManaged(projectName) }
                        .onSuccess {
                            showCreate = false
                            projectName = ""
                            workspaces = manager.list()
                            onOpen(it)
                        }
                        .onFailure { error = it.message }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdeScreen(
    workspace: Workspace,
    theme: DiviumThemeTokens,
    onThemeChanged: (DiviumThemeTokens) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val runtime = remember { RuntimeEnvironment(context) }
    val processManager = remember { ProcessManager(runtime) }
    val fileService = remember(workspace.id) { FileService(workspace.root) }
    val preferences = remember { UserPreferences(context) }
    val scope = rememberCoroutineScope()

    var panel by rememberSaveable(workspace.id) { mutableStateOf(IdePanel.FILES) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var output by remember { mutableStateOf("") }
    var activeProcess by remember { mutableStateOf<ProcessSnapshot?>(null) }
    var previewPort by remember { mutableStateOf<Int?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val profile = remember(workspace.root.lastModified(), workspace.id) { ProjectIntelligence.detect(workspace.root) }

    fun runAction(action: ProjectAction) {
        panel = if (action.localhost) IdePanel.PREVIEW else IdePanel.TERMINAL
        output = ""
        actionError = null
        processManager.start(action.command, workspace.root) { snapshot ->
            scope.launch {
                activeProcess = snapshot
                output = snapshot.output.joinToString("\n")
                snapshot.ports.firstOrNull()?.let { previewPort = it }
            }
        }
    }

    DisposableEffect(workspace.id) {
        onDispose {
            processManager.snapshots()
                .filter { it.state == com.divium.ide.runtime.ProcessState.RUNNING }
                .forEach { processManager.stop(it.id) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(workspace.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(profile.kind, style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = { TextButton(onClick = onClose) { Text("‹ Projects") } },
            )
        },
        bottomBar = {
            NavigationBar {
                listOf(IdePanel.FILES, IdePanel.EDITOR, IdePanel.GIT, IdePanel.TERMINAL, IdePanel.MORE).forEach { item ->
                    NavigationBarItem(
                        selected = panel == item,
                        onClick = { panel = item },
                        icon = { Text(item.label.take(2).uppercase(), style = MaterialTheme.typography.labelSmall) },
                        label = { Text(item.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (profile.actions.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    profile.actions.forEach { action ->
                        OutlinedButton(onClick = { runAction(action) }) { Text(action.label) }
                    }
                    previewPort?.let {
                        OutlinedButton(onClick = { panel = IdePanel.PREVIEW }) { Text("Preview :$it") }
                    }
                }
                HorizontalDivider()
            }

            actionError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
            }

            Box(Modifier.fillMaxSize()) {
                when (panel) {
                    IdePanel.FILES -> FileManagerPanel(
                        workspace = workspace,
                        service = fileService,
                        onOpen = {
                            selectedFile = it
                            panel = IdePanel.EDITOR
                        },
                    )
                    IdePanel.EDITOR -> {
                        val file = selectedFile
                        if (file != null && file.isFile) {
                            EditorPane(
                                file = file,
                                onSave = { text ->
                                    fileService.write(file.relativeTo(workspace.root).path, text)
                                    if (workspace.externalTreeUri != null) {
                                        scope.launch { runCatching { WorkspaceManager(context).syncToExternal(workspace) } }
                                    }
                                },
                            )
                        } else {
                            EmptyState("Open a file from Files to start editing.")
                        }
                    }
                    IdePanel.SEARCH -> SearchPanel(workspace, fileService) {
                        selectedFile = it
                        panel = IdePanel.EDITOR
                    }
                    IdePanel.GIT -> GitPanel(
                        workspace = workspace,
                        authorName = preferences.gitAuthorName,
                        authorEmail = preferences.gitAuthorEmail,
                    )
                    IdePanel.TERMINAL -> Column(Modifier.fillMaxSize()) {
                        if (output.isNotBlank()) {
                            Card(Modifier.fillMaxWidth().padding(6.dp)) {
                                Column(Modifier.padding(8.dp)) {
                                    Text("Task output", fontWeight = FontWeight.Bold)
                                    Text(output.takeLast(6_000), style = MaterialTheme.typography.bodySmall)
                                    activeProcess?.let { snapshot ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(snapshot.state.name, style = MaterialTheme.typography.labelSmall)
                                            if (snapshot.state == com.divium.ide.runtime.ProcessState.RUNNING) {
                                                TextButton(onClick = { processManager.stop(snapshot.id) }) { Text("Stop") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        DiviumTerminal(workspace.root, Modifier.weight(1f))
                    }
                    IdePanel.PREVIEW -> {
                        val port = previewPort
                        if (port == null) EmptyState("Run a localhost project and Divium will detect its port.")
                        else PreviewPane("http://127.0.0.1:$port", Modifier.fillMaxSize())
                    }
                    IdePanel.MORE -> MorePanel(
                        theme = theme,
                        onThemeChanged = onThemeChanged,
                        onSearch = { panel = IdePanel.SEARCH },
                        workspace = workspace,
                        runtime = runtime,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileManagerPanel(
    workspace: Workspace,
    service: FileService,
    onOpen: (File) -> Unit,
) {
    var currentDir by rememberSaveable(workspace.id) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var createMode by remember { mutableStateOf<String?>(null) }
    var createName by remember { mutableStateOf("") }
    var selectedMenu by remember { mutableStateOf<FileNode?>(null) }
    var renameTarget by remember { mutableStateOf<FileNode?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var infoNode by remember { mutableStateOf<FileNode?>(null) }
    var clipboardPath by remember { mutableStateOf<String?>(null) }
    var clipboardCut by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val nodes = remember(currentDir, refresh, showHidden) {
        runCatching { service.list(currentDir, showHidden) }.getOrDefault(emptyList())
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (currentDir.isNotEmpty()) {
                TextButton(onClick = {
                    currentDir = File(currentDir).parent?.takeIf { it != "." } ?: ""
                }) { Text("↑ Up") }
            }
            Button(onClick = { createMode = "file" }) { Text("+ File") }
            OutlinedButton(onClick = { createMode = "folder" }) { Text("+ Folder") }
            OutlinedButton(onClick = { showHidden = !showHidden }) { Text(if (showHidden) "Hide dotfiles" else "Dotfiles") }
            if (clipboardPath != null) {
                OutlinedButton(onClick = {
                    runCatching {
                        if (clipboardCut) service.move(clipboardPath!!, currentDir) else service.copy(clipboardPath!!, currentDir)
                    }.onSuccess {
                        clipboardPath = null
                        refresh++
                    }.onFailure { error = it.message }
                }) { Text("Paste") }
            }
        }
        Text(
            if (currentDir.isEmpty()) workspace.name else currentDir,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HorizontalDivider()
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
            items(nodes.size, key = { index -> nodes[index].relativePath }) { index ->
                val node = nodes[index]
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
                    TextButton(
                        onClick = {
                            if (node.isDirectory) currentDir = node.relativePath else onOpen(node.file)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            (if (node.isDirectory) "▸ " else "") + node.file.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box {
                        TextButton(onClick = { selectedMenu = node }) { Text("•••") }
                        DropdownMenu(expanded = selectedMenu == node, onDismissRequest = { selectedMenu = null }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = {
                                selectedMenu = null
                                renameTarget = node
                                renameValue = node.file.name
                            })
                            DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                                selectedMenu = null
                                runCatching { service.duplicate(node.relativePath) }
                                    .onSuccess { refresh++ }
                                    .onFailure { error = it.message }
                            })
                            DropdownMenuItem(text = { Text("Copy") }, onClick = {
                                clipboardPath = node.relativePath
                                clipboardCut = false
                                selectedMenu = null
                            })
                            DropdownMenuItem(text = { Text("Cut") }, onClick = {
                                clipboardPath = node.relativePath
                                clipboardCut = true
                                selectedMenu = null
                            })
                            DropdownMenuItem(text = { Text("Details") }, onClick = {
                                infoNode = node
                                selectedMenu = null
                            })
                            if (node.file.extension.equals("zip", true)) {
                                DropdownMenuItem(text = { Text("Unzip here") }, onClick = {
                                    selectedMenu = null
                                    runCatching { service.unzip(node.relativePath, currentDir) }
                                        .onSuccess { refresh++ }
                                        .onFailure { error = it.message }
                                })
                            } else {
                                DropdownMenuItem(text = { Text("Create ZIP") }, onClick = {
                                    selectedMenu = null
                                    val output = node.relativePath + ".zip"
                                    runCatching { service.zip(node.relativePath, output) }
                                        .onSuccess { refresh++ }
                                        .onFailure { error = it.message }
                                })
                            }
                            DropdownMenuItem(text = { Text("Delete") }, onClick = {
                                selectedMenu = null
                                runCatching { service.delete(node.relativePath) }
                                    .onSuccess { refresh++ }
                                    .onFailure { error = it.message }
                            })
                        }
                    }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
    }

    if (createMode != null) {
        AlertDialog(
            onDismissRequest = { createMode = null },
            title = { Text(if (createMode == "file") "New file" else "New folder") },
            text = { OutlinedTextField(createName, { createName = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    val relative = listOf(currentDir, createName).filter(String::isNotBlank).joinToString("/")
                    runCatching {
                        if (createMode == "file") service.createFile(relative) else service.createDirectory(relative)
                    }.onSuccess {
                        createMode = null
                        createName = ""
                        refresh++
                    }.onFailure { error = it.message }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { createMode = null }) { Text("Cancel") } },
        )
    }

    renameTarget?.let { node ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(renameValue, { renameValue = it }, singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    runCatching { service.rename(node.relativePath, renameValue) }
                        .onSuccess {
                            renameTarget = null
                            refresh++
                        }
                        .onFailure { error = it.message }
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }

    infoNode?.let { node ->
        AlertDialog(
            onDismissRequest = { infoNode = null },
            title = { Text(node.file.name) },
            text = {
                Column {
                    Text("Path: ${node.relativePath}")
                    Text(if (node.isDirectory) "Directory" else "Size: ${node.size} bytes")
                    Text("Modified: ${java.util.Date(node.modified)}")
                }
            },
            confirmButton = { Button(onClick = { infoNode = null }) { Text("Done") } },
        )
    }
}

@Composable
private fun SearchPanel(workspace: Workspace, service: FileService, onOpen: (File) -> Unit) {
    var query by rememberSaveable(workspace.id) { mutableStateOf("") }
    var regex by rememberSaveable { mutableStateOf(false) }
    var results by remember { mutableStateOf(emptyList<com.divium.ide.files.SearchHit>()) }

    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            query,
            { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search project") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = regex, onClick = { regex = !regex }, label = { Text("Regex") })
            Button(onClick = {
                results = runCatching { service.search(query, regex = regex) }.getOrDefault(emptyList())
            }) { Text("Search") }
        }
        Text("${results.size} results", style = MaterialTheme.typography.labelMedium)
        androidx.compose.foundation.lazy.LazyColumn {
            items(results.size) { index ->
                val hit = results[index]
                TextButton(onClick = { onOpen(hit.file) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(hit.file.relativeTo(workspace.root).path + ":" + hit.line, fontWeight = FontWeight.Bold)
                        Text(hit.preview, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun GitPanel(
    workspace: Workspace,
    authorName: String,
    authorEmail: String,
) {
    val service = remember(workspace.id) { GitService(workspace.root) }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("") }
    var commitMessage by rememberSaveable { mutableStateOf("") }
    var branchName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(workspace.id, refresh) {
        statusText = withContext(Dispatchers.IO) {
            if (!service.isRepository()) "Not a Git repository"
            else runCatching {
                val s = service.status()
                if (s.clean) "Working tree clean"
                else buildString {
                    if (s.modified.isNotEmpty()) append("Modified: ${s.modified.joinToString()}\n")
                    if (s.untracked.isNotEmpty()) append("Untracked: ${s.untracked.joinToString()}\n")
                    if (s.added.isNotEmpty()) append("Staged: ${s.added.joinToString()}\n")
                    if (s.conflicting.isNotEmpty()) append("Conflicts: ${s.conflicting.joinToString()}\n")
                }
            }.getOrElse { it.message ?: "Git error" }
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Source control", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (!service.isRepository()) {
            Button(onClick = {
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { service.init() } }
                        .onSuccess { refresh++ }
                        .onFailure { error = it.message }
                }
            }) { Text("Initialize Git") }
        } else {
            Text(runCatching { service.currentBranch() }.getOrDefault("HEAD"), style = MaterialTheme.typography.labelLarge)
            Card { Text(statusText, Modifier.padding(12.dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { service.stageAll() } }
                            .onSuccess { refresh++ }
                            .onFailure { error = it.message }
                    }
                }) { Text("Stage all") }
                OutlinedButton(onClick = {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { service.pull() } }
                            .onSuccess { refresh++ }
                            .onFailure { error = it.message }
                    }
                }) { Text("Pull") }
                OutlinedButton(onClick = {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { service.push() } }
                            .onSuccess { refresh++ }
                            .onFailure { error = it.message }
                    }
                }) { Text("Push") }
            }
            OutlinedTextField(
                commitMessage,
                { commitMessage = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Commit message") },
            )
            Button(
                enabled = commitMessage.isNotBlank(),
                onClick = {
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                service.stageAll()
                                service.commit(commitMessage, authorName, authorEmail)
                            }
                        }.onSuccess {
                            commitMessage = ""
                            refresh++
                        }.onFailure { error = it.message }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Commit") }
            HorizontalDivider()
            Text("Branches", fontWeight = FontWeight.Bold)
            Text(runCatching { service.branches().joinToString("\n") }.getOrDefault(""))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(branchName, { branchName = it }, modifier = Modifier.weight(1f), label = { Text("New branch") })
                Button(
                    enabled = branchName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { service.createBranch(branchName) } }
                                .onSuccess {
                                    branchName = ""
                                    refresh++
                                }
                                .onFailure { error = it.message }
                        }
                    },
                ) { Text("Create") }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun MorePanel(
    theme: DiviumThemeTokens,
    onThemeChanged: (DiviumThemeTokens) -> Unit,
    onSearch: () -> Unit,
    workspace: Workspace,
    runtime: RuntimeEnvironment,
) {
    val context = LocalContext.current
    val extensions = remember { ExtensionManager(context) }
    var extensionList by remember { mutableStateOf(extensions.installed()) }
    var message by remember { mutableStateOf<String?>(null) }
    val extensionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { extensions.installVsix(uri) }
                .onSuccess {
                    extensionList = extensions.installed()
                    message = "Installed ${it.displayName}"
                }
                .onFailure { message = it.message }
        }
    }
    var codexStatus by remember { mutableStateOf("Not started") }
    val codex = remember(workspace.id) { CodexService(runtime, workspace.root) }
    DisposableEffect(codex) { onDispose { codex.stop() } }

    Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Button(onClick = onSearch, modifier = Modifier.fillMaxWidth()) { Text("Search project") }

        Text("Themes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        DiviumThemes.builtIns.forEach { candidate ->
            FilterChip(
                selected = theme.id == candidate.id,
                onClick = { onThemeChanged(candidate) },
                label = { Text(candidate.name) },
            )
        }

        HorizontalDivider()
        Text("Extensions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedButton(
            onClick = { extensionPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Install .vsix") }
        extensionList.forEach {
            Text("${it.displayName} · ${it.version}", style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider()
        Text("Codex", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Uses the Codex app-server from the installed Divium runtime.")
        Button(onClick = {
            runCatching {
                codex.start { event -> codexStatus = event.optString("method", "Event") }
                codex.initialize { codexStatus = if (it.has("error")) "Initialization failed" else "Connected" }
                codexStatus = "Starting…"
            }.onFailure { codexStatus = it.message ?: "Unable to start Codex" }
        }) { Text("Start Codex") }
        Text(codexStatus, style = MaterialTheme.typography.bodySmall)

        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text("Runtime prefix: ${runtime.prefix.absolutePath}", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(28.dp)) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun StepPill(current: Int, total: Int) {
    Surface(color = Coral, shape = RoundedCornerShape(50)) {
        Text(
            "SETUP · $current / $total",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
