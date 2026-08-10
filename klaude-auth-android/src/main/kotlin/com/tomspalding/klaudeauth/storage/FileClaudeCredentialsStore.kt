package com.tomspalding.klaudeauth.storage

import android.content.Context
import com.tomspalding.klaudeauth.model.ClaudeCredentials
import java.io.File

class FileClaudeCredentialsStore(
    context: Context,
    fileName: String = "claude-credentials.json",
) : ClaudeCredentialsStore {
    private val delegate = PathClaudeCredentialsStore(File(context.filesDir, fileName))

    override fun read(): ClaudeCredentials? = delegate.read()

    override fun write(credentials: ClaudeCredentials) = delegate.write(credentials)

    override fun clear() = delegate.clear()
}
