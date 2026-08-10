package com.tomspalding.klaudeauth

import com.tomspalding.klaudeauth.model.ClaudeAuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class ClaudeAuthSession(
    private val authRepository: ClaudeAuthRepository,
    private val authClient: ClaudeAuthClient,
    private val launchBrowser: ClaudeBrowserLauncher,
    private val defaultFailureMessage: String = "Auth failed. Try again.",
) {
    private val uiOverride = MutableStateFlow<ClaudeAuthState?>(null)

    val authState: Flow<ClaudeAuthState> = combine(
        authRepository.authState,
        uiOverride,
    ) { repositoryState, override ->
        override ?: repositoryState
    }

    suspend fun load() {
        authRepository.loadCredentials()
    }

    suspend fun connect() {
        uiOverride.value = ClaudeAuthState.Loading
        runCatching {
            authClient.refreshOrSignIn(authRepository.loadCredentials(), launchBrowser)
        }.onSuccess { credentials ->
            authRepository.saveCredentials(credentials)
            uiOverride.value = null
        }.onFailure { error ->
            uiOverride.value = ClaudeAuthState.Failed(
                message = error.message?.takeIf { it.isNotBlank() } ?: defaultFailureMessage,
                cause = error,
            )
        }
    }

    suspend fun disconnect() {
        authRepository.clearCredentials()
        uiOverride.value = null
    }
}
