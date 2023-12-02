import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addQuality
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import java.text.SimpleDateFormat
import java.util.Calendar

class HqnimeProvider : MainAPI() {
    override var mainUrl = "https://hqnime.com"
    override var name = "Hqnime"
    override val supportedTypes = setOf(TvType.AnimeMovie, TvType.Anime)
    override var lang = "id"
    override val hasMainPage = true

    override val mainPage: List<MainPageData> = mainPageOf(
            "$mainUrl/anime-completed/" to "Complete",
            "$mainUrl" to "Popular",
            "$mainUrl/jadwal/" to "Today",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(request.data).document
        val result = ArrayList<SearchResponse>()

        if(request.name == "Popular") {
            document.select(".wpop-weekly li").mapNotNull {
                val title = it.selectFirst("h4 a")?.text()?.trim() ?: ""
                val href = it.selectFirst("h4 a")?.attr("href") ?: ""
                val quality = Qualities.Unknown.name
                val type = TvType.Anime
                val image = it.selectFirst(".ts-post-image")?.attr("src") ?: ""

                val found = newAnimeSearchResponse(title, href, type) {
                    addQuality(quality)
                    this.posterUrl = image
                }

                result.add(found)
            }
        } else {
            if(request.name == "Today") {
                val time = Calendar.getInstance().time
                val formatter = SimpleDateFormat("EEEE")
                val day = formatter.format(time).lowercase()

                document.select(".sch_$day .listupd .bs").mapNotNull {
                    val title = it.selectFirst(".tt")?.text()?.trim() ?: ""
                    val href = it.selectFirst("a")?.attr("href") ?: ""
                    val quality = Qualities.Unknown.name
                    val type = TvType.Anime
                    val image = it.selectFirst("img")?.attr("src") ?: ""

                    val found = newAnimeSearchResponse(title, href, type) {
                        addQuality(quality)
                        this.posterUrl = image
                    }

                    result.add(found)
                }
            } else {
                document.select("article.bs").mapNotNull {
                    val title = it.selectFirst(".tt h2")?.text()?.trim() ?: ""
                    val href = it.selectFirst("a")?.attr("href") ?: ""
                    val quality = Qualities.Unknown.name
                    val typez = it.selectFirst(".typez")?.text()?.trim() ?: ""
                    val type = if(typez == "Movie") {
                        TvType.AnimeMovie
                    } else {
                        TvType.Anime
                    }
                    val image = it.selectFirst("img")?.attr("src") ?: ""

                    val found = newAnimeSearchResponse(title, href, type) {
                        addQuality(quality)
                        this.posterUrl = image
                    }

                    result.add(found)
                }
            }
        }

        return newHomePageResponse(request.name, result.toList());
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val document = app.get("$mainUrl/?s=$query").document
        val result = ArrayList<SearchResponse>()

        document.select("article.bs").mapNotNull {
            val title = it.selectFirst(".tt")?.text()?.trim() ?: ""
            val href = it.selectFirst("a")?.attr("href") ?: ""
            val quality = Qualities.Unknown.name
            val typez = it.selectFirst(".typez")?.text()?.trim() ?: ""
            val type = if(typez == "Movie") {
                TvType.AnimeMovie
            } else {
                TvType.Anime
            }
            val image = it.selectFirst("img")?.attr("src") ?: ""

            val found = newAnimeSearchResponse(title, href, type) {
                addQuality(quality)
                this.posterUrl = image
            }

            result.add(found)
        }

        return result
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val resTitle = document.selectFirst(".entry-title")?.text()?.trim() ?: ""
        val title = resTitle.replace("Subtitle Indonesia", "")
        val poster = document.selectFirst(".ts-post-image")?.attr("src") ?: ""
        val year = document.selectFirst(".split")?.text()?.trim()?.split(" ")?.get(1)
        val overview = document.selectFirst(".entry-content p")?.text()?.trim()
        val tags = document.select(".genxed a").mapNotNull { it.text() }
        val rating = document.selectFirst(".rating strong")?.text()?.trim()
        val resType = document.selectFirst(".lastend")?.attr("style") ?: ""

        val type = if(resType.contains("none")) {
            TvType.AnimeMovie
        } else {
            TvType.Anime
        }

        val episode = ArrayList<Episode>()
        val countEps = document.select(".eplister li").count()
        var epsNum = countEps
        document.select(".eplister li").mapNotNull {
            val epsUrl = it.selectFirst("a")?.attr("href") ?: ""
            val epsName = it.selectFirst(".epl-title")?.text()?.trim() ?: ""
            val epsNumber = it.selectFirst(".epl-num")?.text()?.trim() ?: "0"
            val epsDescription = "Nonton dan streaming $epsName dengan subtitle indonesia gratis"

            val resEps = Episode(epsUrl, epsName, 1, epsNumber.toIntOrNull() ?: epsNum, poster, description = epsDescription)

            episode.add(resEps)
            epsNum--;
        }

        val resEpisode = episode.toList().sortedBy { eps -> eps.episode }

        return newTvSeriesLoadResponse(title, url, type, resEpisode) {
            this.posterUrl = poster
            this.year = year?.toIntOrNull()
            this.plot = overview
            this.tags = tags
            this.rating = rating.toRatingInt()
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document  = app.get(data).document

        document.select(".mirror option").mapNotNull {
            val code = it.attr("value")
            if(code.isNotEmpty()) {
                val codeIframe = base64Decode(code)
                val urlIframe = Regex("(?<=src=\")(.*)(?=\" frame)").find(codeIframe)?.groupValues?.get(1) ?: ""
                if(urlIframe.isNotEmpty()) {
                    loadExtractor(urlIframe, mainUrl, subtitleCallback, callback)
                }
            }
        }

        return true;
    }
}

open class PPlayer : ExtractorApi() {
    override val name = "P1Player"
    override val mainUrl = "https://p1player.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val result = app.get(url, referer = referer).text
        val resUrl = Regex("(?<=file\":\")(.*)(?=\",\"t)").find(result)?.groupValues?.get(1)
        val url = resUrl?.replace("\\/", "/") ?: ""
        callback.invoke(
                ExtractorLink("P1Player", "P1Player", url, mainUrl, Qualities.Unknown.value, isM3u8 = url.contains("m3u8"))
        )
    }
}

open class Blogger : ExtractorApi() {
    override val name = "Blogger"
    override val mainUrl = "https://www.blogger.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val result = app.get(url, referer = referer).text
        val url = Regex("(?<=play_url\":\")(.*)(?=\",)").find(result)?.groupValues?.get(1) ?: ""
        callback.invoke(
                ExtractorLink("Blogger", "Blogger", url, mainUrl, Qualities.Unknown.value, isM3u8 = url.contains("m3u8"))
        )
    }
}

open class Yourupload : ExtractorApi() {
    override val name = "Yourupload"
    override val mainUrl = "https://www.yourupload.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val result = app.get(url, referer = referer).text
        val url = Regex("(?<=file: ')(.*)(?=',\\n\\  image)").find(result)?.groupValues?.get(1) ?: ""
        callback.invoke(
                ExtractorLink("Blogger", "Blogger", url, mainUrl, Qualities.Unknown.value, isM3u8 = url.contains("m3u8"))
        )
    }
}

open class Mp4Upload : ExtractorApi() {
    override val name = "Mp4Upload"
    override val mainUrl = "https://www.mp4upload.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val result = app.get(url, referer = referer).text
        val url = Regex("(?<=src: )(.*)(?=\")").find(result)?.groupValues?.get(1) ?: ""
        callback.invoke(
                ExtractorLink("Mp4Upload", "Mp4Upload", url, mainUrl, Qualities.Unknown.value, isM3u8 = url.contains("m3u8"))
        )
    }
}
