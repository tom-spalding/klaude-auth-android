package com.tomspalding.klaudeauth.storage

import com.tomspalding.klaudeauth.model.ClaudePendingAuthSession
import com.tomspalding.klaudeauth.model.ClaudePendingAuthSessionJson
import com.tomspalding.klaudeauth.model.ClaudePkce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileClaudePendingAuthSessionStoreTest {
    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun writeReadAndClearRoundTrip() {
        val file = tempDir.newFile("pending.json")
        val store = FileClaudePendingAuthSessionStore(file)
        val session = ClaudePendingAuthSession(
            pkce = ClaudePkce(verifier = "verifier", challenge = "challenge"),
            state = "state-123",
            redirectUri = "myapp://oauth/callback",
        )

        store.write(session)
        assertEquals(session, store.read())
        assertEquals(session, ClaudePendingAuthSessionJson.decode(file.readText()))

        store.clear()
        assertNull(store.read())
    }
}
