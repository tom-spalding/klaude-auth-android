package com.tomspalding.klaudeauth.storage

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.tomspalding.klaudeauth.model.ClaudeCredentials
import java.io.File
import java.nio.charset.StandardCharsets

class EncryptedFileClaudeCredentialsStore(
    context: Context,
    fileName: String = "claude-credentials.encrypted",
) : ClaudeCredentialsStore {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, fileName)
    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private fun encryptedFile(): EncryptedFile = EncryptedFile.Builder(
        appContext,
        file,
        masterKey,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
    ).build()

    override fun read(): ClaudeCredentials? {
        if (!file.exists()) return null
        val raw = encryptedFile().openFileInput().use { input ->
            input.readBytes().toString(StandardCharsets.UTF_8)
        }
        return ClaudeCredentialsJson.decode(raw)
    }

    override fun write(credentials: ClaudeCredentials) {
        if (file.exists()) {
            file.delete()
        }
        val payload = ClaudeCredentialsJson.encode(credentials)
            .toByteArray(StandardCharsets.UTF_8)
        encryptedFile().openFileOutput().use { output ->
            output.write(payload)
        }
    }

    override fun clear() {
        if (file.exists()) {
            file.delete()
        }
    }
}
