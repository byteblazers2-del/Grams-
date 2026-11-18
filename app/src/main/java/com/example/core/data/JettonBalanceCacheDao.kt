package com.example.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface JettonBalanceCacheDao {
  @Query("SELECT * FROM jetton_cache WHERE accountAddr = :accountAddr AND network = :network")
  fun observeHoldings(accountAddr: String, network: String): Flow<List<JettonBalanceCacheEntity>>

  @Query("SELECT * FROM jetton_cache WHERE accountAddr = :accountAddr AND network = :network")
  suspend fun getHoldings(accountAddr: String, network: String): List<JettonBalanceCacheEntity>

  @Query("DELETE FROM jetton_cache WHERE accountAddr = :accountAddr AND network = :network")
  suspend fun clearForAccount(accountAddr: String, network: String)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<JettonBalanceCacheEntity>)

  @Transaction
  suspend fun replaceForAccount(
    accountAddr: String,
    network: String,
    entities: List<JettonBalanceCacheEntity>,
  ) {
    clearForAccount(accountAddr, network)
    if (entities.isNotEmpty()) {
      upsertAll(entities)
    }
  }

  @Query("DELETE FROM jetton_cache")
  suspend fun clearAll()
}
