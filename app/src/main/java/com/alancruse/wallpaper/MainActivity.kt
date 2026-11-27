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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.alancruse.wallpaper.ui.theme.WallPaperTheme
import com.alancruse.wallpaper.wallpaper.LiveWallpaperService
import com.google.firebase.auth.FirebaseUser

private enum class AppState {
    LOADING, SIGNED_OUT, PERMISSIONS, NEEDS_PAIRING, HOME, GROUP_SCREEN, GROUP_MANAGE, CANVAS, SETTINGS
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
    private var pairingFromApp by mutableStateOf(false)
    private var showSchedule by mutableStateOf(false)
    private var homeRoomId by mutableStateOf<String?>(null)
    private var lockRoomId by mutableStateOf<String?>(null)
    private var thumbVersion by mutableIntStateOf(0)
    private var afterPermissions by mutableStateOf(AppState.HOME)
    private var permissionsFromSettings by mutableStateOf(false)

    private val isDeveloper get() = currentUser?.email.equals(DEVELOPER_EMAIL, ignoreCase = true)

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

        val user = currentUser
        if (user == null) {
            appState = AppState.SIGNED_OUT
        } else {
            appState = AppState.LOADING
            checkPairingAndLoadGroups(user.uid)
        }

        setContent {
            WallPaperTheme {
                val c = com.alancruse.wallpaper.ui.theme.LocalWallpaperColors.current
                val back: (() -> Unit)? = when (appState) {
                    AppState.GROUP_SCREEN, AppState.GROUP_MANAGE, AppState.SETTINGS -> ({ goHome() })
                    AppState.PERMISSIONS -> if (permissionsFromSettings) ({ permissionsFromSettings = false; appState = AppState.SETTINGS }) else null
                    AppState.NEEDS_PAIRING -> if (pairingFromApp) ({ pairingFromApp = false; appState = AppState.HOME }) else null
                    else -> null
                }
                BackHandler(enabled = back != null) { back?.invoke() }

                Surface(modifier = Modifier.fillMaxSize(), color = c.background) {
                    AnimatedContent(
                        targetState = appState,
                        transitionSpec = {
                            val forward = targetState.ordinal > initialState.ordinal
                            (fadeIn(tween(280)) + scaleIn(spring(0.8f, 300f), 0.96f) + slideInHorizontally(spring(0.85f, 320f)) { if (forward) it / 5 else -it / 5 }) togetherWith
                                (fadeOut(tween(180)) + scaleOut(tween(260), 0.97f) + slideOutHorizontally(tween(300)) { if (forward) -it / 6 else it / 6 })
                        },
                        label = "nav"
                    ) { state ->
                        when (state) {
                            AppState.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp)
                            }

                            AppState.SIGNED_OUT -> AuthScreen(onSignInClick = { signInLauncher.launch(googleAuthManager.getSignInIntent()) })

                            AppState.PERMISSIONS -> PermissionsScreen(
                                onDone = {
                                    pairingPrefs.permissionsOnboarded = true
                                    if (permissionsFromSettings) { permissionsFromSettings = false; appState = AppState.SETTINGS }
                                    else appState = afterPermissions
                                },
                                onBack = if (permissionsFromSettings) ({ permissionsFromSettings = false; appState = AppState.SETTINGS }) else null
                            )

                            AppState.NEEDS_PAIRING -> PairingScreen(
                                currentUid = currentUser!!.uid,
                                onPairedSuccess = { pUid ->
                                    partnerUid = pUid
                                    pairingPrefs.savePairing(currentUser!!.uid, pUid)
                                    refreshWallpaperIds()
                                    pairingFromApp = false
                                    loadGroups(currentUser!!.uid)
                                    appState = AppState.HOME
                                },
                                onSkipToGroups = { loadGroups(currentUser!!.uid); appState = AppState.GROUP_SCREEN },
                                onBack = if (pairingFromApp) ({ pairingFromApp = false; appState = AppState.HOME }) else null,
                                onDeveloperBypass = if (isDeveloper) ({ enterDeveloperMode() }) else null
                            )

                            AppState.HOME -> HomeScreen(
                                rooms = rooms(),
                                isPaired = partnerUid != null,
                                homeRoomId = homeRoomId,
                                lockRoomId = lockRoomId,
                                thumbVersion = thumbVersion,
                                onOpenCanvas = { openCanvas(it.roomId) },
                                onSetHome = {
                                    pairingPrefs.setActiveWallpaper(it.roomId, it.roomType); refreshWallpaperIds()
                                },
                                onSetLock = {
                                    pairingPrefs.setLockscreenWallpaper(it.roomId, it.roomType); refreshWallpaperIds()
                                    com.alancruse.wallpaper.wallpaper.LockscreenWallpaperService.start(this@MainActivity)
                                },
                                onManageGroup = { managingGroupId = it; appState = AppState.GROUP_MANAGE },
                                onLeaveGroup = { gid ->
                                    GroupManager(currentUser!!.uid).leaveGroup(gid) {
                                        pairingPrefs.removeGroupRoomId("group_$gid")
                                        refreshWallpaperIds()
                                        loadGroups(currentUser!!.uid)
                                    }
                                },
                                onGroups = { appState = AppState.GROUP_SCREEN },
                                onPair = { pairingFromApp = true; appState = AppState.NEEDS_PAIRING },
                                onApplyWallpaper = { launchWallpaperPicker() },
                                onSchedule = { showSchedule = true },
                                onSettings = { appState = AppState.SETTINGS }
                            )

                            AppState.GROUP_SCREEN -> GroupScreen(
                                currentUid = currentUser!!.uid,
                                displayName = currentUser?.displayName ?: "User",
                                onGroupCreated = { _, roomId ->
                                    pairingPrefs.addGroupRoomId(roomId)
                                    loadGroups(currentUser!!.uid)
                                    appState = AppState.HOME
                                    openCanvas(roomId)
                                },
                                onGroupJoined = { _, roomId ->
                                    pairingPrefs.addGroupRoomId(roomId)
                                    loadGroups(currentUser!!.uid)
                                    appState = AppState.HOME
                                },
                                onBack = { goHome() }
                            )

                            AppState.GROUP_MANAGE -> {
                                val gid = managingGroupId
                                if (gid != null) GroupManageScreen(currentUid = currentUser!!.uid, groupId = gid, onBack = { goHome() })
                            }

                            AppState.SETTINGS -> SettingsScreen(
                                currentUser = currentUser!!,
                                isPaired = partnerUid != null,
                                hasLockscreen = lockRoomId != null,
                                onPair = { pairingFromApp = true; appState = AppState.NEEDS_PAIRING },
                                onUnpair = {
                                    val uid = currentUser!!.uid
                                    val pUid = partnerUid ?: return@SettingsScreen
                                    val unpair: (() -> Unit) -> Unit = { done ->
                                        if (pUid == DEV_PARTNER_UID) { pairingPrefs.developerPaired = false; done() }
                                        else PairingManager(uid).unpair(pUid, done)
                                    }
                                    unpair {
                                        partnerUid = null
                                        pairingPrefs.clearCoupleOnly()
                                        refreshWallpaperIds()
                                        appState = if (groups.isEmpty()) AppState.NEEDS_PAIRING else AppState.HOME
                                    }
                                },
                                onSignOut = {
                                    val uid = currentUser!!.uid
                                    val pUid = partnerUid
                                    val doSignOut = {
                                        googleAuthManager.signOut()
                                        currentUser = null; partnerUid = null; groups = emptyList()
                                        pairingPrefs.clear()
                                        appState = AppState.SIGNED_OUT
                                    }
                                    if (pUid != null && pUid != DEV_PARTNER_UID) PairingManager(uid).unpair(pUid) { doSignOut() } else doSignOut()
                                },
                                onSchedule = { showSchedule = true },
                                onApplyWallpaper = { launchWallpaperPicker() },
                                onPermissions = { permissionsFromSettings = true; appState = AppState.PERMISSIONS },
                                onClearLockscreen = {
                                    pairingPrefs.clearLockscreenWallpaper()
                                    com.alancruse.wallpaper.wallpaper.LockscreenWallpaperService.stop(this@MainActivity)
                                    refreshWallpaperIds()
                                },
                                onBack = { goHome() }
                            )

                            AppState.CANVAS -> {}
                        }
                    }

                    if (showSchedule) {
                        AutoSwitchDialog(
                            availableRooms = rooms().map { Triple(it.roomId, it.roomType, it.displayName) },
                            onDismiss = { showSchedule = false }
                        )
                    }
                }
            }
        }
    }

    /** Developer account: pretend to be paired so the canvas can be used without a partner. */
    private fun enterDeveloperMode() {
        val uid = currentUser?.uid ?: return
        pairingPrefs.developerPaired = true
        partnerUid = DEV_PARTNER_UID
        pairingPrefs.savePairing(uid, DEV_PARTNER_UID)
        refreshWallpaperIds()
        pairingFromApp = false
        loadGroups(uid)
        appState = AppState.HOME
        Toast.makeText(this, "Connected (simulated)", Toast.LENGTH_SHORT).show()
    }

    private fun goHome() {
        currentUser?.let { loadGroups(it.uid) }
        appState = AppState.HOME
    }

    private fun refreshWallpaperIds() {
        homeRoomId = pairingPrefs.getWallpaperRoomId()
        lockRoomId = pairingPrefs.lockscreenRoomId
    }

    private fun rooms(): List<CanvasRoom> = buildList {
        val uid = currentUser?.uid
        val p = partnerUid
        if (uid != null && p != null) add(CanvasRoom(if (uid < p) "${uid}_$p" else "${p}_$uid", "couple", if (p == DEV_PARTNER_UID) "Dev canvas" else "Partner"))
        groups.forEach { g -> add(CanvasRoom(g.roomId, "group", g.name, g.memberCount, g.joinCode, g.groupId)) }
    }

    override fun onResume() {
        super.onResume()
        // Refresh groups when returning from canvas
        val user = currentUser
        thumbVersion++
        refreshWallpaperIds()
        if (user != null && appState == AppState.HOME) {
            loadGroups(user.uid)
        }
    }

    private fun checkPairingAndLoadGroups(uid: String) {
        PairingManager(uid).checkExistingPairing { serverPartner ->
            val existingPartner = serverPartner ?: if (isDeveloper && pairingPrefs.developerPaired) DEV_PARTNER_UID else null
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
            val next = if (existingPartner != null || pairingPrefs.getGroupRoomIds().isNotEmpty()) AppState.HOME else AppState.NEEDS_PAIRING
            if (!pairingPrefs.permissionsOnboarded && !AppPermissions.allGranted(this)) {
                afterPermissions = next
                appState = AppState.PERMISSIONS
            } else appState = next
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
            CanvasRoom.putAll(this, rooms())
        }
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
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

}

private const val DEVELOPER_EMAIL = "mdaraf26@gmail.com"
private const val DEV_PARTNER_UID = "dev-simulated-partner"
