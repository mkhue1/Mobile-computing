package com.example.gamercalendar.data.repository

import android.content.Context
import com.example.gamercalendar.data.model.SessionPlace
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.tasks.await

data class PlaceSuggestion(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String
)

class PlaceRepository(private val client: PlacesClient) {

    // Google bills autocomplete per session: every search until a place is fetched shares one token.
    private var sessionToken: AutocompleteSessionToken? = null

    suspend fun search(query: String): List<PlaceSuggestion> {
        val token = sessionToken ?: AutocompleteSessionToken.newInstance().also { sessionToken = it }
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(token)
            .build()

        return client.findAutocompletePredictions(request).await()
            .autocompletePredictions
            .map { prediction ->
                PlaceSuggestion(
                    placeId = prediction.placeId,
                    primaryText = prediction.getPrimaryText(null).toString(),
                    secondaryText = prediction.getSecondaryText(null).toString()
                )
            }
    }

    suspend fun getPlace(placeId: String): SessionPlace {
        val request = FetchPlaceRequest.builder(placeId, PLACE_FIELDS)
            .setSessionToken(sessionToken)
            .build()
        sessionToken = null

        val place = client.fetchPlace(request).await().place
        val location = place.location ?: throw IllegalStateException("That place has no map location")
        return SessionPlace(
            name = place.displayName ?: place.formattedAddress ?: "Unnamed place",
            address = place.formattedAddress,
            placeId = place.id ?: placeId,
            lat = location.latitude,
            lng = location.longitude
        )
    }

    companion object {
        private val PLACE_FIELDS = listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.FORMATTED_ADDRESS,
            Place.Field.LOCATION
        )

        /** Null when Places wasn't initialised because no Maps API key is configured. */
        fun createOrNull(context: Context): PlaceRepository? {
            if (!Places.isInitialized()) return null
            return PlaceRepository(Places.createClient(context))
        }
    }
}
