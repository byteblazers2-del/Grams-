package com.example.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    BalanceCacheEntity::class,
    JettonBalanceCacheEntity::class,
    TxEntity::class,
    AddressBookEntity::class,
  ],
  version = 5,
  exportSchema = false,
)
abstract class GramVaultDatabase : RoomDatabase() {
  abstract fun balanceCacheDao(): BalanceCacheDao

  abstract fun jettonBalanceCacheDao(): JettonBalanceCacheDao

  abstract fun txDao(): TxDao

  abstract fun addressBookDao(): AddressBookDao

  companion object {
    val MIGRATION_3_4 =
      object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE balance_cache ADD COLUMN extraAddress TEXT")
          db.execSQL("ALTER TABLE balance_cache ADD COLUMN lastTxLt TEXT")
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `address_book` (
              `id` TEXT NOT NULL,
              `network` TEXT NOT NULL,
              `label` TEXT NOT NULL,
              `rawAddress` TEXT NOT NULL,
              `friendlyAddress` TEXT NOT NULL,
              `createdAtEpochMs` INTEGER NOT NULL,
              PRIMARY KEY(`id`)
            )
            """.trimIndent()
          )
          db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_address_book_network_rawAddress` ON `address_book` (`network`, `rawAddress`)"
          )
        }
      }

    val MIGRATION_4_5 =
      object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `jetton_cache` (
              `accountAddr` TEXT NOT NULL,
              `network` TEXT NOT NULL,
              `masterRaw` TEXT NOT NULL,
              `jettonWalletRaw` TEXT,
              `symbol` TEXT NOT NULL,
              `name` TEXT NOT NULL,
              `decimals` INTEGER NOT NULL,
              `amountUnits` TEXT NOT NULL,
              `imageUrl` TEXT,
              `verification` TEXT NOT NULL,
              `providerName` TEXT NOT NULL,
              `fetchedAt` INTEGER NOT NULL,
              PRIMARY KEY(`accountAddr`, `network`, `masterRaw`)
            )
            """.trimIndent()
          )
          db.execSQL("ALTER TABLE tx ADD COLUMN jettonSymbol TEXT")
          db.execSQL("ALTER TABLE tx ADD COLUMN jettonName TEXT")
          db.execSQL("ALTER TABLE tx ADD COLUMN jettonDecimals INTEGER")
          db.execSQL("ALTER TABLE tx ADD COLUMN jettonImageUrl TEXT")
          db.execSQL("ALTER TABLE tx ADD COLUMN jettonVerification TEXT")
        }
      }

    @Volatile private var instance: GramVaultDatabase? = null

    fun getInstance(context: Context): GramVaultDatabase {
      return instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                GramVaultDatabase::class.java,
                "gramvault_cache.db",
              )
              .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
              .fallbackToDestructiveMigration(dropAllTables = true)
              .build()
              .also { instance = it }
        }
    }
  }
}
