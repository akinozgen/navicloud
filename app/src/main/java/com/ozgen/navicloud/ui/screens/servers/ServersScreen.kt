package com.ozgen.navicloud.ui.screens.servers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ozgen.navicloud.core.model.Server
import com.ozgen.navicloud.data.ServerRepository
import com.ozgen.navicloud.ui.screens.login.LoginScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServersUiState(
    val servers: List<Server> = emptyList(),
    val activeId: Long? = null,
)

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val repo: ServerRepository,
) : ViewModel() {
    val state: StateFlow<ServersUiState> =
        combine(repo.servers, repo.activeServer) { servers, active ->
            ServersUiState(servers, active?.id)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ServersUiState())

    fun setActive(id: Long) = viewModelScope.launch { repo.setActive(id) }
    fun remove(id: Long) = viewModelScope.launch { repo.removeServer(id) }
}

@Composable
fun ServersScreen(navController: NavController, vm: ServersViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    if (adding) {
        // Reuses the login form; a successful connect makes the new server active.
        LoginScreen()
        return
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Geri")
            }
            Text(
                "Sunucular",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { adding = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Sunucu ekle")
            }
        }
        LazyColumn {
            items(state.servers.size) { i ->
                val server = state.servers[i]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.setActive(server.id) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(
                        selected = server.id == state.activeId,
                        onClick = { vm.setActive(server.id) },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(server.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${server.username} @ ${server.baseUrl}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.servers.size > 1) {
                        IconButton(onClick = { vm.remove(server.id) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Sil",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
