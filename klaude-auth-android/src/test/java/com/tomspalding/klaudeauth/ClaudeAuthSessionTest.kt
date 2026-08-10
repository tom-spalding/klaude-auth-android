package com.tomspalding.klaudeauth

import com.tomspalding.klaudeauth.browser.ClaudeCallbackResult
import com.tomspalding.klaudeauth.model.BeginSignInResult
import com.tomspalding.klaudeauth.model.ClaudeAuthState
import com.tomspalding.klaudeauth.model.ClaudeCredentials
import com.tomspalding.klaudeauth.model.ClaudeOAuthTokens
import com.tomspalding.klaudeauth.model.ClaudePendingAuthSession
import com.tomspalding.klaudeauth.model.ClaudePkce
import com.tomspalding.klaudeauth.storage.ClaudePendingAuthSessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class ClaudeAuthSessionTest {
    private val existing = credentials("existing-access", "existing-refresh")
    private val refreshed = credentials("new-access", "new-refresh")
    private val pendingSession = ClaudePendingAuthSession(
        pkce = ClaudePkce(verifier = "v", challenge = "c"),
        state = "state-1",
        redirectUri = "myapp://oauth/callback",
    )

    @Test
    fun loadHydratesSignedInFromRepository() = runBlocking {
        val repository = PlaceholderClaudeAuthRepository()
        repository.saveCredentials(existing)
        val session = session(repository = repository)

        session.load()

        val state = session.authState.first()
        assertTrue(state is ClaudeAuthState.SignedIn)
        assertEquals(existing, (state as ClaudeAuthState.SignedIn).credentials)
    }

    @Test
    fun connectSavesCredentialsOnSuccess() = runBlocking {
        val repository = PlaceholderClaudeAuthRepository()
        repository.saveCredentials(existing)
        val session = session(
            repository = repository,
            client = FakeClaudeAuthClient(success = refreshed),
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
        repository.saveCredentials(existing)
        val session = session(
            repository = repository,
            client = FakeClaudeAuthClient(error = IllegalStateException("HTTP 401: nope")),
            defaultFailureMessage = "fallback failure",
        )

        session.connect()

        assertEquals(existing, repository.loadCredentials())
        val state = session.authState.first()
        assertTrue(state is ClaudeAuthState.Failed)
        assertEquals("HTTP 401: nope", (state as ClaudeAuthState.Failed).message)
    }

    @Test
    fun connectFailsWhenNotSignedIn() = runBlocking {
        val repository = PlaceholderClaudeAuthRepository()
        val browserOpens = AtomicReference(0)
        val session = session(
            repository = repository,
            launchBrowser = ClaudeBrowserLauncher { browserOpens.set(browserOpens.get() + 1) },
        )

        session.connect()

        assertNull(repository.loadCredentials())
        assertEquals(0, browserOpens.get())
        val state = session.authState.first()
        assertTrue(state is ClaudeAuthState.Failed)
        assertTrue((state as ClaudeAuthState.Failed).message.contains("Not signed in"))
    }

    @Test
    fun disconnectClearsCredentials() = runBlocking {
        val repository = PlaceholderClaudeAuthRepository()
        repository.saveCredentials(existing)
        val session = session(repository = repository)

        session.load()
        session.disconnect()

        assertNull(repository.loadCredentials())
        assertEquals(ClaudeAuthState.SignedOut, session.authState.first())
    }

    @Test
    fun startSignInPersistsPendingAndOpensBrowser() = runBlocking {
        val pending = InMemoryPendingStore()
        val opened = AtomicReference<String?>(null)
        val client = FakeClaudeAuthClient(
            beginResult = BeginSignInResult(
                session = pendingSession,
                authorizeUrl = "https://example.com/authorize",
            ),
        )
        val session = session(
            client = client,
            pendingStore = pending,
            launchBrowser = ClaudeBrowserLauncher { opened.set(it) },
        )

        session.startSignIn("myapp://oauth/callback")

        assertEquals(pendingSession, pending.read())
        assertEquals("https://example.com/authorize", opened.get())
        assertEquals(ClaudeAuthState.Loading, session.authState.first())
    }

    @Test
    fun finishSignInSavesCredentialsAndClearsPending() = runBlocking {
        val repository = PlaceholderClaudeAuthRepository()
        val pending = InMemoryPendingStore().apply { write(pendingSession) }
        val session = session(
            repository = repository,
            client = FakeClaudeAuthClient(success = refreshed),
            pendingStore = pending,
        )

        session.finishSignIn("myapp://oauth/callback?code=abc&state=state-1")

        assertEquals(refreshed, repository.loadCredentials())
        assertNull(pending.read())
        val state = session.authState.first()
        assertTrue(state is ClaudeAuthState.SignedIn)
        assertEquals(refreshed, (state as ClaudeAuthState.SignedIn).credentials)
    }

    @Test
    fun finishSignInFailsWhenPendingMissing() = runBlocking {
        val repository = PlaceholderClaudeAuthRepository()
        val session = session(repository = repository)

        session.finishSignIn("myapp://oauth/callback?code=abc&state=state-1")

        assertNull(repository.loadCredentials())
        val state = session.authState.first()
        assertTrue(state is ClaudeAuthState.Failed)
        assertTrue((state as ClaudeAuthState.Failed).message.contains("pending"))
    }

    @Test
    fun cancelSignInClearsPending() = runBlocking {
        val pending = InMemoryPendingStore().apply { write(pendingSession) }
        val session = session(pendingStore = pending)
        session.startSignIn("myapp://oauth/callback")

        session.cancelSignIn()

        assertNull(pending.read())
        assertEquals(ClaudeAuthState.SignedOut, session.authState.first())
    }

    private fun session(
        repository: ClaudeAuthRepository = PlaceholderClaudeAuthRepository(),
        client: ClaudeAuthClient = FakeClaudeAuthClient(),
        pendingStore: ClaudePendingAuthSessionStore = InMemoryPendingStore(),
        launchBrowser: ClaudeBrowserLauncher = ClaudeBrowserLauncher { },
        defaultFailureMessage: String = "Auth failed. Try again.",
    ) = ClaudeAuthSession(
        authRepository = repository,
        authClient = client,
        launchBrowser = launchBrowser,
        pendingStore = pendingStore,
        defaultFailureMessage = defaultFailureMessage,
    )

    private fun credentials(access: String, refresh: String) = ClaudeCredentials(
        ClaudeOAuthTokens(
            accessToken = access,
            refreshToken = refresh,
            expiresAt = Instant.parse("2030-01-01T00:00:00Z"),
        ),
    )

    private class InMemoryPendingStore : ClaudePendingAuthSessionStore {
        private var session: ClaudePendingAuthSession? = null

        override fun read(): ClaudePendingAuthSession? = session

        override fun write(session: ClaudePendingAuthSession) {
            this.session = session
        }

        override fun clear() {
            session = null
        }
    }

    private class FakeClaudeAuthClient(
        private val success: ClaudeCredentials? = null,
        private val error: Throwable? = null,
        private val beginResult: BeginSignInResult? = null,
    ) : ClaudeAuthClient {
        override fun beginSignIn(redirectUri: String): BeginSignInResult =
            beginResult ?: BeginSignInResult(
                session = ClaudePendingAuthSession(
                    pkce = ClaudePkce(verifier = "v", challenge = "c"),
                    state = "state",
                    redirectUri = redirectUri,
                ),
                authorizeUrl = "https://example.com/authorize",
            )

        override suspend fun completeSignIn(
            session: ClaudePendingAuthSession,
            callback: ClaudeCallbackResult,
        ): ClaudeCredentials = error("not used")

        override suspend fun completeSignInFromRedirectUrl(
            session: ClaudePendingAuthSession,
            redirectUrl: String,
        ): ClaudeCredentials {
            error?.let { throw it }
            return success ?: error("no result")
        }

        override suspend fun signIn(launchBrowser: ClaudeBrowserLauncher): ClaudeCredentials =
            refreshOrSignIn(null, launchBrowser)

        override suspend fun refresh(credentials: ClaudeCredentials): ClaudeCredentials =
            success ?: throw error ?: error("no result")

        override suspend fun ensureFresh(
            credentials: ClaudeCredentials,
            refreshMargin: java.time.Duration,
        ): ClaudeCredentials {
            error?.let { throw it }
            return success ?: credentials
        }

        override suspend fun refreshOrSignIn(
            current: ClaudeCredentials?,
            launchBrowser: ClaudeBrowserLauncher,
        ): ClaudeCredentials {
            error?.let { throw it }
            return success ?: error("no result")
        }

        override suspend fun fetchUsage(
            credentials: ClaudeCredentials,
        ): com.tomspalding.klaudeauth.model.ClaudeUsageSnapshot {
            error?.let { throw it }
            return com.tomspalding.klaudeauth.model.ClaudeUsageSnapshot()
        }
    }
}
