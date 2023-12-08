import android.util.Log
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
            val poster = posterUrl.replace("s72-c", "w300")

            val itemResult = if (title.contains("Season")) {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = poster
                }
            } else {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = poster
                }
            }

            result.add(itemResult)
        }
        return result;
    }

    override suspend fun load(url: String): LoadResponse? {
        val body = app.get(url)
        val document = body.document
        val bodyText = body.text


        val title = document.selectFirst("article.blog-post .post-title")?.text()?.trim() ?: ""
        val poster = document.selectFirst("article.blog-post .rcimg-cover img")?.attr("data-src") ?: ""
        val resOverview = document.selectFirst(".postmovie")?.text()?.trim() ?: ""
        val overview = resOverview.replace("Sinopsis:", "")
        val genres = document.select(".post-tag a").mapNotNull { it.text() }
        val resRating = document.selectFirst("span.rating")?.text()?.trim() ?: "0"
        val rating = resRating.replace("/10", "")
        val recommendations = document.select("#related-posts li").mapNotNull { it.toSearchResult() }
        val trailer = document.selectFirst("#btn-trailer")?.attr("href") ?: ""

        val resDrive = Regex("(?<=https://drive.google.com/)(.*)(?=\" rel)").find(bodyText)?.groupValues?.get(1) ?: ""
        val gdriveLink = "https://drive.google.com/$resDrive"

        return if(gdriveLink.contains("folders")) {
            val episode = ArrayList<Episode>()

            val driveDoc = app.get(gdriveLink).document
            val epsList = driveDoc.select("[data-target=doc]")
            var epsNum = 1;

            epsList.mapNotNull {
                val id = it.attr("data-id")
                val epsTitle = it.selectFirst("[data-tooltip*=\"Video\"]")?.text()?.trim() ?: ""
                val epsUrl = "https://www.googleapis.com/drive/v3/files/$id?alt=media&key=AIzaSyDVyzmm9lL3IE08vAxwio2ubLr2EVf1ucA"

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
            val getId = Regex("(?<=d/)(.*)(?=/)").find(gdriveLink)?.groupValues?.get(1) ?: ""
            val resultUrl = "https://www.googleapis.com/drive/v3/files/$getId?alt=media&key=AIzaSyDVyzmm9lL3IE08vAxwio2ubLr2EVf1ucA"
            newMovieLoadResponse(
                    title,
                    url,
                    TvType.Movie,
                    resultUrl,
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

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        callback.invoke(ExtractorLink("GDrive", "GDrive", data, mainUrl, Qualities.Unknown.value, isM3u8 = false))
        return true;
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