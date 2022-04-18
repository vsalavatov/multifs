package dev.salavatov.multifs.cloud.googledrive

import io.ktor.client.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

data class GoogleAppCredentials(val clientId: String, val secret: String)

@Serializable
data class GoogleAuthTokens(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null
)

interface GoogleAuthorizationRequester {
    suspend fun requestAuthorization(): GoogleAuthTokens
    suspend fun refreshAuthorization(expired: GoogleAuthTokens): GoogleAuthTokens

    companion object {
        val jsonParser = Json { ignoreUnknownKeys = true }

        suspend fun exchangeAuthCodeOnTokens(
            appCredentials: GoogleAppCredentials,
            redirectUri: String,
            code: String
        ): GoogleAuthTokens {
            val tokenClient = HttpClient() { expectSuccess = false }
            val response =
                tokenClient.submitForm(url = "https://oauth2.googleapis.com/token", formParameters = Parameters.build {
                    append("code", code)
                    append("client_id", appCredentials.clientId)
                    append("client_secret", appCredentials.secret)
                    append("grant_type", "authorization_code")
                    append("redirect_uri", redirectUri)
                })
            if (response.status.value != 200) throw GoogleDriveAPIException("authenticator: failed to get tokens: ${response.status}")
            val rawData = response.bodyAsText()
            return jsonParser.decodeFromString(rawData)
        }

        suspend fun tryRefreshAuthorization(
            appCredentials: GoogleAppCredentials,
            expired: GoogleAuthTokens,
            requestNewAuthorization: suspend () -> GoogleAuthTokens
        ): GoogleAuthTokens {
            val tokenClient = HttpClient() { expectSuccess = false }
            val response =
                tokenClient.submitForm(url = "https://oauth2.googleapis.com/token", formParameters = Parameters.build {
                    append("refresh_token", expired.refreshToken!!)
                    append("client_id", appCredentials.clientId)
                    append("client_secret", appCredentials.secret)
                    append("grant_type", "refresh_token")
                })
            if (response.status.value == 400) { // TODO: && response.bodyAsText().contains("expired") doesn't work, can also contain "invalid_grant"
                return requestNewAuthorization()
            }
            if (response.status.value != 200) throw GoogleDriveAPIException("authenticator: failed to refresh tokens: ${response.status}")
            val rawData = response.bodyAsText()
            return jsonParser.decodeFromString(rawData)
        }

        const val REDIRECT_URI_PROGRAMMATIC_EXTRACTION = "urn:ietf:wg:oauth:2.0:oob:auto"
    }
}