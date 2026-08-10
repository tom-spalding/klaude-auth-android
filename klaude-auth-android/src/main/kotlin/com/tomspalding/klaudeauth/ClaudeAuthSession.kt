package com.tomspalding.klaudeauth

import com.tomspalding.klaudeauth.model.ClaudeAuthState
import com.tomspalding.klaudeauth.storage.ClaudePendingAuthSessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class ClaudeAuthSession(
    private val authRepository: ClaudeAuthRepository,
    private val authClient: ClaudeAuthClient,
    private val launchBrowser: ClaudeBrowserLauncher,
    private val pendingStore: ClaudePendingAuthSessionStore,
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
            val current = authRepository.loadCredentials()
                ?: error("Not signed in")
            authClient.ensureFresh(current)
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

    suspend fun startSignIn(redirectUri: String) {
        uiOverride.value = ClaudeAuthState.Loading
        runCatching {
            val begun = authClient.beginSignIn(redirectUri)
            pendingStore.write(begun.session)
            launchBrowser.open(begun.authorizeUrl)
        }.onFailure { error ->
            uiOverride.value = ClaudeAuthState.Failed(
                message = error.message?.takeIf { it.isNotBlank() } ?: defaultFailureMessage,
                cause = error,
            )
        }
    }

    suspend fun finishSignIn(redirectUrl: String) {
        uiOverride.value = ClaudeAuthState.Loading
        runCatching {
            val session = pendingStore.read()
                ?: error("No pending sign-in session")
            val credentials = authClient.completeSignInFromRedirectUrl(session, redirectUrl)
            authRepository.saveCredentials(credentials)
            pendingStore.clear()
            uiOverride.value = null
        }.onFailure { error ->
            uiOverride.value = ClaudeAuthState.Failed(
                message = error.message?.takeIf { it.isNotBlank() } ?: defaultFailureMessage,
                cause = error,
            )
        }
    }

    suspend fun cancelSignIn() {
        pendingStore.clear()
        uiOverride.value = null
    }

    suspend fun disconnect() {
        authRepository.clearCredentials()
        uiOverride.value = null
    }
}
