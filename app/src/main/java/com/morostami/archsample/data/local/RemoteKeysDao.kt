/*
 * *
 *  * Created by Moslem Rostami on 6/16/20 1:48 PM
 *  * Copyright (c) 2020 . All rights reserved.
 *  * Last modified 6/16/20 1:48 PM
 *
 */

package com.morostami.archsample.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.morostami.archsample.domain.model.CoinsRemoteKeys

@Dao
interface RemoteKeysDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllRemoteKeys(remoteKey: List<CoinsRemoteKeys>)

    @Query("SELECT * FROM CoinsRemoteKeys WHERE coin_Id = :coinId")
    fun remoteKeysCoinId(coinId: String): CoinsRemoteKeys?

    @Query("DELETE FROM CoinsRemoteKeys")
    fun clearCoinsRemoteKeys()
}