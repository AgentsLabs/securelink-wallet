package com.securelink.wallet

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class MainActivity : FragmentActivity() {
    private val viewModel by lazy { ViewModelProvider(this)[AppViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { SecureLinkApp(viewModel, ::authenticateVault) }
        })
    }

    override fun onStop() {
        viewModel.lockVault()
        super.onStop()
    }

    private fun authenticateVault() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val manager = BiometricManager.from(this)
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            viewModel.reportVaultAuthenticationUnavailable()
            return
        }
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    viewModel.unlockVault()
                }
            },
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock credential vault")
                .setSubtitle("Confirm your identity to view saved passwords")
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

}

@Composable
fun SecureLinkApp(viewModel: AppViewModel, onUnlockVault: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableStateOf(Tab.Chats) }
    val context = LocalContext.current
    val mediaPermissions = remember { arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.updateMediaPermission(result.values.all { it })
    }

    LaunchedEffect(Unit) {
        val granted = mediaPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        viewModel.updateMediaPermission(granted)
    }

    val colors = secureLinkColors()
    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
            Scaffold(
                containerColor = colors.background,
                bottomBar = {
                    NavigationBar(containerColor = colors.surface) {
                        Tab.entries.forEach {
                            NavigationBarItem(
                                selected = tab == it,
                                onClick = { tab = it },
                                label = { Text(it.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                icon = { Text(it.symbol, fontWeight = FontWeight.Bold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.primary,
                                    selectedTextColor = colors.primary,
                                    indicatorColor = colors.primaryContainer,
                                ),
                            )
                        }
                    }
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .background(colors.background)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Header(state)
                    when (tab) {
                        Tab.Chats -> ChatsScreen(state, viewModel::selectContact, viewModel::addContact, viewModel::sendMessage)
                        Tab.Calls -> CallsScreen(
                            state = state,
                            onRequestPermissions = { permissionLauncher.launch(mediaPermissions) },
                            onCreateInvite = viewModel::createCallInvite,
                            onApplySignal = viewModel::applyCallSignal,
                            onEndCall = viewModel::endCall,
                            localTrack = viewModel.callManager.localVideoTrack,
                            remoteTrack = viewModel.callManager.remoteVideoTrack,
                            eglContext = viewModel.callManager.eglContext,
                        )
                        Tab.Wallet -> WalletScreen(state.documents, viewModel::addDemoDocument)
                        Tab.Passwords -> PasswordsScreen(
                            credentials = state.credentials,
                            unlocked = state.vaultUnlocked,
                            vaultMessage = state.vaultMessage,
                            onUnlock = onUnlockVault,
                            onLock = viewModel::lockVault,
                            onGenerate = viewModel::addGeneratedCredential,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: AppState) {
    val selected = state.contacts.firstOrNull { it.id == state.selectedContactId } ?: state.contacts.firstOrNull()
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(Color(0xFF3B176D), Color(0xFF7C3AED))))
                .padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SecureLink Wallet", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "Private chat, calling, documents, and credentials for ${selected?.name ?: "your trusted peer"}.",
                    color = Color(0xFFF3E8FF),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill("Local vault")
                    StatusPill(if (state.mediaPermissionGranted) "Camera/mic ready" else "Permissions needed")
                    StatusPill(state.callStage.label())
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

private fun CallStage.label(): String = when (this) {
    CallStage.Idle -> "Ready"
    CallStage.Gathering -> "Preparing"
    CallStage.WaitingForPeer -> "Waiting"
    CallStage.Connecting -> "Connecting"
    CallStage.InCall -> "Live"
    CallStage.Failed -> "Retry"
}

@Composable
private fun ColumnScope.ChatsScreen(
    state: AppState,
    onSelectContact: (Long) -> Unit,
    onAddContact: (String) -> Unit,
    onSend: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var newContact by remember { mutableStateOf("") }
    val selected = state.contacts.firstOrNull { it.id == state.selectedContactId } ?: state.contacts.firstOrNull()

    SectionTitle("Contacts", "Choose who this device is linked to")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        items(state.contacts) { contact ->
            ContactChip(contact, selected = contact.id == selected?.id, onClick = { onSelectContact(contact.id) })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newContact,
            onValueChange = { newContact = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Add trusted contact") },
            singleLine = true,
        )
        Button(
            onClick = {
                onAddContact(newContact)
                newContact = ""
            },
            shape = RoundedCornerShape(8.dp),
        ) { Text("Add") }
    }

    SectionTitle("Encrypted chat", selected?.deviceId ?: "No peer selected")
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.weight(1f),
    ) {
        items(state.messages) { message ->
            MessageBubble(message)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message ${selected?.name ?: "peer"}") },
            singleLine = true,
        )
        Button(
            onClick = {
                onSend(draft)
                draft = ""
            },
            shape = RoundedCornerShape(8.dp),
        ) { Text("Send") }
    }
}

@Composable
private fun ContactChip(contact: Contact, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color(0xFFE9D5FF))
    Card(
        modifier = Modifier
            .width(178.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        border = border,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(contact.name)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(contact.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(contact.trustLevel, style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B6477), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Avatar(name: String) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color(0xFFEDE9FE)),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), color = Color(0xFF5B21B6), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.sentByMe) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.sentByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier.fillMaxWidth(0.82f),
        ) {
            Text(
                text = if (message.sentByMe) message.text else "Peer: ${message.text}",
                modifier = Modifier.padding(12.dp),
                color = if (message.sentByMe) Color.White else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CallsScreen(
    state: AppState,
    onRequestPermissions: () -> Unit,
    onCreateInvite: () -> Unit,
    onApplySignal: (String) -> Unit,
    onEndCall: () -> Unit,
    localTrack: VideoTrack?,
    remoteTrack: VideoTrack?,
    eglContext: org.webrtc.EglBase.Context,
) {
    var signalDraft by remember { mutableStateOf("") }
    val selected = state.contacts.firstOrNull { it.id == state.selectedContactId } ?: state.contacts.firstOrNull()
    val context = LocalContext.current

    SectionTitle("Audio/video calling", "Direct WebRTC call with ${selected?.name ?: "selected peer"}")
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFFE9D5FF)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CallPreview(state, localTrack, remoteTrack, eglContext)
            Text(state.p2pStatus, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4C3D5E))
            if (!state.mediaPermissionGranted) {
                OutlinedButton(onClick = onRequestPermissions, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Allow camera and microphone")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onCreateInvite,
                    enabled = state.mediaPermissionGranted,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("Create invite") }
                state.localCallPayload?.let { payload ->
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard.setPrimaryClip(ClipData.newPlainText("SecureLink call signal", payload))
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text("Copy signal") }
                }
            }
            HorizontalDivider()
            OutlinedTextField(
                value = signalDraft,
                onValueChange = { signalDraft = it },
                placeholder = { Text("Paste SecureLink invite or answer") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        onApplySignal(signalDraft)
                        signalDraft = ""
                    },
                    enabled = state.mediaPermissionGranted,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("Apply signal") }
                TextButton(onClick = onEndCall, modifier = Modifier.weight(1f)) {
                    Text("End session")
                }
            }
        }
    }
}

@Composable
private fun CallPreview(
    state: AppState,
    localTrack: VideoTrack?,
    remoteTrack: VideoTrack?,
    eglContext: org.webrtc.EglBase.Context,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1E1038), Color(0xFF6D28D9)))),
    ) {
        if (state.mediaPermissionGranted && localTrack != null) {
            WebRtcVideoRenderer(remoteTrack ?: localTrack, eglContext, mirror = remoteTrack == null)
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = if (state.callStage == CallStage.InCall) 0.12f else 0f)),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when (state.callStage) {
                    CallStage.Idle -> "Ready"
                    CallStage.Gathering -> "Preparing"
                    CallStage.WaitingForPeer -> "Waiting"
                    CallStage.Connecting -> "Connecting"
                    CallStage.InCall -> "Live"
                    CallStage.Failed -> "Retry"
                },
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (state.mediaPermissionGranted) "Camera and microphone are available" else "Grant media permissions to place calls",
                color = Color(0xFFF3E8FF),
            )
        }
    }
}

@Composable
private fun WebRtcVideoRenderer(track: VideoTrack, eglContext: org.webrtc.EglBase.Context, mirror: Boolean) {
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            SurfaceViewRenderer(viewContext).apply {
                init(eglContext, null)
                setEnableHardwareScaler(true)
                setMirror(mirror)
                renderer = this
            }
        },
        update = { it.setMirror(mirror) },
    )
    DisposableEffect(track, renderer) {
        renderer?.let(track::addSink)
        onDispose { renderer?.let(track::removeSink) }
    }
}

@Composable
private fun ColumnScope.WalletScreen(documents: List<WalletDocument>, onAddDocument: () -> Unit) {
    SectionTitle("Personal document wallet", "Identity records stay on this phone")
    Button(onClick = onAddDocument, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Add demo document")
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
        items(documents) { doc ->
            ListCard(title = doc.title, subtitle = doc.kind, body = doc.note)
        }
    }
}

@Composable
private fun ColumnScope.PasswordsScreen(
    credentials: List<CredentialEntry>,
    unlocked: Boolean,
    vaultMessage: String?,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
    onGenerate: () -> Unit,
) {
    SectionTitle("Credential manager", if (unlocked) "Vault unlocked for this session" else "Unlock with biometrics or device credential")
    if (unlocked) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onGenerate, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                Text("Generate saved credential")
            }
            OutlinedButton(onClick = onLock, shape = RoundedCornerShape(8.dp)) { Text("Lock") }
        }
    } else {
        Button(onClick = onUnlock, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Unlock credential vault")
        }
        vaultMessage?.let { Text(it, color = Color(0xFF7E22CE), style = MaterialTheme.typography.bodyMedium) }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
        items(credentials) { item ->
            ListCard(title = item.label, subtitle = item.username, body = "${item.url}\n${item.password}")
        }
    }
}

@Composable
private fun ListCard(title: String, subtitle: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFFE9D5FF)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text(body, color = Color(0xFF4C3D5E), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color(0xFF6B6477), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun secureLinkColors() = MaterialTheme.colorScheme.copy(
    primary = Color(0xFF6D28D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = Color(0xFF9333EA),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8FF),
    background = Color(0xFFFCFAFF),
    onBackground = Color(0xFF241B2E),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF241B2E),
    surfaceVariant = Color(0xFFF3E8FF),
    outline = Color(0xFF9586A6),
)

private enum class Tab(val label: String, val symbol: String) {
    Chats("Chats", "C"),
    Calls("Calls", "A"),
    Wallet("Wallet", "W"),
    Passwords("Passwords", "P"),
}
