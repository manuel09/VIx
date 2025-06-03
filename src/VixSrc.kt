package com.vixsrc

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class VixSrc : MainAPI() {
    override var mainUrl = "https://vixsrc.to"
    override var name = "VixSrc"
    override val hasMainPage = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val tmdbApiKey = "7c6df7a1b0b016561e6c2dcd26ecf711"

    override suspend fun search(query: String): List<SearchResponse> {
        val tmdbUrl = "https://api.themoviedb.org/3/search/multi?api_key=$tmdbApiKey&query=${query.encodeURL()}"
        val results = app.get(tmdbUrl).parsedSafe<TmdbSearchResult>() ?: return emptyList()

        return results.results.mapNotNull {
            when (it.media_type) {
                "movie" -> MovieSearchResponse(
                    it.title ?: return@mapNotNull null,
                    "$mainUrl/movie/${it.id}",
                    TvType.Movie,
                    it.poster_path?.let { path -> "https://image.tmdb.org/t/p/w500$path" },
                    null,
                    it.release_date?.split("-")?.firstOrNull()?.toIntOrNull()
                )

                "tv" -> TvSeriesSearchResponse(
                    it.name ?: return@mapNotNull null,
                    "$mainUrl/tv/${it.id}/1/1",
                    TvType.TvSeries,
                    it.poster_path?.let { path -> "https://image.tmdb.org/t/p/w500$path" },
                    null,
                    it.first_air_date?.split("-")?.firstOrNull()?.toIntOrNull()
                )

                else -> null
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val isMovie = url.contains("/movie/")
        val tmdbId = Regex("/movie/(\\d+)|/tv/(\\d+)/").find(url)?.groupValues?.filterNot(String::isEmpty)?.getOrNull(1)?.toIntOrNull()
            ?: throw ErrorLoadingException("Invalid URL")

        val tmdbDetailsUrl = if (isMovie)
            "https://api.themoviedb.org/3/movie/$tmdbId?api_key=$tmdbApiKey"
        else
            "https://api.themoviedb.org/3/tv/$tmdbId?api_key=$tmdbApiKey"

        val details = app.get(tmdbDetailsUrl).parsedSafe<TmdbDetails>() ?: throw ErrorLoadingException("Can't get TMDB info")

        if (isMovie) {
            return newMovieLoadResponse(
                title = details.title ?: "",
                url = url,
                dataUrl = "$mainUrl/movie/$tmdbId"
            ) {
                posterUrl = details.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                year = details.release_date?.split("-")?.firstOrNull()?.toIntOrNull()
                plot = details.overview
            }
        } else {
            val episodes = mutableListOf<Episode>()
            details.number_of_seasons?.let { seasons ->
                for (season in 1..seasons) {
                    for (episode in 1..30) { // Arbitrary max
                        val epUrl = "$mainUrl/tv/$tmdbId/$season/$episode"
                        episodes.add(Episode(epUrl, "Stagione $season Episodio $episode"))
                    }
                }
            }

            return newTvSeriesLoadResponse(
                title = details.name ?: "",
                url = url,
                episodes = episodes
            ) {
                posterUrl = details.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                year = details.first_air_date?.split("-")?.firstOrNull()?.toIntOrNull()
                plot = details.overview
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val embeddedUrl = data + "?autoplay=true"
        callback(
            ExtractorLink(
                name = "VixSrc",
                source = "vixsrc.to",
                url = embeddedUrl,
                referer = mainUrl,
                quality = Qualities.Unknown.value,
                isM3u8 = true
            )
        )
    }
}

// JSON Data Models

data class TmdbSearchResult(val results: List<TmdbItem>)
data class TmdbItem(
    val id: Int,
    val media_type: String,
    val name: String?,
    val title: String?,
    val poster_path: String?,
    val release_date: String?,
    val first_air_date: String?
)

data class TmdbDetails(
    val title: String?,
    val name: String?,
    val poster_path: String?,
    val overview: String?,
    val release_date: String?,
    val first_air_date: String?,
    val number_of_seasons: Int?
)

