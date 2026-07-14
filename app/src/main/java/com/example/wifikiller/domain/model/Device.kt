package netspoofer.full.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class Device(
    @PrimaryKey
    val macAddress: String,
    val ipAddress: String,
    val hostname: String?,
    val vendor: String?,
    val isBlocked: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val friendlyName: String? = null
)
