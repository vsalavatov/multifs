package dev.salavatov.multifs.cloud.googledrive

import io.ktor.client.*
import io.ktor.client.features.auth.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.*

// inspired by https://github.com/ktorio/ktor-documentation/blob/main/codeSnippets/snippets/client-auth-oauth-google/src/main/kotlin/com/example/Application.kt
class GoogleDriveAPI(protected val authenticator: GoogleAuthenticator) {
    private val apiClient = HttpClient {
        expectSuccess = false
        install(JsonFeature) {
            serializer = KotlinxSerializer()
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

                refreshTokens { unauthorizedResponse: HttpResponse ->
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