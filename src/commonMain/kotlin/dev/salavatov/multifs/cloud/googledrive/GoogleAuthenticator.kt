package dev.salavatov.multifs.cloud.googledrive

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class GoogleAppCredentials(val clientId: String, val secret: String)

@Serializable
data class GoogleAuthTokens(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("refresh_token") val refreshToken: String?
)

interface GoogleAuthenticator {
    suspend fun authenticate(): GoogleAuthTokens
    suspend fun refresh(expired: GoogleAuthTokens): GoogleAuthTokens
}

expect fun sampleApplicableGoogleAuthenticator(appCredentials: GoogleAppCredentials): GoogleAuthenticator // TODO: rename?