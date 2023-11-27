// use an integer for version numbers
version = 4


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Honime plugin repository"
    authors = listOf("qolbudr")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of avaliable types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf(
            "Anime"
    )

    iconUrl = "https://honime.com/wp-content/uploads/2021/10/Honime-Favicon.png"
}