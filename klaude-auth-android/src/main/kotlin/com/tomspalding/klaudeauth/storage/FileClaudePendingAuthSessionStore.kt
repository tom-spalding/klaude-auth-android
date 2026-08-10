package com.tomspalding.klaudeauth.storage

import android.content.Context
import com.tomspalding.klaudeauth.model.ClaudePendingAuthSession
import com.tomspalding.klaudeauth.model.ClaudePendingAuthSessionJson
import java.io.File

class FileClaudePendingAuthSessionStore(
    private val file: File,
) : ClaudePendingAuthSessionStore {
    constructor(
        context: Context,
        fileName: String = "claude-pending-auth.json",
    ) : this(File(context.filesDir, fileName))

    override fun read(): ClaudePendingAuthSession? {
        if (!file.exists()) return null
        return ClaudePendingAuthSessionJson.decode(file.readText())
    }

    override fun write(session: ClaudePendingAuthSession) {
        file.parentFile?.mkdirs()
        file.writeText(ClaudePendingAuthSessionJson.encode(session))
    }

    override fun clear() {
        if (file.exists()) {
            file.delete()
        }
    }
}
