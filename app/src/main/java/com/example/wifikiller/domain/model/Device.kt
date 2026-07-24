// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

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
