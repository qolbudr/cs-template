import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addQuality
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import org.jsoup.nodes.Element

class PojookProvider : MainAPI() {
    override var mainUrl = "https://pojook.com"
    override var name = "Pojook"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "id"
    override val hasMainPage = true

    val tmdbURL = "https://api.themoviedb.org/3"
    val apiTmdb = "cad7722e1ca44bd5f1ea46b59c8d54c8"

    data class TmdbSearchResponse (val results: List<TmdbBody>?)
    data class TmdbBody (val id: String?)

    data class EmbedResponse (val embed_url: String, val type : String)

    private fun getUrl(id: Int?, tvShow: Boolean): String {
        return if (tvShow) "https://www.themoviedb.org/tv/${id ?: -1}"
        else "https://www.themoviedb.org/movie/${id ?: -1}"
    }
    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst(".data a")?.text()?.trim() ?: ""
        val href = this.selectFirst(".data a")?.attr("href") ?: ""
        val posterUrl = this.selectFirst(".poster img")?.attr("src")
        val quality = this.selectFirst(".poster .quality")?.text()?.trim() ?: "Bluray"

        return if(href.contains("tvshows")) {
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

    private suspend fun getTmdbId(tvShow: Boolean, query: String): String? {
        return if(tvShow) {
            val result = app.get("$tmdbURL/search/tv?query=$query&api_key=$apiTmdb").text
            val data = parseJson<TmdbSearchResponse>(result)

            data.results?.firstOrNull()?.id;
        } else {
            val result = app.get("$tmdbURL/search/movie?query=$query&api_key=$apiTmdb").text
            val data = parseJson<TmdbSearchResponse>(result)

            data.results?.firstOrNull()?.id;
        }
    }

    override val mainPage: List<MainPageData> = mainPageOf(
            "$mainUrl/trending-2/page/" to "Trending",
            "$mainUrl/ratings-2/page/" to "Rating",
            "$mainUrl/movies/page/" to "Movies",
            "$mainUrl/tvshows/page/" to "TV Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(request.data + page).document
        val home = ArrayList<SearchResponse>()

        document.select("article.item").forEach {
            val title = it.selectFirst(".data a")?.text()?.trim() ?: ""
            val href = it.selectFirst(".data a")?.attr("href") ?: ""
            val tmdbId = getTmdbId(tvShow = href.contains("tvshows"), query = title)

            if(tmdbId != null) {
                home.add(it.toSearchResult())
            }
        }

        return newHomePageResponse(request.name, home.toList())
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val document = app.get("$mainUrl/?s=$query").document
        val result = ArrayList<SearchResponse>()

        document.select(".result-item").mapNotNull {
            val title = it.selectFirst(".title a")?.text()?.trim() ?: ""
            val href = it.selectFirst(".title a")?.attr("href") ?: ""
            val posterUrl = it.selectFirst(".thumbnail img")?.attr("src")
            val quality = "Bluray"

            val tmdbId = getTmdbId(tvShow = href.contains("tvshows"), query = title)

            if(tmdbId != null) {
                if (href.contains("tvshows")) {
                    result.add(newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                        this.posterUrl = posterUrl
                        addQuality(quality)
                    })
                } else {
                    result.add(newMovieSearchResponse(title, href, TvType.Movie) {
                        this.posterUrl = posterUrl
                        addQuality(quality)
                    })
                }
            }
        }

        return result;
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst(".sheader .data h1")?.text()?.trim() ?: ""

        val tmdbId = getTmdbId(tvShow = url.contains("tvshows"), query = title);
        val tmdbHref = getUrl(tmdbId!!.toInt(), tvShow = url.contains("tvshows"))
        val dataFromTmdb = TmdbProvider().load(tmdbHref);

        if(url.contains("tvshows")) {
            val dataParsed = dataFromTmdb as? TvSeriesLoadResponse
            var episodeParsed = ArrayList<Episode>()
            val episode = dataParsed?.episodes
            var seasonNumber = 1;

            document.select(".se-c").mapNotNull {
                var episodeNumber = 1;
                val episodeToEdit = episode?.filter { episodeIt -> episodeIt.season == seasonNumber }

                it.select(".episodiotitle a").mapNotNull {episodeBox ->
                    val realHrefEpisode = "$mainUrl${episodeBox.attr("href")}"
                    val resEpisode = episodeToEdit?.first { epItem -> epItem.episode == episodeNumber }?.copy(data = realHrefEpisode)
                    if(resEpisode != null) episodeParsed.add(resEpisode)
                    episodeNumber++;
                }

                seasonNumber++;
            }

            return dataParsed?.copy(episodes = episodeParsed.toList(), url = url)
        } else {
            val dataParsed = dataFromTmdb as? MovieLoadResponse
            return dataParsed?.copy(url = url, dataUrl = url)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data, referer = mainUrl).document
        val movieId = document.selectFirst("[data-nume]")?.attr("data-post")

        val embedRes = app.get("$mainUrl/wp-json/dooplayer/v2/$movieId/movie/3").text
        val embedData = parseJson<EmbedResponse>(embedRes)

        val playerId = Regex("(?<=v\\/)(.*)(?=&)").find(embedData.embed_url)?.groupValues?.getOrNull(1)

        val qualities = listOf<Int>(360, 720, 1080)

        for(quality in qualities) {
            val streamUrl = "https://d308.gshare.art/stream/$quality/$playerId/__001"
            callback.invoke(
                    ExtractorLink("VIP Server", "VIP Server HD", streamUrl, "https://fa.efek.stream", quality, type = ExtractorLinkType.VIDEO)
            )
        }

        return true;
    }
}