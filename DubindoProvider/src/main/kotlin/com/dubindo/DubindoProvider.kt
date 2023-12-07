import com.fasterxml.jackson.annotation.JsonProperty
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
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.fixUrl
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import java.net.URI

class DubindoProvider : MainAPI() {
    override var mainUrl = "https://www.sontolfilm.xyz"
    override var name = "DubIndo"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "id"
    override val hasMainPage = true

    override val mainPage: List<MainPageData> = mainPageOf(
            mainUrl to "Top of Week",
            mainUrl to "Latest Update",
            "$mainUrl/search/label/Indonesia" to "Indonesia",
    )

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst(".post-title a")?.text()?.trim() ?: this.selectFirst(".recent-title a")?.text()?.trim()  ?: ""
        val href = this.selectFirst(".post-title a")?.attr("href") ?: this.selectFirst(".recent-title a")?.text()?.trim() ?: ""
        val posterUrl = this.selectFirst(".rec-image img")?.attr("data-src")

        return if (title.contains("Season")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(request.data).document
        val home = ArrayList<SearchResponse>()

        if (request.name == "Top of Week") {
            document.select("#PopularPosts1 .post").mapNotNull {
                home.add(it.toSearchResult())
            }
        }

        if (request.name == "Latest Update" || request.name == "Indonesia") {
            document.select("#Blog1 .blog-post").mapNotNull {
                home.add(it.toSearchResult())
            }
        }

        return newHomePageResponse(request.name, home.toList(), hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl/feeds/posts/summary?q=$query&alt=json&orderby=updated").text
        val json = parseJson<BaseSearchDataResponse>(response)

        val result = ArrayList<SearchResponse>()

        (json.feed?.entry ?: listOf<BaseSearchEntry>()).map {
            val title = it.baseTitle?.title ?: "";
            val href = it.link?.firstOrNull {el -> el.rel == "alternate"}?.href ?: ""
            val posterUrl = it.thumbnail?.url ?: ""

            val itemResult = if (title.contains("Season")) {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                }
            } else {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                }
            }

            result.add(itemResult)
        }
        return result;
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("article.blog-post .post-title")?.text()?.trim() ?: ""
        val poster = document.selectFirst("article.blog-post .rcimg-cover img")?.attr("data-src") ?: ""
        val resOverview = document.selectFirst(".postmovie")?.text()?.trim() ?: ""
        val overview = resOverview.replace("Sinopsis:", "")
        val genres = document.select(".post-tag a").mapNotNull { it.text() }
        val resRating = document.selectFirst("span.rating")?.text()?.trim() ?: "0"
        val rating = resRating.replace("/10", "")
        val recommendations = document.select("#related-posts li").mapNotNull { it.toSearchResult() }
        val trailer = document.selectFirst("#btn-trailer")?.attr("href") ?: ""
        val iframe = document.selectFirst("#movietop iframe")?.attr("data-src") ?: ""


        return if(iframe.contains("bestx.stream/p/")) {
            val episode = ArrayList<Episode>()

            val iframeDoc = app.get(iframe).document
            val epsList = iframeDoc.select("span.episode")
            var epsNum = 1;

            epsList.mapNotNull {
                val host = "https://bestx.stream"
                val epsTitle = it.text().trim()
                val resEpsUrl = it.attr("data-url")
                val epsUrl = "$host$resEpsUrl"

                episode.add(Episode(epsUrl, epsTitle, 1, epsNum, poster, description = "Nonton $title episode $epsNum subtitle/dubbing indonesia"))

                epsNum++
            }


            newTvSeriesLoadResponse(
                    title,
                    url,
                    TvType.TvSeries,
                    episode,
            ) {
                this.posterUrl = poster
                this.year = year
                this.plot = overview
                this.tags = genres
                this.rating = rating.toRatingInt()
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(
                    title,
                    url,
                    TvType.Movie,
                    iframe,
            ) {
                this.posterUrl = poster
                this.year = year
                this.plot = overview
                this.tags = genres
                this.rating = rating.toRatingInt()
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }
    private fun String.getHost(): String {
        return URI(this).host.substringBeforeLast(".").substringAfterLast(".")
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val referer = data.getHost()
        return loadExtractor(data, referer, subtitleCallback, callback)
    }

    data class BaseSearchDataResponse(
            @JsonProperty("feed") val feed: BaseData? = null,
    )

    data class BaseData(
            @JsonProperty("entry") val entry: ArrayList<BaseSearchEntry>? = null,
    )

    data class BaseSearchEntry(
            @JsonProperty("title") val baseTitle: BaseSearchTitle? = null,
            @JsonProperty("link") val link: ArrayList<BaseSearchLink>? = null,
            @JsonProperty("media\$thumbnail") val thumbnail: BaseSearchThumbnail? = null,
    )

    data class BaseSearchTitle(
            @JsonProperty("\$t") val title: String? = null,
    )

    data class BaseSearchLink(
            @JsonProperty("rel") val rel: String? = null,
            @JsonProperty("href") val href: String? = null,
    )

    data class BaseSearchThumbnail(
            @JsonProperty("url") val url: String? = null,
    )
}

open class BestX : ExtractorApi() {
    override val name = "BestX"
    override val mainUrl = "https://bestx.stream"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val res = app.get(url, referer = referer).text
        val data = getAndUnpack(res)

        var code = Regex("(?<=JScript = ')(.*)(?=';)").find(data)?.groupValues?.getOrNull(1)

        var resJson = app.post("https://cs-backend-navy.vercel.app/bestx-extract", data = mapOf("data" to (code ?: "")), headers = mapOf("Content-Type" to "application/json")).text

        var dataLink = parseJson<Response>(resJson)

        dataLink?.sources?.forEach {
            callback.invoke(ExtractorLink(this.name, this.name, fixUrl(it.file), "$mainUrl/", Qualities.Unknown.value, isM3u8 = it.file.contains("m3u8")))
        }
    }

    data class Response(@JsonProperty("sources") val sources: List<FileData>)

    data class FileData(@JsonProperty("file") val file: String, @JsonProperty("type") val type: String?, @JsonProperty("label") val label: String?)
}