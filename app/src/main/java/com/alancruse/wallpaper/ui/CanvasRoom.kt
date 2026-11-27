package com.alancruse.wallpaper.ui

import android.content.Intent

data class CanvasRoom(
    val roomId: String,
    val roomType: String,      // "couple" or "group"
    val displayName: String,
    val memberCount: Int = 2,
    val joinCode: String? = null,
    val groupId: String? = null,
) {
    companion object {
        fun putAll(intent: Intent, rooms: List<CanvasRoom>) {
            intent.putExtra("rooms_id", rooms.map { it.roomId }.toTypedArray())
            intent.putExtra("rooms_type", rooms.map { it.roomType }.toTypedArray())
            intent.putExtra("rooms_name", rooms.map { it.displayName }.toTypedArray())
            intent.putExtra("rooms_group", rooms.map { it.groupId ?: "" }.toTypedArray())
        }

        fun readAll(intent: Intent): List<CanvasRoom> {
            val ids = intent.getStringArrayExtra("rooms_id") ?: return emptyList()
            val types = intent.getStringArrayExtra("rooms_type") ?: return emptyList()
            val names = intent.getStringArrayExtra("rooms_name") ?: return emptyList()
            val groups = intent.getStringArrayExtra("rooms_group") ?: return emptyList()
            return ids.indices.map { i -> CanvasRoom(ids[i], types[i], names[i], groupId = groups[i].ifEmpty { null }) }
        }
    }
}
