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
import com.alancruse.wallpaper.pairing.GroupManager
import com.alancruse.wallpaper.pairing.PairingManager
import com.alancruse.wallpaper.pairing.PairingPrefs
import com.alancruse.wallpaper.ui.*
import com.alancruse.wallpaper.ui.theme.DeepNavy
import com.alancruse.wallpaper.ui.theme.CyanAccent
import com.alancruse.wallpaper.ui.theme.WallPaperTheme
import com.alancruse.wallpaper.wallpaper.LiveWallpaperService
import com.google.firebase.auth.FirebaseUser

private enum class AppState {
    LOADING, SIGNED_OUT, NEEDS_PAIRING, HOME, GROUP_SCREEN, GROUP_MANAGE, CANVAS
}

class MainActivity : ComponentActivity() {

    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var pairingPrefs: PairingPrefs

    // Compose state at activity level
    private var appState by mutableStateOf(AppState.LOADING)
    private var currentUser by mutableStateOf<FirebaseUser?>(null)
    private var partnerUid by mutableStateOf<String?>(null)
    private var groups by mutableStateOf<List<GroupManager.GroupInfo>>(emptyList())

    // Canvas navigation state
    private var pendingRoomId by mutableStateOf<String?>(null)
    private var pendingRoomType by mutableStateOf<String?>(null)
    private var managingGroupId by mutableStateOf<String?>(null)

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        googleAuthManager.handleSignInResult(result.data) { success, errorMsg ->
            if (success) {
                val user = googleAuthManager.getCurrentUser()
                currentUser = user
                if (user != null) {
                    checkPairingAndLoadGroups(user.uid)
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

        val user = currentUser
        if (user == null) {
            appState = AppState.SIGNED_OUT
        } else {
            appState = AppState.LOADING
            checkPairingAndLoadGroups(user.uid)
        }

        setContent {
            WallPaperTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = DeepNavy) {
                    when (appState) {
                        AppState.LOADING -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                                    loadGroups(currentUser!!.uid)
                                    appState = AppState.HOME
                                },
                                onSkipToGroups = {
                                    loadGroups(currentUser!!.uid)
                                    appState = AppState.HOME
                                }
                            )
                        }

                        AppState.HOME -> {
                            // Auto-switch dialog state
                            var showAutoSwitch by remember { mutableStateOf(false) }

                            HomeScreen(
                                currentUid = currentUser!!.uid,
                                partnerUid = partnerUid,
                                groups = groups,
                                activeWallpaperRoomId = pairingPrefs.activeRoomId ?: pairingPrefs.getCoupleRoomId(),
                                lockscreenRoomId = pairingPrefs.lockscreenRoomId,
                                onOpenCanvas = { roomId, roomType ->
                                    openCanvas(roomId)
                                },
                                onSetWallpaper = { roomId, roomType ->
                                    pairingPrefs.setActiveWallpaper(roomId, roomType)
                                    // Force recompose
                                    val g = groups; groups = emptyList(); groups = g
                                    Toast.makeText(this@MainActivity, "🏠 Home wallpaper set!", Toast.LENGTH_SHORT).show()
                                },
                                onSetLockscreen = { roomId, roomType ->
                                    pairingPrefs.setLockscreenWallpaper(roomId, roomType)
                                    // Start the lock screen wallpaper rendering service
                                    com.alancruse.wallpaper.wallpaper.LockscreenWallpaperService.start(this@MainActivity)
                                    // Force recompose
                                    val g = groups; groups = emptyList(); groups = g
                                    Toast.makeText(this@MainActivity, "🔒 Lock screen set!", Toast.LENGTH_SHORT).show()
                                },
                                onActivateWallpaper = { launchWallpaperPicker() },
                                onManageGroups = { appState = AppState.GROUP_SCREEN },
                                onManageGroup = { groupId ->
                                    managingGroupId = groupId
                                    appState = AppState.GROUP_MANAGE
                                },
                                onLeaveGroup = { groupId ->
                                    GroupManager(currentUser!!.uid).leaveGroup(groupId) {
                                        pairingPrefs.removeGroupRoomId("group_$groupId")
                                        loadGroups(currentUser!!.uid)
                                    }
                                },
                                onUnpair = {
                                    val uid = currentUser!!.uid
                                    val pUid = partnerUid ?: return@HomeScreen
                                    PairingManager(uid).unpair(pUid) {
                                        partnerUid = null
                                        pairingPrefs.clearCoupleOnly()
                                        if (groups.isEmpty()) {
                                            appState = AppState.NEEDS_PAIRING
                                        }
                                        // Stay on HOME if user has groups
                                    }
                                },
                                onSignOut = {
                                    val uid = currentUser!!.uid
                                    val pUid = partnerUid
                                    val doSignOut = {
                                        googleAuthManager.signOut()
                                        currentUser = null
                                        partnerUid = null
                                        groups = emptyList()
                                        pairingPrefs.clear()
                                        appState = AppState.SIGNED_OUT
                                    }
                                    if (pUid != null) {
                                        PairingManager(uid).unpair(pUid) { doSignOut() }
                                    } else {
                                        doSignOut()
                                    }
                                },
                                onAutoSwitch = { showAutoSwitch = true }
                            )

                            // Auto-switch dialog
                            if (showAutoSwitch) {
                                val availableRooms = buildList {
                                    val coupleRoom = pairingPrefs.getCoupleRoomId()
                                    if (coupleRoom != null) {
                                        add(Triple(coupleRoom, "couple", "❤️ Partner Canvas"))
                                    }
                                    groups.forEach { g ->
                                        add(Triple(g.roomId, "group", "👥 ${g.name}"))
                                    }
                                }
                                AutoSwitchDialog(
                                    availableRooms = availableRooms,
                                    onDismiss = { showAutoSwitch = false }
                                )
                            }
                        }

                        AppState.GROUP_SCREEN -> {
                            GroupScreen(
                                currentUid = currentUser!!.uid,
                                displayName = currentUser?.displayName ?: "User",
                                onGroupCreated = { groupId, roomId ->
                                    pairingPrefs.addGroupRoomId(roomId)
                                    loadGroups(currentUser!!.uid)
                                    // Go home so user sees the new group in the list
                                    appState = AppState.HOME
                                },
                                onGroupJoined = { groupId, roomId ->
                                    pairingPrefs.addGroupRoomId(roomId)
                                    loadGroups(currentUser!!.uid)
                                    appState = AppState.HOME
                                },
                                onBack = {
                                    loadGroups(currentUser!!.uid)
                                    appState = AppState.HOME
                                }
                            )
                        }

                        AppState.GROUP_MANAGE -> {
                            val gid = managingGroupId
                            if (gid != null) {
                                GroupManageScreen(
                                    currentUid = currentUser!!.uid,
                                    groupId = gid,
                                    onBack = {
                                        loadGroups(currentUser!!.uid)
                                        appState = AppState.HOME
                                    }
                                )
                            } else {
                                appState = AppState.HOME
                            }
                        }

                        AppState.CANVAS -> {
                            // Canvas is now in its own activity, handled by openCanvas()
                            // This state shouldn't normally be reached
                            appState = AppState.HOME
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh groups when returning from canvas
        val user = currentUser
        if (user != null && appState == AppState.HOME) {
            loadGroups(user.uid)
        }
    }

    private fun checkPairingAndLoadGroups(uid: String) {
        PairingManager(uid).checkExistingPairing { existingPartner ->
            if (existingPartner != null) {
                partnerUid = existingPartner
                pairingPrefs.savePairing(uid, existingPartner)
            }
            loadGroups(uid)
            // Start lock screen service if a lockscreen room is set
            if (pairingPrefs.lockscreenRoomId != null) {
                com.alancruse.wallpaper.wallpaper.LockscreenWallpaperService.start(this)
            }
            // Go to home if paired or has groups, otherwise needs pairing
            if (existingPartner != null || pairingPrefs.getGroupRoomIds().isNotEmpty()) {
                appState = AppState.HOME
            } else {
                appState = AppState.NEEDS_PAIRING
            }
        }
    }

    private fun loadGroups(uid: String) {
        GroupManager(uid).getMyGroups { groupList ->
            groups = groupList
        }
    }

    private fun openCanvas(roomId: String) {
        val intent = Intent(this, LiveLockscreenActivity::class.java).apply {
            putExtra("roomId", roomId)
            // Also pass UIDs for backward compat
            putExtra("currentUid", currentUser?.uid)
            putExtra("partnerUid", partnerUid)
        }
        startActivity(intent)
    }

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
