import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addPoster
import com.lagradost.cloudstream3.addQuality
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor

class AnimeindoProvider : MainAPI() {
    override var mainUrl = "https://www.animeindo.stream"
    override var name = "Animeindo"
    override val supportedTypes = setOf(TvType.AnimeMovie, TvType.Anime)
    override var lang = "id"
    override val hasMainPage = true

    override val mainPage: List<MainPageData> = mainPageOf(
            "$mainUrl/movie/" to "Anime Movie",
            "$mainUrl/anime-rating-terbanyak/" to "Rating Terbanyak",
            "$mainUrl/genre/romance/" to "Romance",
            "$mainUrl/genre/slice-of-life/" to "Slice of Life",
            "$mainUrl/genre/shounen/" to "Shounen",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(request.data + page).document
        val result = ArrayList<SearchResponse>()

        document.select("a.animate-in").mapNotNull {
            val title = it.selectFirst("h2")?.text()?.trim() ?: ""
            val href = it.attr("href") ?: ""
            val quality = Qualities.Unknown.name
            val type = TvType.Anime
            val image = it.selectFirst("img")?.attr("data-src") ?: ""

            val found = newAnimeSearchResponse(title, href, type) {
                addQuality(quality)
                this.posterUrl = image
            }

            result.add(found)
        }

        return newHomePageResponse(request.name, result.toList());
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val document = app.get("$mainUrl/search/$query").document
        val result = ArrayList<SearchResponse>()

        document.select("a.animate-in").mapNotNull {
            val title = it.selectFirst("h2")?.text()?.trim() ?: ""
            val href = it.attr("href") ?: ""
            val quality = Qualities.Unknown.name
            val type = TvType.Anime
            val image = it.selectFirst("img")?.attr("data-src") ?: ""

            val found = newAnimeSearchResponse(title, href, type) {
                addQuality(quality)
                this.posterUrl = image
            }

            result.add(found)
        }

        return result;
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val resTitle = document.selectFirst("h1.text-2xl")?.text()?.trim() ?: ""
        val title = resTitle.replace("Subtitle Indonesia", "")
        val poster = document.selectFirst("img:nth-child(1)")?.attr("data-src") ?: ""
        val year = document.selectFirst("div.text-sm:nth-child(2)")?.text()?.trim()?.split(" ")?.get(1)
        val overview = document.selectFirst("p.text-sm:nth-child(6)")?.text()?.trim()
        val tags = document.select("a.py-1").mapNotNull { it.text() }
        val rating = document.selectFirst("div.mb-5:nth-child(3) > div:nth-child(1)")?.text()?.trim()?.split(" ")?.get(0).toRatingInt()
        val resType = document.selectFirst("div.btn:nth-child(3)")?.text() ?: ""

        val type = if(resType == "TV") {
            TvType.Anime
        } else {
            TvType.AnimeMovie
        }

        if(type == TvType.AnimeMovie) {
            val dataURL = document.selectFirst("#allEpisode > div:nth-child(1) > a")?.attr("href") ?: ""
            return newMovieLoadResponse(title, dataURL, type, dataURL) {
                this.posterUrl = poster
                this.year = year?.toIntOrNull()
                this.plot = overview
                this.tags = tags
                this.rating = rating
            }
        } else {
            val episode = ArrayList<Episode>()

            document.select("#allEpisode > div:nth-child(1) > a").mapNotNull {
                val epsUrl = it.attr("href") ?: ""
                val epsName = it.text().trim() ?: ""
                val epsNumber = epsName.split(" ").last()
                val epsDescription = "Nonton dan streaming $title - $epsName dengan subtitle indonesia gratis"

                val resEps = Episode(epsUrl, epsName, 1, epsNumber.toIntOrNull() ?: 0, poster, description = epsDescription)
                episode.add(resEps)
            }

            return newTvSeriesLoadResponse(title, url, type, episode.reversed()) {
                this.posterUrl = poster
                this.year = year?.toIntOrNull()
                this.plot = overview
                this.tags = tags
                this.rating = rating
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        document.select("ul.bg-primary-500:nth-child(2) > li").mapNotNull {
            val code = it.selectFirst("[insight]")?.attr("insight") ?: ""
            if (code.isNotEmpty()) {
                val codeStr = code.split("/").last().replace("_", "")
                val iframeURL = "https://www.animeindo.stream/streaming/?url=$codeStr"
                val docIframe = app.get(iframeURL).document
                var urlIframe = docIframe.selectFirst("iframe")?.attr("src") ?: ""
                urlIframe = if (urlIframe.contains("http")) {
                    urlIframe
                } else {
                    "https:$urlIframe"
                }

                if (urlIframe.isNotEmpty()) {
                    loadExtractor(urlIframe, mainUrl, subtitleCallback, callback)
                }
            }
        }

        return true;
    }
}

open class Mp4Upload : ExtractorApi() {
    override val name = "Mp4Upload"
    override val mainUrl = "https://www.mp4upload.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val result = app.get(url, referer = referer).text
        val resultURL = Regex("(?<=src: )(.*)(?=\")").find(result)?.groupValues?.get(1) ?: ""
        callback.invoke(
                ExtractorLink(
                        "Mp4Upload",
                        "Mp4Upload",
                        resultURL,
                        mainUrl,
                        Qualities.Unknown.value,
                        isM3u8 = url.contains("m3u8"),
                        headers = mapOf(
                                "range" to "bytes=0-"
                        )
                )
        )
    }
}