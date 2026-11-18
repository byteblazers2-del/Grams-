package com.example.core.data

import androidx.room.Entity

@Entity(
  tableName = "jetton_cache",
  primaryKeys = ["accountAddr", "network", "masterRaw"],
)
data class JettonBalanceCacheEntity(
  val accountAddr: String,
  val network: String,
  val masterRaw: String,
  val jettonWalletRaw: String?,
  val symbol: String,
  val name: String,
  val decimals: Int,
  val amountUnits: String,
  val imageUrl: String?,
  val verification: String,
  val providerName: String,
  val fetchedAt: Long,
)
