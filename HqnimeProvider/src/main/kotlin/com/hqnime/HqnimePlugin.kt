package com.hqnime

import Blogger
import HqnimeProvider
import Mp4Upload
import PPlayer
import Yourupload
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class HqnimeProviderPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(HqnimeProvider())
        registerExtractorAPI(PPlayer())
        registerExtractorAPI(Blogger())
        registerExtractorAPI(Yourupload())
        registerExtractorAPI(Mp4Upload())
    }
}