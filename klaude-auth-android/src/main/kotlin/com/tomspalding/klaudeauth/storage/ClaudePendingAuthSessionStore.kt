package com.tomspalding.klaudeauth.storage

import com.tomspalding.klaudeauth.model.ClaudePendingAuthSession

interface ClaudePendingAuthSessionStore {
    fun read(): ClaudePendingAuthSession?

    fun write(session: ClaudePendingAuthSession)

    fun clear()
}
