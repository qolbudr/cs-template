import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
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
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element

class HonimeProvider : MainAPI() {
    override var mainUrl = "https://honime.com"
    override var name = "Honime"
    override val supportedTypes = setOf(TvType.Anime, TvType.TvSeries, TvType.Movie)
    override var lang = "id"
    override val hasMainPage = true

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("div.tt h2")?.text()?.trim() ?: ""
        val href = this.selectFirst("a.tip")?.attr("href") ?: ""
        val posterUrl = this.selectFirst("img.ts-post-image")?.attr("src")?.replace("?resize=247,350", "")


        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override val mainPage: List<MainPageData> = mainPageOf(
            "$mainUrl/anime/?status=&type=&order=update&page=" to "Latest Upload",
            "$mainUrl/Completed/page/" to "Completed",
            "$mainUrl/Movie/page/" to "Movie"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(request.data + page).document
        val home = document.select("article.bs").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val document = app.get("$mainUrl/?s=$query").document
        val result = document.select("article.bs").mapNotNull {
            it.toSearchResult()
        }

        return result;
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: ""
        val poster = document.selectFirst("img.ts-post-image")?.attr("src")?.replace("?resize=247,350", "")
        val tags = document.select(".genxed a")?.mapNotNull { it.text().trim() }
        val description = document.selectFirst(".entry-content p")?.text()?.trim()
        val rating = document.selectFirst(".rating strong")?.text()?.replace("Rating ", "").toRatingInt()
        val actors = document.select("a.casts")?.mapNotNull { it.text().trim() }
        val typeRaw = document.select(".spe span:nth-child(5)").text().trim()

        val type = if(typeRaw.contains("Movie")) {
            TvType.Movie
        } else {
            TvType.TvSeries
        }

        if(type == TvType.Movie) {
            val newURL = document.selectFirst(".eplister li a")?.attr("href") ?: "";

            return newMovieLoadResponse(title, url, type, newURL) {
                this.posterUrl = poster
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.plot = description
            }
        } else {
            val episodeList = ArrayList<Episode>();

            document.select(".eplister li").reversed().mapNotNull {
                val epName = it.select(".epl-title").text().trim()
                val epNumber = it.select(".epl-num").text().trim().toIntOrNull() ?: 0
                val epUrl = it.select("a").attr("href");
                episodeList.add(Episode(epUrl, epName, episode = epNumber))
            }

            return newTvSeriesLoadResponse(title, url, type, episodeList.toList()) {
                this.posterUrl = poster
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document

        document.select(".mirror option").mapNotNull {
            val code = it.attr("value")

            if(code.isNotEmpty()) {
                val myiFrame = base64Decode(code)

                val embedUrl = Regex("(?<=src=\")(.*)(?>\" frame)").find(myiFrame)?.groupValues?.getOrNull(1) ?: ""

                if (embedUrl.contains("qoop")) {
                    //qoop
                    val iframeText = app.get(embedUrl).text

                    val keyFrame = Regex("(?<=kaken = \")(.*)(?=\",)").find(iframeText)?.groupValues?.getOrNull(1)
                            ?: ""

                    val qoopUrl = "https://s2.qoop.my.id/api/?$keyFrame";

                    val result = app.get(qoopUrl).text

                    val json = parseJson<ResponseSource>(result);

                    val sourceTitle = json.title ?: ""

                    val sourceUrl = json.source?.firstOrNull()?.file ?: ""


                    callback.invoke(
                            ExtractorLink(sourceUrl, sourceTitle, sourceUrl, referer = sourceUrl, quality = 0)
                    )


                } else {
                    // Google Video
                    val iframeText = app.get(embedUrl).text

                    val source = Regex("(?<=play_url\":\")(.*)(?=\",)").find(iframeText)?.groupValues?.getOrNull(1)
                            ?: ""

                    callback.invoke(
                            ExtractorLink(source, "Google Video", source, referer = source, quality = 480)
                    )
                }
            }
        }
        return true
    }

    data class SourceList(
        val file: String?,
        val type: String?,
        val label: String?,
    )

    data class ResponseSource(
            val title: String?,
            val source: List<SourceList>?
    )
}
