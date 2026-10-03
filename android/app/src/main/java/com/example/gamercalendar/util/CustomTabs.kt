package com.example.gamercalendar.util

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

fun openCustomTab(context: Context, url: String) {
    CustomTabsIntent.Builder()
        .build()
        .launchUrl(context, Uri.parse(url))
}
