package com.alancruse.wallpaper.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.alancruse.wallpaper.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class GoogleAuthManager(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    
    private val signInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    fun getSignInIntent(): Intent {
        return signInClient.signInIntent
    }

    fun handleSignInResult(data: Intent?, onComplete: (Boolean, String?) -> Unit) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)!!
            firebaseAuthWithGoogle(account.idToken!!, onComplete)
        } catch (e: ApiException) {
            onComplete(false, e.localizedMessage)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String, onComplete: (Boolean, String?) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener() { task ->
                if (task.isSuccessful) {
                    onComplete(true, null)
                } else {
                    onComplete(false, task.exception?.localizedMessage)
                }
            }
    }
    
    fun getCurrentUser() = auth.currentUser
    
    fun signOut() {
        auth.signOut()
        signInClient.signOut()
    }
}
