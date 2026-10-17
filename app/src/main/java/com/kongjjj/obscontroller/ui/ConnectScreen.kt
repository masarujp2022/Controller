package com.kongjjj.obscontroller.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.kongjjj.obscontroller.ConnectionState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import com.kongjjj.obscontroller.DEFAULT_OBS_PORT
import com.kongjjj.obscontroller.OBSProfile

// Sentinel value representing a blank "new profile" slot
private val NEW_PROFILE = OBSProfile(id = "__new__", name = "", host = "", port = DEFAULT_OBS_PORT, password = "")

@Composable
fun ConnectScreen(
    profiles: List<OBSProfile>,
    lastUsedProfileId: String?,
    connectionState: ConnectionState,
    autoConnect: Boolean,
    reconnecting: Boolean,
    onConnect: (OBSProfile) -> Unit,
    onSaveProfile: (OBSProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onAutoConnectChange: (Boolean) -> Unit,
    onCancelReconnect: () -> Unit = {},
    onExport: (Uri) -> Unit = {},
    onImport: (Uri) -> Unit = {}
) {
    // Which profile chip is currently selected (null = new)
    var selectedId by remember(profiles) {
        // Pre-select last used profile, or first profile, or new
        val initialId = when {
            lastUsedProfileId != null && profiles.any { it.id == lastUsedProfileId } -> lastUsedProfileId
            profiles.isNotEmpty() -> profiles.first().id
            else -> NEW_PROFILE.id
        }
        mutableStateOf(initialId)
    }

    val selectedProfile = profiles.find { it.id == selectedId }

    // Form state — synced to selected profile
    var name by remember(selectedId) { mutableStateOf(selectedProfile?.name ?: "") }
    var host by remember(selectedId) { mutableStateOf(selectedProfile?.host ?: "") }
    var port by remember(selectedId) { mutableStateOf(selectedProfile?.port?.toString() ?: "4455") }
    var password by remember(selectedId) { mutableStateOf(selectedProfile?.password ?: "") }
    var showHost by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val isConnecting = connectionState is ConnectionState.Connecting
    val isNewProfile = selectedId == NEW_PROFILE.id

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri -> uri?.let { onExport(it) } }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> uri?.let { onImport(it) } }
    )

    var reconnectCountdown by remember { mutableIntStateOf(5) }
    LaunchedEffect(reconnecting) {
        if (reconnecting) {
            reconnectCountdown = 5
            while (reconnectCountdown > 0) {
                delay(1.seconds)
                reconnectCountdown--
            }
        }
    }

    // ── Delete confirmation dialog ────────────────────────────────────────────

    if (showDeleteDialog && selectedProfile != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("確定刪除設定檔？") },
            text = { Text("\"${selectedProfile.name}\" 將被移除") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteProfile(selectedProfile.id)
                    selectedId = NEW_PROFILE.id
                    showDeleteDialog = false
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current

    fun hideKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
        val window = (context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.ime())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { hideKeyboard() }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("OBS Controller", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary)
        Text("Controller app for IRL Streaming.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (reconnecting) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        "Connection lost. Reconnecting in ${reconnectCountdown}s…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onCancelReconnect) {
                        Text("Cancel", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Profile chips ─────────────────────────────────────────────────────

        if (profiles.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                profiles.forEach { profile ->
                    ProfileChip(
                        label = profile.name,
                        selected = selectedId == profile.id,
                        onClick = { selectedId = profile.id },
                        onDelete = {
                            selectedId = profile.id
                            showDeleteDialog = true
                        }
                    )
                }

                // "+ New" chip
                FilterChip(
                    selected = isNewProfile,
                    onClick = { selectedId = NEW_PROFILE.id },
                    label = { Text("新增") },
                    shape = CircleShape,
                    leadingIcon = {
                        Icon(Icons.Default.Add, contentDescription = "New profile",
                            modifier = Modifier.size(16.dp))
                    }
                )
            }

            HorizontalDivider()
        }

        // ── Form ──────────────────────────────────────────────────────────────

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("設定檔名稱") },
            placeholder = { Text("e.g. Home, Studio") },
            singleLine = true,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("OBS Websocket IP 地址") },
            placeholder = { Text("192.168.1.100") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            shape = CircleShape,
            visualTransformation = if (showHost) VisualTransformation.None else PasswordVisualTransformation('•'),
            trailingIcon = {
                IconButton(onClick = { showHost = !showHost }) {
                    Icon(
                        imageVector = if (showHost) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showHost) "Hide IP" else "Show IP"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { c -> c.isDigit() } },
            label = { Text("Port") },
            placeholder = { Text("4455") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密碼 (選擇式)") },
            singleLine = true,
            shape = CircleShape,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide" else "Show"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // ── Action buttons ────────────────────────────────────────────────────

        val portInt = port.toIntOrNull() ?: DEFAULT_OBS_PORT
        val canAct = !isConnecting && host.isNotBlank()

        // Save profile button (only shown when name is filled)
        if (name.isNotBlank()) {
            OutlinedButton(
                onClick = {
                    val profile = if (isNewProfile)
                        OBSProfile(name = name, host = host, port = portInt, password = password)
                    else
                        selectedProfile!!.copy(name = name, host = host, port = portInt, password = password)
                    onSaveProfile(profile)
                    selectedId = profile.id
                },
                enabled = canAct,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isNewProfile) "另存為新的設定檔" else "儲存設定")
            }
        }

        // ── Import/Export settings ────────────────────────────────────────────

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { importLauncher.launch("*/*") },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("匯入設定", fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = { exportLauncher.launch("OBSController_Backup.bak") },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("匯出設定", fontSize = 13.sp)
            }
        }

        // ── Auto-connect toggle ───────────────────────────────────────────────

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("啟動時自動連線", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "自動連接至上次使用的設定檔",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = autoConnect, onCheckedChange = onAutoConnectChange)
        }

        Button(
            onClick = {
                val profile = if (!isNewProfile && selectedProfile != null)
                    selectedProfile.copy(host = host, port = portInt, password = password)
                else
                    OBSProfile(name = name.ifBlank { host }, host = host, port = portInt, password = password)
                onConnect(profile)
            },
            enabled = canAct,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("連線中...")
            } else {
                Text("連線")
            }
        }

        // Error card
        if (connectionState is ConnectionState.Error) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(connectionState.message, color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        Text(
            "請到OBS → 工具 → WebSocket 伺服器設定\n去尋找你的 IP, port 及 password.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfileChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = CircleShape,
        trailingIcon = {
            IconButton(onClick = onDelete, modifier = Modifier.size(18.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Delete $label",
                    modifier = Modifier.size(14.dp))
            }
        }
    )
}
