package com.tomspalding.klaudeauth

import com.tomspalding.klaudeauth.model.ClaudeAuthState
import com.tomspalding.klaudeauth.model.ClaudeCredentials
import com.tomspalding.klaudeauth.model.ClaudeOAuthTokens
import com.tomspalding.klaudeauth.model.needsRefresh
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ClaudeAuthSessionTest {
    private val existing = credentials("existing-access", "existing-refresh")
    private val refreshed = credentials("new-access", "new-refresh")

    @Test
    fun loadHydratesSignedInFromRepository() = runBlocking {
        val repository = PlaceholderClaudeAuthRepository()
        repository.saveCredentials(existing)
        val session = ClaudeAuthSession(
            authRepository = repository,
            authClient = FakeClaudeAuthClient(),
            launchBrowser = ClaudeBrowserLauncher { },
        )

        session.load()

        val state = session.authState.first()
        assertTrue(state is ClaudeAuthState.SignedIn)
        assertEquals(existing, (state as ClaudeAuthState.SignedIn).credentials)
    }

    @Test
    fun connectSavesCredentialsOnSuccess() = runBlocking {
        val repository = PlaceholderClaudeAuthRepository()
        val client = FakeClaudeAuthClient(success = refreshed)
        val session = ClaudeAuthSession(
            authRepository = repository,
            authClient = client,
            launchBrowser = ClaudeBrowserLauncher { },
        )

        session.connect()

        assertEquals(refreshed, repository.loadCredentials())
        val state = session.authState.first()
        assertTrue(state is ClaudeAuthState.SignedIn)
        assertEquals(refreshed, (state as ClaudeAuthState.SignedIn).credentials)
    }

    @Test
    fun connectSetsFailedAndDoesNotSaveOnError() = runBlocking {
        val repository = PlaceholderClaudeAuthRepository()
        val session = ClaudeAuthSession(
            authRepository = repository,
            authClient = FakeClaudeAuthClient(error = IllegalStateException("HTTP 401: nope")),
            launchBrowser = ClaudeBrowserLauncher { },
            defaultFailureMessage = "fallback failure",
        )

        session.connect()

        assertNull(repository.loadCredentials())
        val state = session.authState.first()
        assertTrue(state is ClaudeAuthState.Failed)
        assertEquals("HTTP 401: nope", (state as ClaudeAuthState.Failed).message)
    }

    @Test
    fun disconnectClearsCredentials() = runBlocking {
        val repository = PlaceholderClaudeAuthRepository()
        repository.saveCredentials(existing)
        val session = ClaudeAuthSession(
            authRepository = repository,
            authClient = FakeClaudeAuthClient(),
            launchBrowser = ClaudeBrowserLauncher { },
        )

        session.load()
        session.disconnect()

        assertNull(repository.loadCredentials())
        assertEquals(ClaudeAuthState.SignedOut, session.authState.first())
    }

    private fun credentials(access: String, refresh: String) = ClaudeCredentials(
        ClaudeOAuthTokens(
            accessToken = access,
            refreshToken = refresh,
            expiresAt = Instant.parse("2030-01-01T00:00:00Z"),
        ),
    )

    private class FakeClaudeAuthClient(
        private val success: ClaudeCredentials? = null,
        private val error: Throwable? = null,
    ) : ClaudeAuthClient {
        override fun beginSignIn(redirectUri: String) = error("not used")

        override suspend fun completeSignIn(
            session: com.tomspalding.klaudeauth.model.ClaudePendingAuthSession,
            callback: com.tomspalding.klaudeauth.browser.ClaudeCallbackResult,
        ): ClaudeCredentials = error("not used")

        override suspend fun completeSignInFromRedirectUrl(
            session: com.tomspalding.klaudeauth.model.ClaudePendingAuthSession,
            redirectUrl: String,
        ): ClaudeCredentials = error("not used")

        override suspend fun signIn(launchBrowser: ClaudeBrowserLauncher): ClaudeCredentials =
            refreshOrSignIn(null, launchBrowser)

        override suspend fun refresh(credentials: ClaudeCredentials): ClaudeCredentials =
            success ?: throw error ?: error("no result")

        override suspend fun ensureFresh(
            credentials: ClaudeCredentials,
            refreshMargin: java.time.Duration,
        ): ClaudeCredentials =
            if (credentials.needsRefresh(refreshMargin)) {
                refresh(credentials)
            } else {
                credentials
            }

        override suspend fun refreshOrSignIn(
            current: ClaudeCredentials?,
            launchBrowser: ClaudeBrowserLauncher,
        ): ClaudeCredentials {
            error?.let { throw it }
            return success ?: error("no result")
        }
    }
}