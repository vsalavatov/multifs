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
import dev.salavatov.multifs.cloud.googledrive.GoogleAuthorizationRequester.Companion.REDIRECT_URI_PROGRAMMATIC_EXTRACTION
import dev.salavatov.multifs.cloud.googledrive.GoogleAuthorizationRequester.Companion.exchangeAuthCodeOnTokens
import dev.salavatov.multifs.cloud.googledrive.GoogleAuthorizationRequester.Companion.tryRefreshAuthorization

class IntentGoogleAuthorizationRequester(
    private val appCredentials: GoogleAppCredentials,
    val scope: GoogleDriveAPI.Companion.DriveScope,
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
        if (!task.isComplete)
            throw GoogleDriveAPIException("sign in flow didn't complete")
        val serverAuthCode = task.result.serverAuthCode!!
        return exchangeAuthCodeOnTokens(appCredentials, REDIRECT_URI_PROGRAMMATIC_EXTRACTION, serverAuthCode)
    }

    override suspend fun refreshAuthorization(expired: GoogleAuthTokens): GoogleAuthTokens =
        tryRefreshAuthorization(appCredentials, expired) { requestAuthorization() }

    private fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestServerAuthCode(appCredentials.clientId, true)
            .requestScopes(Scope(scope.value))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }
}