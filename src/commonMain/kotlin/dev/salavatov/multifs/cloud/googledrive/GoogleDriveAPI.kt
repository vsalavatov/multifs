package dev.salavatov.multifs.cloud.googledrive

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.json.*
import io.ktor.client.plugins.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.*

class GoogleDriveAPI(
    protected val appCredentials: GoogleAppCredentials,
    protected val authenticator: GoogleAuthenticator = sampleGoogleAuthenticator(appCredentials)
) {
    private val apiClient = HttpClient {
        expectSuccess = false
        install(JsonPlugin) {
            this.serializer = KotlinxSerializer()
        }
        install(Auth) {
            lateinit var tokenInfo: GoogleAuthTokens

            bearer {
                loadTokens {
                    tokenInfo = authenticator.authenticate()
                    BearerTokens(
                        accessToken = tokenInfo.accessToken,
                        refreshToken = tokenInfo.refreshToken!!
                    )
                }

                refreshTokens {
                    val refreshTokenInfo = authenticator.refresh(tokenInfo)
                    tokenInfo = GoogleAuthTokens(
                        refreshTokenInfo.accessToken,
                        refreshTokenInfo.expiresIn,
                        refreshTokenInfo.refreshToken ?: tokenInfo.refreshToken
                    )
                    BearerTokens(
                        accessToken = tokenInfo.accessToken,
                        refreshToken = tokenInfo.refreshToken!!
                    )
                }
            }
        }
    }
}