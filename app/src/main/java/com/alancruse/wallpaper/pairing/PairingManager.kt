package com.alancruse.wallpaper.pairing

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class PairingManager(private val currentUid: String) {

    private val db = FirebaseDatabase.getInstance().reference
    private var partnerListener: ValueEventListener? = null

    /**
     * Check if the current user already has a paired partner in Firebase.
     * Returns the partner UID via callback if found, null otherwise.
     */
    fun checkExistingPairing(onResult: (String?) -> Unit) {
        db.child("users").child(currentUid).child("partner_uid")
            .get()
            .addOnSuccessListener { snapshot ->
                val partnerUid = snapshot.getValue(String::class.java)
                onResult(partnerUid)
            }
            .addOnFailureListener {
                Log.e("PairingManager", "Failed to check pairing: ${it.message}")
                onResult(null)
            }
    }

    /**
     * Generate a 6-digit PIN and save it under /pairing_codes/{PIN}.
     * Starts listening for partner to claim this PIN.
     *
     * @param onCodeGenerated called immediately with the PIN string
     * @param onPartnerLinked called when a partner successfully claims this PIN, with partner UID
     */
    fun generatePairingCode(
        onCodeGenerated: (String) -> Unit,
        onPartnerLinked: (String) -> Unit
    ) {
        val pin = (100000..999999).random().toString()
        val timestamp = System.currentTimeMillis()

        val codeData = mapOf(
            "temp_uid" to currentUid,
            "created_at" to timestamp
        )

        db.child("pairing_codes").child(pin).setValue(codeData)
            .addOnSuccessListener {
                onCodeGenerated(pin)
                listenForPartner(onPartnerLinked)
            }
            .addOnFailureListener {
                Log.e("PairingManager", "Failed to generate pairing code: ${it.message}")
            }
    }

    /**
     * Listen on users/{currentUid}/partner_uid for a value to appear.
     * Uses both a realtime listener AND a polling fallback to ensure
     * the code generator always gets notified.
     */
    private fun listenForPartner(onPartnerLinked: (String) -> Unit) {
        val userRef = db.child("users").child(currentUid).child("partner_uid")
        var alreadyLinked = false

        partnerListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val partnerUid = snapshot.getValue(String::class.java)
                if (partnerUid != null && !alreadyLinked) {
                    alreadyLinked = true
                    Log.d("PairingManager", "Partner linked via listener! UID: $partnerUid")
                    onPartnerLinked(partnerUid)
                    userRef.removeEventListener(this)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("PairingManager", "Partner listener cancelled: ${error.message}")
            }
        }

        userRef.addValueEventListener(partnerListener!!)

        // Polling fallback: check every 3 seconds in case the listener missed the event
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val pollRunnable = object : Runnable {
            override fun run() {
                if (alreadyLinked) return
                userRef.get().addOnSuccessListener { snapshot ->
                    val partnerUid = snapshot.getValue(String::class.java)
                    if (partnerUid != null && !alreadyLinked) {
                        alreadyLinked = true
                        Log.d("PairingManager", "Partner linked via poll! UID: $partnerUid")
                        onPartnerLinked(partnerUid)
                        partnerListener?.let { userRef.removeEventListener(it) }
                    } else if (!alreadyLinked) {
                        handler.postDelayed(this, 3000)
                    }
                }
            }
        }
        handler.postDelayed(pollRunnable, 3000)
    }

    /**
     * Call this when the partner enters the PIN on their screen.
     * If valid and not expired, links both users permanently using
     * an atomic multi-path update.
     *
     * @param onResult (success, partnerUid?, errorMessage?)
     */
    fun claimPairingCode(pin: String, onResult: (Boolean, String?, String?) -> Unit) {
        db.child("pairing_codes").child(pin).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val partnerUid = snapshot.child("temp_uid").value as? String
                val createdAt = snapshot.child("created_at").value as? Long ?: 0L
                val now = System.currentTimeMillis()

                // Check if code is expired (5 minutes for more usability)
                if (now - createdAt > 300_000) {
                    onResult(false, null, "Code expired. Ask your partner to generate a new one.")
                    db.child("pairing_codes").child(pin).removeValue()
                    return@addOnSuccessListener
                }

                if (partnerUid == null) {
                    onResult(false, null, "Invalid pair data.")
                    return@addOnSuccessListener
                }

                if (partnerUid == currentUid) {
                    onResult(false, null, "You can't pair with yourself!")
                    return@addOnSuccessListener
                }

                // Link both users and wait for completion
                permanentlyLinkUsers(currentUid, partnerUid) { success ->
                    if (success) {
                        db.child("pairing_codes").child(pin).removeValue()
                        onResult(true, partnerUid, "Successfully paired!")
                    } else {
                        // Fallback: try individual writes if the first attempt failed
                        Log.w("PairingManager", "Link failed, retrying with individual writes")
                        db.child("users").child(currentUid).child("partner_uid").setValue(partnerUid)
                        db.child("users").child(partnerUid).child("partner_uid").setValue(currentUid)
                        db.child("pairing_codes").child(pin).removeValue()
                        onResult(true, partnerUid, "Successfully paired!")
                    }
                }
            } else {
                onResult(false, null, "Invalid PIN. Please check and try again.")
            }
        }.addOnFailureListener {
            onResult(false, null, "Connection error: ${it.message}")
        }
    }

    /**
     * Link two users using two setValue calls with completion tracking.
     * Writes partner's node first so their listener fires ASAP.
     */
    private fun permanentlyLinkUsers(userA: String, userB: String, onComplete: (Boolean) -> Unit) {
        // First try atomic update
        val updates = mapOf(
            "users/$userA/partner_uid" to userB,
            "users/$userB/partner_uid" to userA
        )
        db.updateChildren(updates)
            .addOnSuccessListener {
                Log.d("PairingManager", "Both users linked atomically: $userA <-> $userB")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.w("PairingManager", "Atomic update failed (${e.message}), using individual writes")
                // Fallback: two separate writes
                // Write partner's node first — their listener is waiting for this
                db.child("users").child(userB).child("partner_uid").setValue(userA)
                    .addOnCompleteListener { task1 ->
                        db.child("users").child(userA).child("partner_uid").setValue(userB)
                            .addOnCompleteListener { task2 ->
                                val ok = task1.isSuccessful && task2.isSuccessful
                                Log.d("PairingManager", "Individual writes: task1=${task1.isSuccessful}, task2=${task2.isSuccessful}")
                                onComplete(ok)
                            }
                    }
            }
    }

    /**
     * Unpair the current user from their partner.
     */
    fun unpair(partnerUid: String, onComplete: () -> Unit) {
        val updates = mapOf<String, Any?>(
            "users/$currentUid/partner_uid" to null,
            "users/$partnerUid/partner_uid" to null
        )
        db.updateChildren(updates)
            .addOnCompleteListener { onComplete() }
    }

    /**
     * Remove any active listeners to avoid leaks.
     */
    fun cleanup() {
        partnerListener?.let {
            db.child("users").child(currentUid).child("partner_uid")
                .removeEventListener(it)
        }
    }
}
