package org.opened.app.core.drive

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opened.app.core.datastore.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val driveScope = Scope(DriveScopes.DRIVE_FILE)
    private val appDataScope = Scope(DriveScopes.DRIVE_APPDATA)

    private val gso: GoogleSignInOptions by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(driveScope, appDataScope)
            .build()
    }

    val signInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, gso)
    }

    fun getLastSignedInAccount(): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return if (account != null && GoogleSignIn.hasPermissions(account, driveScope)) {
            account
        } else {
            null
        }
    }

    suspend fun handleSignInResult(account: GoogleSignInAccount?) = withContext(Dispatchers.IO) {
        if (account != null) {
            preferencesRepository.setBackupAccountEmail(account.email)
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        signInClient.signOut()
        preferencesRepository.setBackupAccountEmail(null)
    }

    fun isAuthorized(): Boolean {
        return getLastSignedInAccount() != null
    }
}
