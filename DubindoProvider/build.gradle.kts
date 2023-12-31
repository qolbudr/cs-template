// use an integer for version numbers
version = 12


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Dubindo plugin repository"
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
            "Cartoon",
            "TvSeries",
            "Movie"
    )

    iconUrl = "https://sg-res.9appsdownloading.com/sg/res/jpg/c6/16/398d1dc95da0016cef52175d11e2-if31.jpg"
}
