// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package netspoofer.full.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import netspoofer.full.domain.model.Device

@Database(entities = [Device::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
}
