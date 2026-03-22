package com.alancruse.wallpaper

import android.Manifest
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.alancruse.wallpaper.auth.GoogleAuthManager
import com.alancruse.wallpaper.pairing.PairingManager
import com.alancruse.wallpaper.pairing.PairingPrefs
import com.alancruse.wallpaper.ui.AuthScreen
import com.alancruse.wallpaper.ui.HomeScreen
import com.alancruse.wallpaper.ui.PairingScreen
import com.alancruse.wallpaper.ui.theme.DeepNavy
import com.alancruse.wallpaper.ui.theme.CyanAccent
import com.alancruse.wallpaper.ui.theme.WallPaperTheme
import com.alancruse.wallpaper.wallpaper.LiveWallpaperService
import com.google.firebase.auth.FirebaseUser

private enum class AppState {
    LOADING, SIGNED_OUT, NEEDS_PAIRING, PAIRED
}

class MainActivity : ComponentActivity() {

    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var pairingPrefs: PairingPrefs

    // Compose state at activity level so signInLauncher can update it
    private var appState by mutableStateOf(AppState.LOADING)
    private var currentUser by mutableStateOf<FirebaseUser?>(null)
    private var partnerUid by mutableStateOf<String?>(null)

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        googleAuthManager.handleSignInResult(result.data) { success, errorMsg ->
            if (success) {
                val user = googleAuthManager.getCurrentUser()
                currentUser = user
                if (user != null) {
                    PairingManager(user.uid).checkExistingPairing { existingPartner ->
                        if (existingPartner != null) {
                            partnerUid = existingPartner
                            pairingPrefs.savePairing(user.uid, existingPartner)
                            appState = AppState.PAIRED
                        } else {
                            appState = AppState.NEEDS_PAIRING
                        }
                    }
                }
            } else {
                Log.e("Auth", "Sign in failed: $errorMsg")
                Toast.makeText(this, "Sign in failed: $errorMsg", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("Permissions", "Notification permission granted: $granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        googleAuthManager = GoogleAuthManager(this)
        pairingPrefs = PairingPrefs(this)
        currentUser = googleAuthManager.getCurrentUser()

        requestNotificationPermission()

        // Check initial state
        val user = currentUser
        if (user == null) {
            appState = AppState.SIGNED_OUT
        } else {
            appState = AppState.LOADING
            PairingManager(user.uid).checkExistingPairing { existingPartner ->
                if (existingPartner != null) {
                    partnerUid = existingPartner
                    pairingPrefs.savePairing(user.uid, existingPartner)
                    appState = AppState.PAIRED
                } else {
                    appState = AppState.NEEDS_PAIRING
                }
            }
        }

        setContent {
            WallPaperTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DeepNavy
                ) {
                    when (appState) {
                        AppState.LOADING -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = CyanAccent)
                            }
                        }

                        AppState.SIGNED_OUT -> {
                            AuthScreen(
                                onSignInClick = {
                                    signInLauncher.launch(googleAuthManager.getSignInIntent())
                                }
                            )
                        }

                        AppState.NEEDS_PAIRING -> {
                            PairingScreen(
                                currentUid = currentUser!!.uid,
                                onPairedSuccess = { pUid ->
                                    partnerUid = pUid
                                    pairingPrefs.savePairing(currentUser!!.uid, pUid)
                                    appState = AppState.PAIRED
                                }
                            )
                        }

                        AppState.PAIRED -> {
                            HomeScreen(
                                partnerUid = partnerUid ?: "",
                                onOpenCanvas = {
                                    val intent = Intent(
                                        this@MainActivity,
                                        LiveLockscreenActivity::class.java
                                    ).apply {
                                        putExtra("currentUid", currentUser!!.uid)
                                        putExtra("partnerUid", partnerUid)
                                    }
                                    startActivity(intent)
                                },
                                onActivateWallpaper = {
                                    launchWallpaperPicker()
                                },
                                onUnpair = {
                                    val uid = currentUser!!.uid
                                    val pUid = partnerUid ?: return@HomeScreen
                                    PairingManager(uid).unpair(pUid) {
                                        partnerUid = null
                                        pairingPrefs.clear()
                                        appState = AppState.NEEDS_PAIRING
                                    }
                                },
                                onSignOut = {
                                    val uid = currentUser!!.uid
                                    val pUid = partnerUid
                                    if (pUid != null) {
                                        PairingManager(uid).unpair(pUid) {
                                            googleAuthManager.signOut()
                                            currentUser = null
                                            partnerUid = null
                                            pairingPrefs.clear()
                                            appState = AppState.SIGNED_OUT
                                        }
                                    } else {
                                        googleAuthManager.signOut()
                                        currentUser = null
                                        partnerUid = null
                                        pairingPrefs.clear()
                                        appState = AppState.SIGNED_OUT
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Launch the system live wallpaper picker pre-selecting our WallPaper service.
     */
    private fun launchWallpaperPicker() {
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, LiveWallpaperService::class.java)
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback: open generic live wallpaper picker
            try {
                val intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(this, "Could not open wallpaper picker", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
