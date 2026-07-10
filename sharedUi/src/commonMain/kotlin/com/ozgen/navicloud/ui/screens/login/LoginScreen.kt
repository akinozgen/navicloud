package com.ozgen.navicloud.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ozgen.navicloud.ui.containerViewModel
import com.ozgen.navicloud.ui.i18n.LocalStrings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ozgen.navicloud.data.ServerSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(
    private val servers: ServerSource,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

    fun connect(name: String, url: String, username: String, password: String, connectionFailedMessage: String) {
        viewModelScope.launch {
            _state.value = LoginUiState(loading = true)
            runCatching {
                servers.addServer(name.ifBlank { "Navidrome" }, url.trim(), username.trim(), password)
            }.onSuccess {
                _state.value = LoginUiState()
            }.onFailure { e ->
                _state.value = LoginUiState(error = e.message ?: connectionFailedMessage)
            }
        }
    }
}

@Composable
fun LoginScreen(vm: LoginViewModel = containerViewModel { LoginViewModel(it.servers) }) {
    val strings = LocalStrings.current
    val state by vm.state.collectAsStateWithLifecycle()
    val name = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val url = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val username = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val password = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("NaviCloud", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(
            strings.loginSubtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = name.value,
            onValueChange = { name.value = it },
            label = { Text(strings.loginServerName) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = url.value,
            onValueChange = { url.value = it },
            label = { Text(strings.loginServerUrl) },
            placeholder = { Text(strings.loginServerUrlHint) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = username.value,
            onValueChange = { username.value = it },
            label = { Text(strings.loginUsername) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password.value,
            onValueChange = { password.value = it },
            label = { Text(strings.loginPassword) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { vm.connect(name.value, url.value, username.value, password.value, strings.loginConnectionFailed) },
            enabled = !state.loading && url.value.isNotBlank() && username.value.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp).fillMaxWidth(0.1f), strokeWidth = 2.dp)
            } else {
                Text(strings.loginConnect)
            }
        }
    }
}
