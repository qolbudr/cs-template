import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addPoster
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class BolaaProvider : MainAPI() {
    override var mainUrl = "https://nobarbolagratis.live"
    override var name = "Bolaa"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "id"
    override val hasMainPage = true

    override val mainPage: List<MainPageData> = mainPageOf(
            "$mainUrl" to "Live Sekarang",
            "$mainUrl" to "Hari Ini",
            "$mainUrl" to "Besok",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val type = request.name

        val document = when (type) {
            "Live Sekarang" -> {
                app.post("$mainUrl/ajaxLive/").document
            }
            "Hari Ini" -> {
                app.post("$mainUrl/ajaxToday/").document
            }
            else -> {
                app.post("$mainUrl/ajaxBesok/").document
            }
        }

        val selector = when (type) {
            "Live Sekarang" -> {
                "#live"
            }
            "Hari Ini" -> {
                "#today"
            }
            else -> {
                "#besok"
            }
        }

        val result = document.select("div.match-list").mapNotNull {
            val team = it.select(".team-name").mapNotNull { team -> team.text().trim() }
            val title = team.joinToString(" Vs ")
            val time = if(selector.contains("live")) {
                "live"
            } else {
                val textTime = it.select("a").text()
                Regex("(?<=[0-9][0-9][0-9][0-9] )(.*)(?= WIB)").find(textTime)?.groupValues?.get(1) ?: "-"
            }
            val tournament = it.select(".liga-name").text().trim()

            val img = it.select(".w-5 img").mapNotNull  {imageTeam -> imageTeam.attr("src") }
            val codeImg = img.mapNotNull { codee -> Regex("(?<=team\\/)(.*)(?=.png)").find(codee)?.groupValues?.get(1) ?: "" }

            val realImg = "https://cs-backend-navy.vercel.app/image?home=${codeImg[0]}&away=${codeImg[1]}&time=$time&nameHome=${team[0] ?: "-"}&nameAway=${team[1] ?: "-"}&tournament=$tournament"

            val url = it.select("a").attr("href")
            val parsedUrl = "$mainUrl$url|$selector"

            newMovieSearchResponse(title, parsedUrl, TvType.Live) {
                this.posterUrl = realImg
            }
        }

        return newHomePageResponse(request.name, result, hasNext = false)
    }

    override suspend fun load(url: String): LoadResponse? {
        val realUrl = url.split("|")[0]
        val document = app.get(realUrl!!).document
        val title = document.select(".blog-post h1").text().trim()
        val image = "https://www.penalutim.com/wp-content/uploads/2017/10/permukaan-lapangan-sepak-bola.jpg"
        val comingSoon = url.contains("today") || url.contains("besok")

        return newMovieLoadResponse(title, realUrl, TvType.Live, realUrl) {
            addPoster(image)
            this.comingSoon = comingSoon
            this.plot = "Live streaming nonton pertandingan $title gratis"
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        val idMatch = data.split("-").last()
        var serverNumber = 1;

        document.select(".mirror-item .text").mapNotNull {
            val embedUrl = "$mainUrl/stream/$idMatch?s=$serverNumber"
            val resEmbed = app.get(embedUrl, referer = mainUrl).text
            val resUrl = Regex("(?<=playNormal\\(')(.*)(?=')").find(resEmbed)?.groupValues?.get(1)

            if(resUrl != null) {
                callback.invoke(
                        ExtractorLink(
                                "Bolaa Server - Server $serverNumber",
                                "Bolaa Server - Server $serverNumber",
                                resUrl,
                                referer = mainUrl,
                                quality = Qualities.Unknown.value,
                                isM3u8 = resUrl.contains("m3u8")
                        )

                )
            }

            serverNumber++;
        }

        return true
    }
}