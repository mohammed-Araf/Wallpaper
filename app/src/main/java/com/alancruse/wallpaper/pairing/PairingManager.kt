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
     * This fires when the partner claims our PIN and permanentlyLinkUsers is called.
     */
    private fun listenForPartner(onPartnerLinked: (String) -> Unit) {
        val userRef = db.child("users").child(currentUid).child("partner_uid")

        partnerListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val partnerUid = snapshot.getValue(String::class.java)
                if (partnerUid != null) {
                    Log.d("PairingManager", "Partner linked! UID: $partnerUid")
                    onPartnerLinked(partnerUid)
                    // Stop listening once paired
                    userRef.removeEventListener(this)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("PairingManager", "Partner listener cancelled: ${error.message}")
            }
        }

        userRef.addValueEventListener(partnerListener!!)
    }

    /**
     * Call this when the partner enters the PIN on their screen.
     * If valid and not expired, links both users permanently.
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

                // Link the two UIDs together permanently
                permanentlyLinkUsers(currentUid, partnerUid)

                // Cleanup the pin
                db.child("pairing_codes").child(pin).removeValue()
                onResult(true, partnerUid, "Successfully paired!")
            } else {
                onResult(false, null, "Invalid PIN. Please check and try again.")
            }
        }.addOnFailureListener {
            onResult(false, null, "Connection error: ${it.message}")
        }
    }

    private fun permanentlyLinkUsers(userA: String, userB: String) {
        // userA is linked to userB
        db.child("users").child(userA).child("partner_uid").setValue(userB)
        // userB is linked to userA
        db.child("users").child(userB).child("partner_uid").setValue(userA)
    }

    /**
     * Unpair the current user from their partner.
     */
    fun unpair(partnerUid: String, onComplete: () -> Unit) {
        db.child("users").child(currentUid).child("partner_uid").removeValue()
        db.child("users").child(partnerUid).child("partner_uid").removeValue()
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
