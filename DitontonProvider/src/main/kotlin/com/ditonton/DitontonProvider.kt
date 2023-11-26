package com.ditonton

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class DitontonProvider : MainAPI() {
    override var mainUrl = "https://ditonton.bid"
    override var name = "Ditonton Site"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "id"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
            "$mainUrl/latest/page/" to "Terbaru",
            "$mainUrl/populer/page/" to "Populer",
            "$mainUrl/trending-minggu-ini/page/" to "Trending",
    )

    // Get Main Page
    override suspend fun getMainPage(page: Int, request: MainPageRequest) : HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("article.grid-item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    // Search Item
    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query#gsc.tab=0&gsc.q=$query&gsc.page=1").document
        val result = document.select("article.grid-item").mapNotNull {
            it.toSearchResult()
        }
        return result;
    }

    // To Search Response
    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("a")?.attr("title")?.replace("Nonton Film ", "")?.replace(" Subtitle Indonesia Streaming Movie Download", "")?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        val quality = this.select("span.quality").text().trim()
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            addQuality(quality)
        }
    }

    // Movie Detail
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url ?: return null).document
        val title = document.selectFirst(".entry-header a")?.text()?.replace("Nonton Film ", "")?.replace(" Subtitle Indonesia Streaming Movie Download", "")?.trim().toString()
        val poster = document.selectFirst(".entry-content img")?.attr("src").toString()
        val tags = (document.selectFirst("table td:nth-child(2)")?.text() ?: "").split(", ").map { it }
        val year = title.split("(").last().replace(")", "")
        val description = document.selectFirst("#movie-synopsis p")?.text()?.trim()
        val trailer = document.selectFirst("div.action-player li > a.fancybox")?.attr("href")
        val rating = document.selectFirst("table tr:nth-child(6) td")?.text()?.split(" ")?.first().toRatingInt()
        val actors = document.selectFirst("table tr:nth-child(2) td")?.text()?.split(", ")?.map { it }
        val tvType = TvType.Movie
        val recommendation = document.select("section.related .grid-item").mapNotNull {
            it.toSearchResult()
        }


        return newMovieLoadResponse(title, url, tvType, url) {
            this.posterUrl = poster
            this.year = year.toIntOrNull()
            this.plot = description
            this.tags = tags
            this.rating = rating
            addActors(actors)
            this.recommendations = recommendation
            addTrailer(trailer)
        }
    }

    // loadlink
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
//        val document = app.get(data).document
//        document.select("[target=iframe]").map {
//            fixUrl(it.attr("href"))
//        }.apmap {
            loadExtractor("https://emturbovid.com/t/DDOG6t8HIxlK9Vul9H3W", "https://ditonton.bid", subtitleCallback, callback)
//        }

        return true
    }

//    private suspend fun String.getIframe() : String {
//        val header = mapOf("referer" to "https://ditonton.bid")
//        val src = app.get(this, headers = header).document.select("#loadPlayer iframe").attr("src")
//        val document = app.get(src, headers = header).document
//        return document.select("iframe").attr("src")
//    }

    open class Emturbovid : ExtractorApi() {
        override val name = "Emturbovid"
        override val mainUrl = "https://emturbovid.com"
        override val requiresReferer = true

        override suspend fun getUrl(
                url: String,
                referer: String?,
                subtitleCallback: (SubtitleFile) -> Unit,
                callback: (ExtractorLink) -> Unit
        ) {
            val response = app.get(url, referer = referer)
            val m3u8 = Regex("[\"'](.*?master\\.m3u8.*?)[\"']").find(response.text)?.groupValues?.getOrNull(1)
            M3u8Helper.generateM3u8(
                    name,
                    m3u8 ?: return,
                    mainUrl
            ).forEach(callback)
        }

    }
}