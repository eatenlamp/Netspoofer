// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package netspoofer.full.data.database

import androidx.room.*
import netspoofer.full.domain.model.Device
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY lastSeen DESC")
    fun getAllDevices(): Flow<List<Device>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: Device)

    @Update
    suspend fun updateDevice(device: Device)

    @Query("UPDATE devices SET isBlocked = :blocked WHERE macAddress = :mac")
    suspend fun setBlockedStatus(mac: String, blocked: Boolean)

    @Delete
    suspend fun deleteDevice(device: Device)
}
