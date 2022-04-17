package dev.salavatov.multifs.cloud.googledrive

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class IntentGoogleAuthorizationRequester(
    private val appCredentials: GoogleAppCredentials,
    private val context: Context,
    private val triggerSignIn: suspend (Intent) -> ActivityResult,
) : GoogleAuthorizationRequester {

    private val googleSignInClient = getGoogleSignInClient()

    override suspend fun requestAuthorization(): GoogleAuthTokens {
        val result = triggerSignIn(googleSignInClient.signInIntent)
        if (result.resultCode != Activity.RESULT_OK) {
            throw RuntimeException("activity result code: ${result.resultCode}; googleSignInStatus: ${result.data!!.extras!!["googleSignInStatus"]}")
        }
        val intent = result.data!!
        val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(intent)
        assert(task.isComplete)
        val serverAuthCode = task.result.serverAuthCode!!
        return exchangeAuthCodeOnTokens("urn:ietf:wg:oauth:2.0:oob", serverAuthCode)
    }

    private suspend fun exchangeAuthCodeOnTokens(redirectUri: String, code: String): GoogleAuthTokens {
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
        return Json { ignoreUnknownKeys = true }.decodeFromString(rawData)
    }

    override suspend fun refreshAuthorization(expired: GoogleAuthTokens): GoogleAuthTokens {
        val tokenClient = HttpClient() { expectSuccess = false }
        val response =
            tokenClient.submitForm(url = "https://oauth2.googleapis.com/token", formParameters = Parameters.build {
                append("refresh_token", expired.refreshToken!!)
                append("client_id", appCredentials.clientId)
                append("client_secret", appCredentials.secret)
                append("grant_type", "refresh_token")
            })
        if (response.status.value == 400) { // TODO: && response.bodyAsText().contains("expired") doesn't work, can also contain "invalid_grant"
            return requestAuthorization()
        }
        if (response.status.value != 200) throw GoogleDriveAPIException("authenticator: failed to refresh tokens: ${response.status}")
        val rawData = response.bodyAsText()
        return Json { ignoreUnknownKeys = true }.decodeFromString(rawData)
    }

    private fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestServerAuthCode(appCredentials.clientId, true).requestScopes(Scope(DRIVE_SCOPE)).build()
        return GoogleSignIn.getClient(context, gso)
    }

    companion object {
        // TODO: extract these
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
    }
}