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

    data class MemberInfo(
        val uid: String,
        val name: String,
        val joinedAt: Long,
        val canEdit: Boolean = true,
        val isBlocked: Boolean = false
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

                    // Check if blocked
                    if (groupSnapshot.child("blocked").hasChild(currentUid)) {
                        onResult(false, null, null, "You have been blocked from this group.")
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
     * Leave a group.
     * - If the user is the last member, the group and its canvas are deleted.
     * - If the user is the owner and other members remain, ownership
     *   transfers to the member who joined earliest.
     */
    fun leaveGroup(groupId: String, onComplete: () -> Unit) {
        // Remove user from group members and user's group list
        val updates = mutableMapOf<String, Any?>(
            "groups/$groupId/members/$currentUid" to null,
            "users/$currentUid/groups/$groupId" to null
        )

        db.updateChildren(updates).addOnSuccessListener {
            // Check remaining members
            db.child("groups").child(groupId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val membersSnap = snapshot.child("members")

                        if (!membersSnap.exists() || membersSnap.childrenCount == 0L) {
                            // No members left → delete group and canvas
                            db.child("groups").child(groupId).removeValue()
                            db.child("canvas_sessions").child("group_$groupId").removeValue()
                            Log.d("GroupManager", "Deleted empty group: $groupId")
                        } else {
                            // If the leaving user was the owner, transfer ownership
                            val ownerId = snapshot.child("ownerId").getValue(String::class.java)
                            if (ownerId == currentUid) {
                                // Pick the member with the earliest joinedAt
                                val nextOwner = membersSnap.children
                                    .minByOrNull {
                                        it.child("joinedAt").getValue(Long::class.java) ?: Long.MAX_VALUE
                                    }?.key
                                if (nextOwner != null) {
                                    db.child("groups").child(groupId).child("ownerId").setValue(nextOwner)
                                    Log.d("GroupManager", "Transferred ownership of $groupId to $nextOwner")
                                }
                            }
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

    /**
     * Get detailed group info including member list.
     */
    fun getGroupDetails(
        groupId: String,
        onResult: (GroupInfo?, List<MemberInfo>) -> Unit
    ) {
        db.child("groups").child(groupId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        onResult(null, emptyList())
                        return
                    }

                    val info = GroupInfo(
                        groupId = groupId,
                        name = snapshot.child("name").getValue(String::class.java) ?: "Group",
                        ownerId = snapshot.child("ownerId").getValue(String::class.java) ?: "",
                        joinCode = snapshot.child("joinCode").getValue(String::class.java) ?: "",
                        memberCount = snapshot.child("members").childrenCount.toInt(),
                        roomId = "group_$groupId"
                    )

                    val members = snapshot.child("members").children.mapNotNull { memberSnap ->
                        val uid = memberSnap.key ?: return@mapNotNull null
                        MemberInfo(
                            uid = uid,
                            name = memberSnap.child("name").getValue(String::class.java) ?: "User",
                            joinedAt = memberSnap.child("joinedAt").getValue(Long::class.java) ?: 0L,
                            canEdit = memberSnap.child("canEdit").getValue(Boolean::class.java) != false,
                            isBlocked = memberSnap.child("blocked").getValue(Boolean::class.java) == true
                        )
                    }.sortedBy { it.joinedAt }

                    onResult(info, members)
                }
                override fun onCancelled(error: DatabaseError) { onResult(null, emptyList()) }
            })
    }

    /**
     * Remove a member from the group. Owner-only action.
     */
    fun removeMember(groupId: String, memberUid: String, onComplete: () -> Unit) {
        val updates = mapOf<String, Any?>(
            "groups/$groupId/members/$memberUid" to null,
            "users/$memberUid/groups/$groupId" to null
        )
        db.updateChildren(updates)
            .addOnSuccessListener {
                Log.d("GroupManager", "Removed member $memberUid from group $groupId")
                onComplete()
            }
            .addOnFailureListener { onComplete() }
    }

    /**
     * Block a member — removes them and adds to blocked list so they can't rejoin.
     */
    fun blockMember(groupId: String, memberUid: String, onComplete: () -> Unit) {
        val updates = mapOf<String, Any?>(
            "groups/$groupId/members/$memberUid" to null,
            "groups/$groupId/blocked/$memberUid" to true,
            "users/$memberUid/groups/$groupId" to null
        )
        db.updateChildren(updates)
            .addOnSuccessListener {
                Log.d("GroupManager", "Blocked member $memberUid from group $groupId")
                onComplete()
            }
            .addOnFailureListener { onComplete() }
    }

    /**
     * Toggle a member's editing permission.
     */
    fun setEditPermission(groupId: String, memberUid: String, canEdit: Boolean, onComplete: () -> Unit) {
        db.child("groups").child(groupId)
            .child("members").child(memberUid)
            .child("canEdit").setValue(canEdit)
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener { onComplete() }
    }

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // No O/0/1/I for readability
        return (1..6).map { chars.random() }.joinToString("")
    }
}
