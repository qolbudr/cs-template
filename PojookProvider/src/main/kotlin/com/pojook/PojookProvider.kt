import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
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
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import org.jsoup.nodes.Element
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toRatingInt
import java.time.Year
import kotlin.math.roundToInt

class PojookProvider : MainAPI() {
    override var mainUrl = "https://pojook.com"
    override var name = "Pojook"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "id"
    override val hasMainPage = true

    val tmdbURL = "https://api.themoviedb.org/3"
    val apiTmdb = "cad7722e1ca44bd5f1ea46b59c8d54c8"

    data class TmdbSearchResponse(val results: List<TmdbBody>?)
    data class TmdbBody(val id: String?, val first_air_date: String?)

    data class EmbedResponse(val embed_url: String, val type: String)

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst(".data a")?.text()?.trim() ?: ""
        val href = this.selectFirst(".data a")?.attr("href") ?: ""
        val posterUrl = this.selectFirst(".poster img")?.attr("src")
        val quality = this.selectFirst(".poster .quality")?.text()?.trim() ?: "Bluray"

        return if (href.contains("tvshows")) {
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

    private suspend fun getTmdbId(tvShow: Boolean, query: String, year: String): String? {
        return if (tvShow) {
            val result = app.get("$tmdbURL/search/tv?query=$query&api_key=$apiTmdb").text
            val data = parseJson<TmdbSearchResponse>(result)

            data.results?.firstOrNull {it.first_air_date?.contains(year) ?: false}?.id;
        } else {
            val result = app.get("$tmdbURL/search/movie?query=$query&api_key=$apiTmdb").text
            val data = parseJson<TmdbSearchResponse>(result)

            data.results?.firstOrNull {it.first_air_date?.contains(year) ?: false}?.id;
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
            val queryTitle = Regex("(?<=)(.*)(?= \\()").find(title)?.groupValues?.getOrNull(1) ?: ""
            val year = Regex("(?<=\\()(.*)(?=\\))").find(title)?.groupValues?.getOrNull(1) ?: ""

            val tmdbId = getTmdbId(tvShow = href.contains("tvshows"), query = queryTitle, year = year)

            if (tmdbId != null) {
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

            val queryTitle = Regex("(?<=)(.*)(?= \\()").find(title)?.groupValues?.getOrNull(1) ?: ""
            val year = Regex("(?<=\\()(.*)(?=\\))").find(title)?.groupValues?.getOrNull(1) ?: ""

            val tmdbId = getTmdbId(tvShow = href.contains("tvshows"), query = queryTitle, year = year)

            if (tmdbId != null) {
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
        val queryTitle = Regex("(?<=)(.*)(?= \\()").find(title)?.groupValues?.getOrNull(1) ?: ""
        val yearS = Regex("(?<=\\()(.*)(?=\\))").find(title)?.groupValues?.getOrNull(1) ?: ""

        val tmdbId = getTmdbId(tvShow = url.contains("tvshows"), query = queryTitle, year = yearS)

        val append = "alternative_titles,credits,external_ids,keywords,videos,recommendations"

        val urlFetch = if (url.contains("tvshows")) {
            "$tmdbURL/tv/${tmdbId}?api_key=$apiTmdb&append_to_response=$append"
        } else {
            "$tmdbURL/movie/${tmdbId}?api_key=$apiTmdb&append_to_response=$append"
        }

        val res = app.get(urlFetch).parsedSafe<MediaDetail>()
                ?: throw ErrorLoadingException("Invalid Json Response")

        val resTitle = res.title ?: res.name ?: return null
        val poster = getOriImageUrl(res.posterPath)
        val bgPoster = getOriImageUrl(res.backdropPath)
        val rating = res.vote_average.toString().toRatingInt()
        val genres = res.genres?.mapNotNull { it.name }
        val actors = res.credits?.cast?.mapNotNull { cast ->
            ActorData(Actor(cast.name ?: cast.originalName
            ?: return@mapNotNull null, getImageUrl(cast.profilePath)), roleString = cast.character)
        } ?: return null

        val trailer = res.videos?.results?.map { "https://www.youtube.com/watch?v=${it.key}" }?.randomOrNull()

        return if (url.contains("tvshows")) {
            val lastSeason = res.last_episode_to_air?.season_number ?: 0
            val parsedEpisode = ArrayList<Episode>()

            val episodes = res.seasons?.mapNotNull { season ->
                app.get("$tmdbURL/tv/$tmdbId/season/${season.seasonNumber}?api_key=$apiTmdb").parsedSafe<MediaDetailEpisodes>()?.episodes?.map { eps ->
                    Episode(url, name = eps.name, season = eps.seasonNumber, episode = eps.episodeNumber, posterUrl = getImageUrl(eps.stillPath), rating = eps.voteAverage?.times(10)?.roundToInt(), description = eps.overview)
                }
            }?.flatten() ?: listOf()

            var seasonNumber = 1;

            document.select(".se-c").mapNotNull {
                var episodeNumber = 1;
                it.select(".episodiotitle a").mapNotNull {epsBtn ->
                    val href = it.attr("href")
                    val itEps = episodes.first {epIt -> epIt.episode == episodeNumber && epIt.season == seasonNumber}
                    val parsedItEps = itEps.copy(data = "$mainUrl$href")

                    parsedEpisode.add(parsedItEps)
                    episodeNumber++;
                }

                seasonNumber++;
            }

            newTvSeriesLoadResponse(resTitle, url, TvType.TvSeries, parsedEpisode.toList()) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.tags = genres
                this.rating = rating
                this.recommendations = recommendations
                this.actors = actors
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(
                    resTitle,
                    url,
                    TvType.Movie,
                    url,
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.duration = res.runtime
                this.tags = genres
                this.rating = rating
                this.recommendations = recommendations
                this.actors = actors
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data, referer = mainUrl).document
        val movieId = document.selectFirst("[data-nume]")?.attr("data-post")

        document.select("[data-nume]").mapNotNull {
            if (it.attr("data-nume") != "trailer") {
                val movieId = it.attr("data-post")
                val idPlayer = it.attr("data-nume")

                val urlPath = if(data.contains("episode")) {
                    "$mainUrl/wp-json/dooplayer/v2/$movieId/tv/$idPlayer"
                } else {
                    "$mainUrl/wp-json/dooplayer/v2/$movieId/movie/$idPlayer"
                }

                val embedRes = app.get(urlPath).text
                val embedData = parseJson<EmbedResponse>(embedRes)

                if(embedData.embed_url.contains("fa.efek.stream")) {
                    val playerId = Regex("(?<=v\\/)(.*)(?=&)").find(embedData.embed_url)?.groupValues?.getOrNull(1)
                    val qualities = listOf<Int>(360, 720, 1080)
                    for (quality in qualities) {
                        val streamUrl = "https://d308.gshare.art/stream/$quality/$playerId/__001"
                        callback.invoke(ExtractorLink("VIP Server", "VIP Server HD", streamUrl, "https://fa.efek.stream", quality, type = ExtractorLinkType.VIDEO))
                    }
                }
            }
        }

        return true;
    }

    data class MediaDetail(
            @JsonProperty("id") val id: Int? = null,
            @JsonProperty("title") val title: String? = null,
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("poster_path") val posterPath: String? = null,
            @JsonProperty("backdrop_path") val backdropPath: String? = null,
            @JsonProperty("overview") val overview: String? = null,
            @JsonProperty("runtime") val runtime: Int? = null,
            @JsonProperty("vote_average") val vote_average: Any? = null,
            @JsonProperty("genres") val genres: ArrayList<Genres>? = arrayListOf(),
            @JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
            @JsonProperty("videos") val videos: ResultsTrailer? = null,
            @JsonProperty("credits") val credits: Credits? = null,
            @JsonProperty("last_episode_to_air") val last_episode_to_air: LastEpisodeToAir? = null,
    )

    data class Trailers(
            @JsonProperty("key") val key: String? = null,
    )

    data class ResultsTrailer(
            @JsonProperty("results") val results: ArrayList<Trailers>? = arrayListOf(),
    )

    data class AltTitles(
            @JsonProperty("iso_3166_1") val iso_3166_1: String? = null,
            @JsonProperty("title") val title: String? = null,
            @JsonProperty("type") val type: String? = null,
    )

    data class Credits(
            @JsonProperty("cast") val cast: ArrayList<Cast>? = arrayListOf(),
    )

    data class Cast(
            @JsonProperty("id") val id: Int? = null,
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("original_name") val originalName: String? = null,
            @JsonProperty("character") val character: String? = null,
            @JsonProperty("known_for_department") val knownForDepartment: String? = null,
            @JsonProperty("profile_path") val profilePath: String? = null,
    )

    data class Genres(
            @JsonProperty("id") val id: Int? = null,
            @JsonProperty("name") val name: String? = null,
    )

    data class Seasons(
            @JsonProperty("id") val id: Int? = null,
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("season_number") val seasonNumber: Int? = null,
            @JsonProperty("air_date") val airDate: String? = null,
    )

    data class Episodes(
            @JsonProperty("id") val id: Int? = null,
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("overview") val overview: String? = null,
            @JsonProperty("air_date") val airDate: String? = null,
            @JsonProperty("still_path") val stillPath: String? = null,
            @JsonProperty("vote_average") val voteAverage: Double? = null,
            @JsonProperty("episode_number") val episodeNumber: Int? = null,
            @JsonProperty("season_number") val seasonNumber: Int? = null,
    )

    data class MediaDetailEpisodes(
            @JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf(),
    )

    private fun getOriImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original/$link" else link
    }

    private fun getImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/w500/$link" else link
    }

    data class LastEpisodeToAir(
            @JsonProperty("episode_number") val episode_number: Int? = null,
            @JsonProperty("season_number") val season_number: Int? = null,
    )

}