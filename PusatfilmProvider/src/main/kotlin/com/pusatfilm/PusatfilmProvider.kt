import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import java.util.Base64.*

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

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            addQuality(quality)
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
        val document = app.get("$mainUrl/?s=$query").document
        val result = document.select("article.item").mapNotNull {
            it.toSearchResult()
        }

        return result;
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: ""
        val poster = document.selectFirst("img.attachment-thumbnail")?.attr("src")
        val tags = document.select(".gmr-moviedata:nth-child(5) a")?.mapNotNull { it.text().trim() }
        val description = document.selectFirst(".entry-content p")?.text()?.trim()
        val trailer = document.selectFirst("a.gmr-trailer-popup")?.attr("href")
        val rating = document.selectFirst(".gmr-meta-rating span:nth-child(2)")?.text()?.toRatingInt()
//        val actors = document.select(".gmr-moviedata:nth-child(14) a'")?.mapNotNull { it.text().trim() }
        val tvType = TvType.Movie

        val recommendation = document.select("div.idmuvi-core article.item").mapNotNull {
            it.toSearchResult()
        }

        return newMovieLoadResponse(title, url, tvType, url) {
            this.posterUrl = poster
            this.tags = tags
            addTrailer(trailer)
            this.rating = rating
//            addActors(actors)
            this.plot = description
            this.recommendations = recommendation
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        val iframe = document.selectFirst(".gmr-embed-responsive iframe")?.attr("src") ?: ""

        return loadExtractor(iframe, data, subtitleCallback, callback)
    }

    open class Kotakajaib : ExtractorApi() {
        override val name = "KotakAjaib"
        override val mainUrl = "https://kotakajaib.me"
        override val requiresReferer = true

        override suspend fun getUrl(
                url: String,
                referer: String?,
                subtitleCallback: (SubtitleFile) -> Unit,
                callback: (ExtractorLink) -> Unit
        ) {
            val document = app.get(url, referer = referer).document
            val dataFrame = document.selectFirst("#dropdown-server li a")?.attr("data-frame") ?: ""

            val url =  base64Decode(dataFrame)
            val ref = base64Encode("https://139.99.115.223/".toByteArray())

            val response = app.get("$url&r=$ref", referer = referer)

            val m3u8 = Regex("[\"'](.*?master\\.m3u8.*?)[\"']").find(response.text)?.groupValues?.getOrNull(1)
            M3u8Helper.generateM3u8(
                    name,
                    m3u8 ?: return,
                    mainUrl
            ).forEach(callback)
        }
    }
}