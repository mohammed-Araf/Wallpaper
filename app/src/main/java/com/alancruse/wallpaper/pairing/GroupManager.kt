package com.alancruse.wallpaper.pairing

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Handles group creation, joining, leaving, and listing.
 *
 * Firebase structure:
 *   groups/{groupId}/
 *     name: "Study Squad"
 *     ownerId: "uid_abc"
 *     createdAt: 1234567890
 *     joinCode: "ABC123"
 *     members/{uid}: { name: "Araf", joinedAt: 1234567890 }
 *
 *   users/{uid}/groups/{groupId}: true
 *
 *   canvas_sessions/group_{groupId}/  ← canvas data (managed by CanvasSyncManager)
 */
class GroupManager(private val currentUid: String) {

    private val db = FirebaseDatabase.getInstance().reference

    data class GroupInfo(
        val groupId: String,
        val name: String,
        val ownerId: String,
        val joinCode: String,
        val memberCount: Int,
        val roomId: String  // "group_{groupId}"
    )

    /**
     * Create a new group. Returns the groupId on success.
     */
    fun createGroup(
        name: String,
        displayName: String,
        onResult: (success: Boolean, groupId: String?, joinCode: String?, error: String?) -> Unit
    ) {
        val groupRef = db.child("groups").push()
        val groupId = groupRef.key ?: run {
            onResult(false, null, null, "Failed to generate group ID")
            return
        }

        val joinCode = generateJoinCode()

        val memberData = mapOf(
            "name" to displayName,
            "joinedAt" to System.currentTimeMillis()
        )

        // Include members INSIDE groupData to avoid overlapping paths
        val groupData = mapOf(
            "name" to name.take(20),
            "ownerId" to currentUid,
            "createdAt" to System.currentTimeMillis(),
            "joinCode" to joinCode,
            "members" to mapOf(currentUid to memberData)
        )

        // Non-overlapping paths: groups/$groupId and users/$uid/groups/$groupId
        val updates = mapOf<String, Any>(
            "groups/$groupId" to groupData,
            "users/$currentUid/groups/$groupId" to true
        )

        db.updateChildren(updates)
            .addOnSuccessListener {
                Log.d("GroupManager", "Group created: $groupId (code: $joinCode)")
                onResult(true, groupId, joinCode, null)
            }
            .addOnFailureListener { e ->
                Log.e("GroupManager", "Failed to create group: ${e.message}")
                onResult(false, null, null, e.message)
            }
    }

    /**
     * Join a group using a 6-character join code.
     */
    fun joinGroup(
        joinCode: String,
        displayName: String,
        onResult: (success: Boolean, groupId: String?, groupName: String?, error: String?) -> Unit
    ) {
        // Search for the group with this join code
        db.child("groups").orderByChild("joinCode").equalTo(joinCode.uppercase())
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists() || !snapshot.hasChildren()) {
                        onResult(false, null, null, "Invalid code. No group found.")
                        return
                    }

                    val groupSnapshot = snapshot.children.first()
                    val groupId = groupSnapshot.key ?: run {
                        onResult(false, null, null, "Invalid group data.")
                        return
                    }

                    val groupName = groupSnapshot.child("name").getValue(String::class.java) ?: "Group"

                    // Check if already a member
                    if (groupSnapshot.child("members").hasChild(currentUid)) {
                        onResult(false, null, null, "You're already in this group!")
                        return
                    }

                    // Check member limit (20)
                    val memberCount = groupSnapshot.child("members").childrenCount
                    if (memberCount >= 20) {
                        onResult(false, null, null, "Group is full (max 20 members).")
                        return
                    }

                    val memberData = mapOf(
                        "name" to displayName,
                        "joinedAt" to System.currentTimeMillis()
                    )

                    val updates = mapOf(
                        "groups/$groupId/members/$currentUid" to memberData,
                        "users/$currentUid/groups/$groupId" to true
                    )

                    db.updateChildren(updates)
                        .addOnSuccessListener {
                            Log.d("GroupManager", "Joined group: $groupId")
                            onResult(true, groupId, groupName, null)
                        }
                        .addOnFailureListener { e ->
                            onResult(false, null, null, e.message)
                        }
                }

                override fun onCancelled(error: DatabaseError) {
                    onResult(false, null, null, error.message)
                }
            })
    }

    /**
     * Leave a group. If the user is the last member, the group is deleted.
     */
    fun leaveGroup(groupId: String, onComplete: () -> Unit) {
        // Remove user from group members and user's group list
        val updates = mutableMapOf<String, Any?>(
            "groups/$groupId/members/$currentUid" to null,
            "users/$currentUid/groups/$groupId" to null
        )

        db.updateChildren(updates).addOnSuccessListener {
            // Check if group is now empty → delete it
            db.child("groups").child(groupId).child("members")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                            // Delete group and its canvas
                            db.child("groups").child(groupId).removeValue()
                            db.child("canvas_sessions").child("group_$groupId").removeValue()
                            Log.d("GroupManager", "Deleted empty group: $groupId")
                        }
                        onComplete()
                    }
                    override fun onCancelled(error: DatabaseError) { onComplete() }
                })
        }.addOnFailureListener { onComplete() }
    }

    /**
     * Fetch all groups the current user belongs to.
     */
    fun getMyGroups(onResult: (List<GroupInfo>) -> Unit) {
        db.child("users").child(currentUid).child("groups")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val groupIds = snapshot.children.mapNotNull { it.key }
                    if (groupIds.isEmpty()) {
                        onResult(emptyList())
                        return
                    }

                    val results = mutableListOf<GroupInfo>()
                    var remaining = groupIds.size

                    groupIds.forEach { gid ->
                        db.child("groups").child(gid)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(groupSnap: DataSnapshot) {
                                    if (groupSnap.exists()) {
                                        results.add(GroupInfo(
                                            groupId = gid,
                                            name = groupSnap.child("name").getValue(String::class.java) ?: "Group",
                                            ownerId = groupSnap.child("ownerId").getValue(String::class.java) ?: "",
                                            joinCode = groupSnap.child("joinCode").getValue(String::class.java) ?: "",
                                            memberCount = groupSnap.child("members").childrenCount.toInt(),
                                            roomId = "group_$gid"
                                        ))
                                    }
                                    remaining--
                                    if (remaining == 0) onResult(results.sortedBy { it.name })
                                }
                                override fun onCancelled(error: DatabaseError) {
                                    remaining--
                                    if (remaining == 0) onResult(results.sortedBy { it.name })
                                }
                            })
                    }
                }
                override fun onCancelled(error: DatabaseError) { onResult(emptyList()) }
            })
    }

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // No O/0/1/I for readability
        return (1..6).map { chars.random() }.joinToString("")
    }
}
