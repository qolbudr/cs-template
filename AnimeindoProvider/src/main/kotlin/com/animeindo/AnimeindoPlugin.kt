package com.animeindo

import AnimeindoProvider
import Mp4Upload
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AnimeindoProviderPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(AnimeindoProvider())
        registerExtractorAPI(Mp4Upload());
    }
}