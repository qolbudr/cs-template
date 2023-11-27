package com.pusatfilm

import PusatfilmProvider
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class PusatfilmProviderPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(PusatfilmProvider())
        registerExtractorAPI(PusatfilmProvider.Uplayer())
    }
}