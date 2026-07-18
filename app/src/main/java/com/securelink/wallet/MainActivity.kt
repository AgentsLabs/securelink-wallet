package com.securelink.wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SecureLinkApp(viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureLinkApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableStateOf(Tab.Chats) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = { TopAppBar(title = { Text("SecureLink Wallet") }) },
                bottomBar = {
                    NavigationBar {
                        Tab.entries.forEach {
                            NavigationBarItem(
                                selected = tab == it,
                                onClick = { tab = it },
                                label = { Text(it.label) },
                                icon = {},
                            )
                        }
                    }
                },
            ) { padding ->
                Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                    when (tab) {
                        Tab.Chats -> ChatsScreen(state, viewModel::sendMessage)
                        Tab.Calls -> CallsScreen(state.p2pStatus, viewModel::refreshInvite)
                        Tab.Wallet -> WalletScreen(state.documents, viewModel::addDemoDocument)
                        Tab.Passwords -> PasswordsScreen(state.credentials, viewModel::addGeneratedCredential)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatsScreen(state: AppState, onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    Text("Local-first encrypted chat", style = MaterialTheme.typography.titleLarge)
    Text("Peer: ${state.contacts.firstOrNull()?.name ?: "No peer"}")
    Spacer(Modifier.height(12.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.messages) { message ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (message.sentByMe) "Me: ${message.text}" else "Peer: ${message.text}",
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth(0.65f), placeholder = { Text("Message") })
        Button(onClick = {
            onSend(draft)
            draft = ""
        }) { Text("Send") }
    }
}

@Composable
private fun CallsScreen(status: String, onCreateInvite: () -> Unit) {
    Text("P2P audio/video", style = MaterialTheme.typography.titleLarge)
    Text("MVP uses manual invite exchange; WebRTC media wiring is the next hardening step.")
    Spacer(Modifier.height(12.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(status, modifier = Modifier.padding(12.dp))
    }
    Spacer(Modifier.height(12.dp))
    Button(onClick = onCreateInvite) { Text("Create peer invite") }
}

@Composable
private fun WalletScreen(documents: List<WalletDocument>, onAddDocument: () -> Unit) {
    Text("Personal document wallet", style = MaterialTheme.typography.titleLarge)
    Button(onClick = onAddDocument) { Text("Add demo document") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(documents) { doc ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(doc.title, style = MaterialTheme.typography.titleMedium)
                    Text("${doc.kind} · ${doc.note}")
                }
            }
        }
    }
}

@Composable
private fun PasswordsScreen(credentials: List<CredentialEntry>, onGenerate: () -> Unit) {
    Text("Credential manager", style = MaterialTheme.typography.titleLarge)
    Button(onClick = onGenerate) { Text("Generate saved credential") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(credentials) { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(item.label, style = MaterialTheme.typography.titleMedium)
                    Text(item.username)
                    Text(item.url)
                    Text(item.password)
                }
            }
        }
    }
}

private enum class Tab(val label: String) {
    Chats("Chats"),
    Calls("Calls"),
    Wallet("Wallet"),
    Passwords("Passwords"),
}
