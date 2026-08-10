package com.tomspalding.klaudeauth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

object ClaudeCustomTabs {
    fun launch(context: Context, url: String) {
        val uri = Uri.parse(url)
        val packageName = CustomTabsClient.getPackageName(context, null)
        if (packageName != null) {
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.intent.setPackage(packageName)
            customTabsIntent.launchUrl(context, uri)
        } else {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
