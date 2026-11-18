package com.example.core.wallet

import android.content.Context
import com.example.core.model.MnemonicScheme
import com.example.core.model.Network
import com.example.core.model.VaultMetadata
import com.example.core.model.VaultRegistry
import com.example.core.model.VaultSecurityMode
import com.example.core.model.WalletVersion
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class VaultMetadataStore(context: Context) {
  private val vaultDir = File(context.filesDir, "vault")
  private val metaFile = File(vaultDir, "vault_meta.json")
  private val registryFile = File(vaultDir, "vault_registry.json")

  fun loadRegistry(): VaultRegistry {
    if (registryFile.exists()) {
      val parsed =
        runCatching {
          val json = JSONObject(registryFile.readText())
          val activeId = json.optString("activeWalletId", "").takeIf { it.isNotBlank() }
          val array = json.optJSONArray("wallets") ?: JSONArray()
          val wallets = buildList {
            for (i in 0 until array.length()) {
              parseMetadata(array.getJSONObject(i))?.let { add(it) }
            }
          }
          val resolvedActive =
            wallets.firstOrNull { it.walletId == activeId }?.walletId
              ?: wallets.firstOrNull()?.walletId
          VaultRegistry(wallets = wallets, activeWalletId = resolvedActive)
        }.getOrNull()
      if (parsed != null) return parsed
    }

    val legacy = loadLegacySingle()
    return if (legacy != null) {
      VaultRegistry(wallets = listOf(legacy), activeWalletId = legacy.walletId)
    } else {
      VaultRegistry(wallets = emptyList(), activeWalletId = null)
    }
  }

  fun loadAll(): List<VaultMetadata> = loadRegistry().wallets

  fun load(): VaultMetadata? {
    val registry = loadRegistry()
    val activeId = registry.activeWalletId
    return registry.wallets.firstOrNull { it.walletId == activeId } ?: registry.wallets.firstOrNull()
  }

  fun save(metadata: VaultMetadata) {
    val current = loadRegistry()
    val updatedWallets =
      current.wallets.toMutableList().apply {
        val idx = indexOfFirst { it.walletId == metadata.walletId }
        if (idx >= 0) {
          this[idx] = metadata
        } else {
          add(metadata)
        }
      }
    persistRegistry(VaultRegistry(wallets = updatedWallets, activeWalletId = metadata.walletId))
  }

  fun updateMetadataPreservingActive(metadata: VaultMetadata) {
    val current = loadRegistry()
    val updatedWallets =
      current.wallets.map { if (it.walletId == metadata.walletId) metadata else it }
    persistRegistry(current.copy(wallets = updatedWallets))
  }

  fun setActiveWallet(walletId: String): VaultMetadata? {
    val current = loadRegistry()
    val target = current.wallets.firstOrNull { it.walletId == walletId } ?: return null
    persistRegistry(current.copy(activeWalletId = target.walletId))
    return target
  }

  fun removeWallet(walletId: String): VaultRegistry {
    val current = loadRegistry()
    val remaining = current.wallets.filterNot { it.walletId == walletId }
    if (remaining.isEmpty()) {
      clear()
      return VaultRegistry(emptyList(), null)
    }
    val nextActive =
      if (current.activeWalletId == walletId) {
        remaining.first().walletId
      } else {
        current.activeWalletId?.takeIf { id -> remaining.any { it.walletId == id } }
          ?: remaining.first().walletId
      }
    val updated = VaultRegistry(wallets = remaining, activeWalletId = nextActive)
    persistRegistry(updated)
    return updated
  }

  fun clear() {
    registryFile.delete()
    metaFile.delete()
  }

  private fun loadLegacySingle(): VaultMetadata? {
    if (!metaFile.exists()) return null
    return runCatching { parseMetadata(JSONObject(metaFile.readText())) }.getOrNull()
  }

  private fun persistRegistry(registry: VaultRegistry) {
    if (!vaultDir.exists()) vaultDir.mkdirs()
    val array = JSONArray()
    registry.wallets.forEach { array.put(toJson(it)) }
    val json =
      JSONObject()
        .put("activeWalletId", registry.activeWalletId ?: "")
        .put("wallets", array)
    writeFileAtomic(registryFile, json.toString())

    val activeMeta =
      registry.wallets.firstOrNull { it.walletId == registry.activeWalletId }
        ?: registry.wallets.firstOrNull()
    if (activeMeta != null) {
      writeFileAtomic(metaFile, toJson(activeMeta).toString())
    } else {
      metaFile.delete()
    }
  }

  private fun writeFileAtomic(target: File, content: String) {
    val tempFile = File(vaultDir, "${target.name}.tmp")
    tempFile.writeText(content)
    if (!tempFile.renameTo(target)) {
      target.writeText(content)
      tempFile.delete()
    }
  }

  private fun toJson(metadata: VaultMetadata): JSONObject =
    JSONObject()
      .put("walletId", metadata.walletId)
      .put("walletName", metadata.walletName)
      .put("isWatchOnly", metadata.isWatchOnly)
      .put("publicKeyHex", metadata.publicKeyHex)
      .put("mainnetRaw", metadata.mainnetRaw)
      .put("testnetRaw", metadata.testnetRaw)
      .put("isBackedUp", metadata.isBackedUp)
      .put("createdAtEpochMs", metadata.createdAtEpochMs)
      .put("securityMode", metadata.securityMode.name)
      .put("walletVersion", metadata.walletVersion.name)
      .put("mnemonicScheme", metadata.mnemonicScheme.name)
      .put("lastActiveNetwork", metadata.lastActiveNetwork.name)

  private fun parseMetadata(json: JSONObject): VaultMetadata? =
    runCatching {
      val modeName = json.optString("securityMode", VaultSecurityMode.DEVICE_KEYSTORE.name)
      val mode =
        runCatching { VaultSecurityMode.valueOf(modeName) }
          .getOrDefault(VaultSecurityMode.DEVICE_KEYSTORE)
      val versionName = json.optString("walletVersion", WalletVersion.V5R1.name)
      val version =
        runCatching { WalletVersion.valueOf(versionName) }
          .getOrDefault(WalletVersion.V5R1)
      val schemeName = json.optString("mnemonicScheme", MnemonicScheme.MULTICHAIN.name)
      val scheme =
        runCatching { MnemonicScheme.valueOf(schemeName) }
          .getOrDefault(MnemonicScheme.MULTICHAIN)
      val walletId =
        json.optString("walletId", VaultMetadata.LEGACY_WALLET_ID)
          .ifBlank { VaultMetadata.LEGACY_WALLET_ID }
      val isWatchOnly = json.optBoolean("isWatchOnly", false)
      val defaultName =
        if (isWatchOnly) "Watch Wallet" else VaultMetadata.DEFAULT_WALLET_NAME
      val walletName =
        json.optString("walletName", defaultName)
          .ifBlank { defaultName }
      VaultMetadata(
        publicKeyHex = json.optString("publicKeyHex", ""),
        mainnetRaw = json.getString("mainnetRaw"),
        testnetRaw = json.optString("testnetRaw", json.getString("mainnetRaw")),
        isBackedUp = json.optBoolean("isBackedUp", isWatchOnly),
        createdAtEpochMs = json.optLong("createdAtEpochMs", 0L),
        securityMode = mode,
        walletVersion = version,
        mnemonicScheme = scheme,
        lastActiveNetwork = Network.MAINNET,
        walletId = walletId,
        walletName = walletName,
        isWatchOnly = isWatchOnly,
      )
    }.getOrNull()
}
