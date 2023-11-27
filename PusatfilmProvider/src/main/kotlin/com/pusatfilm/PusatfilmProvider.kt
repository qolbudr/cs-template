import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.extractors.Gdriveplayer
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class PusatfilmProvider : MainAPI() {
    override var mainUrl = "https://139.99.115.223"
    override var name = "PusatFilm"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "id"
    override val hasMainPage = true

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("h2.entry-title a")?.text()?.trim() ?: ""
        val href = this.selectFirst("h2.entry-title a")?.attr("href") ?: ""
        val posterUrl = this.selectFirst("img")?.attr("src")
        val quality = this.selectFirst("div.gmr-quality-item a")?.text()?.trim() ?: ""

        return if(href.contains("tv")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    override val mainPage: List<MainPageData> = mainPageOf(
            "$mainUrl/film-terbaru/page/" to "Film Terbaru",
            "$mainUrl/trending/page/" to "Trending",
            "$mainUrl/genre/action/page/" to "Action",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(request.data + page).document
        val home = document.select("article.item").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val document = app.get("$mainUrl/?s=$query&post_type[]=post&post_type[]=tv").document
        val result = document.select("article.item").mapNotNull {
            it.toSearchResult()
        }

        return result;
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: ""
        val poster = document.selectFirst("img.attachment-thumbnail")?.attr("src")?.replace("-60x90", "")
        val tags = document.select(".gmr-moviedata [rel*=category]")?.mapNotNull { it.text().trim() }
        val description = document.selectFirst(".entry-content p")?.text()?.trim()
        val trailer = document.selectFirst("a.gmr-trailer-popup")?.attr("href")
        val rating = document.selectFirst(".gmr-meta-rating span:nth-child(2)")?.text()?.toRatingInt()
        val actors = document.select("[itemprop=actors] a")?.mapNotNull { it.text().trim() }

        val tvType = if(url.contains("tv")) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }

        val recommendation = document.select("div.idmuvi-core article.item").mapNotNull {
            it.toSearchResult()
        }

        if(tvType == TvType.Movie) {
            return newMovieLoadResponse(title, url, tvType, url) {
                this.posterUrl = poster
                this.tags = tags
                addTrailer(trailer)
                this.rating = rating
                addActors(actors)
                this.plot = description
                this.recommendations = recommendation
            }
        } else {
            var seasonNumber = 0;
            var listEpisode = ArrayList<Episode>();

            document.select(".custom-epslist").reversed().mapNotNull {
                seasonNumber++;
                var epsNumber = 0;
                it.select("a.s-eps").mapNotNull { episodes ->
                    epsNumber++;
                    val hrefEpisode = episodes.attr("href")
                    val docEpisode = app.get(hrefEpisode).document
                    val epsTitle = docEpisode.selectFirst(".gmr-moviedata:nth-child(3)")?.text()?.replace("Nama Episode:", "") ?: ""
                    val epsPoster = docEpisode.selectFirst("img.attachment-thumbnail")?.attr("src")?.replace("-60x90", "")
                    val epsDescription = docEpisode.selectFirst(".entry-content p")?.text()?.trim()
                    val episodeItem = Episode(hrefEpisode, epsTitle, seasonNumber, epsNumber, epsPoster, description = epsDescription)

                    listEpisode.add(episodeItem);
                }
            }

            return newTvSeriesLoadResponse(title, url, tvType, listEpisode.toList()) {
                this.posterUrl = poster
                this.tags = tags
                addTrailer(trailer)
                this.rating = rating
                addActors(actors)
                this.plot = description
                this.recommendations = recommendation
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        val iframe = document.selectFirst(".gmr-embed-responsive iframe")?.attr("src") ?: ""
        val documentFrame = app.get(iframe, referer = mainUrl).document

        documentFrame.select("#dropdown-server li a").mapNotNull {
            val url =  base64Decode(it.attr("data-frame"))
            val ref = base64Encode("https://139.99.115.223/".toByteArray())

            "$url&r=$ref";

        }.apmap {
            loadExtractor(httpsify(it), data, subtitleCallback, callback)
        }

        return true;
    }

    open class Uplayer : ExtractorApi() {
        override val name = "UplayerXYZ"
        override val mainUrl = "https://uplayer.xyz"
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