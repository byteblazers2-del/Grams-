package com.example.core.network

import com.example.core.model.GRAM_DECIMALS
import com.example.core.model.JettonAllowList
import com.example.core.model.JettonHolding
import com.example.core.model.JettonMetadataSanitizer
import com.example.core.model.JettonUrlSanitizer
import com.example.core.model.JettonVerificationStatus
import com.example.core.model.MAX_JETTON_DECIMALS
import com.example.core.model.Money
import com.example.core.model.Network
import com.example.core.model.SlippageTolerance
import com.example.core.model.SwapAsset
import com.example.core.model.SwapInternalMessage
import com.example.core.model.SwapQuote
import com.example.core.model.TonAddress
import com.example.core.model.USDT_DECIMALS
import com.example.core.wallet.TransferBuilder
import com.gramvault.wallet.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.ton.boc.BagOfCells
import org.ton.cell.Cell

interface SwapEngine {
  suspend fun fetchAvailableTokens(userHoldings: List<JettonHolding>): List<SwapAsset>

  suspend fun fetchQuote(
    inputAsset: SwapAsset,
    outputAsset: SwapAsset,
    inputAmount: Money,
    slippage: SlippageTolerance,
  ): Result<SwapQuote>

  suspend fun buildSwapMessages(
    senderAddress: TonAddress,
    quote: SwapQuote,
    slippage: SlippageTolerance,
  ): Result<List<SwapInternalMessage>>
}

class SwapCoffeeService(
  private val okHttpClient: OkHttpClient,
  private val swapCoffeeBaseUrl: String = DEFAULT_SWAP_COFFEE_BASE_URL,
  private val stonFiAssetsUrl: String = DEFAULT_STONFI_ASSETS_URL,
  private val apiKey: String = BuildConfig.SWAP_COFFEE_API_KEY,
  private val referralName: String = BuildConfig.SWAP_COFFEE_REFERRAL_NAME,
  private val clockMs: () -> Long = { System.currentTimeMillis() },
) : SwapEngine {

  private val moshi = Moshi.Builder().build()
  private val mapAdapter =
    moshi.adapter<Map<String, Any?>>(
      Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )
  private val listAdapter =
    moshi.adapter<List<Any?>>(Types.newParameterizedType(List::class.java, Any::class.java))

  @Volatile private var cachedRemoteCatalog: List<SwapAsset>? = null
  @Volatile private var cachedRemoteCatalogEpochMs: Long = 0L

  override suspend fun fetchAvailableTokens(userHoldings: List<JettonHolding>): List<SwapAsset> {
    val now = clockMs()
    val remote =
      if (cachedRemoteCatalog != null && (now - cachedRemoteCatalogEpochMs) in 0L until CATALOG_TTL_MS) {
        cachedRemoteCatalog!!
      } else {
        withContext(Dispatchers.IO) { fetchRemoteCatalog() }.also { fetched ->
          if (fetched.isNotEmpty()) {
            cachedRemoteCatalog = fetched
            cachedRemoteCatalogEpochMs = clockMs()
          }
        }
      }

    val merged = linkedMapOf<String, SwapAsset>()
    curatedDefaultAssets().forEach { asset -> merged[asset.id.lowercase()] = asset }

    userHoldings
      .filter { it.verification != JettonVerificationStatus.BLACKLISTED }
      .forEach { holding ->
        val allowSpec = JettonAllowList.findVerified(holding.masterAddress, holding.balance.decimals)
        val friendlyBounce = holding.masterAddress.toFriendly(bounceable = true)
        val asset =
          SwapAsset(
            addressOrNative = allowSpec?.masterFriendly ?: friendlyBounce,
            rawAddress = holding.masterRaw,
            symbol = allowSpec?.symbol ?: holding.symbol,
            name = allowSpec?.name ?: holding.name,
            decimals = allowSpec?.decimals ?: holding.balance.decimals,
            imageUrl = holding.imageUrl,
            verification = if (allowSpec != null) JettonVerificationStatus.ALLOWLISTED else holding.verification,
          )
        merged[asset.id.lowercase()] = asset
      }

    remote.forEach { remoteAsset ->
      val key = remoteAsset.id.lowercase()
      val existing = merged[key]
      if (existing == null) {
        merged[key] = remoteAsset
      } else {
        merged[key] =
          existing.copy(
            imageUrl = existing.imageUrl ?: remoteAsset.imageUrl,
            priceUsd = remoteAsset.priceUsd ?: existing.priceUsd,
          )
      }
    }

    return merged.values.toList()
  }

  override suspend fun fetchQuote(
    inputAsset: SwapAsset,
    outputAsset: SwapAsset,
    inputAmount: Money,
    slippage: SlippageTolerance,
  ): Result<SwapQuote> = runCatching {
    require(!inputAsset.id.equals(outputAsset.id, ignoreCase = true)) {
      "Select two different tokens to swap"
    }
    require(inputAmount.decimals == inputAsset.decimals) {
      "Input amount decimals mismatch for ${inputAsset.symbol}"
    }
    require(inputAmount.units > BigInteger.ZERO) { "Enter a positive amount to swap" }

    val humanInput =
      BigDecimal(inputAmount.units)
        .movePointLeft(inputAsset.decimals)
        .stripTrailingZeros()
    val payloadMap = linkedMapOf<String, Any>(
      "input_token" to mapOf("blockchain" to "ton", "address" to inputAsset.addressOrNative),
      "output_token" to mapOf("blockchain" to "ton", "address" to outputAsset.addressOrNative),
      "input_amount" to humanInput.toDouble(),
      "max_splits" to MAX_SPLITS_FOR_TON_WALLET,
      "max_length" to 3,
    )
    val requestJson = mapAdapter.toJson(payloadMap)

    val responseBody =
      withContext(Dispatchers.IO) {
        val reqBuilder =
          Request.Builder()
            .url("${swapCoffeeBaseUrl.trimEnd('/')}/v1/route")
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
        usableSecret(apiKey)?.let { key ->
          reqBuilder.header("X-Api-Key", key)
        }
        okHttpClient.newCall(reqBuilder.build()).execute().use { res ->
          val bodyStr = res.body?.string().orEmpty()
          if (!res.isSuccessful) {
            throw buildApiException("swap.coffee route", res.code, bodyStr)
          }
          bodyStr
        }
      }

    parseRouteResponse(
      jsonBody = responseBody,
      inputAsset = inputAsset,
      outputAsset = outputAsset,
      inputAmount = inputAmount,
      slippage = slippage,
    )
  }

  override suspend fun buildSwapMessages(
    senderAddress: TonAddress,
    quote: SwapQuote,
    slippage: SlippageTolerance,
  ): Result<List<SwapInternalMessage>> = runCatching {
    require(senderAddress.network == Network.MAINNET) {
      "Swaps are only supported on TON Mainnet"
    }
    val pathsList =
      listAdapter.fromJson(quote.rawPathsJson)?.takeIf { it.isNotEmpty() }
        ?: throw InvalidPayloadException("swap.coffee", "Missing route paths for transaction build")

    val payloadMap = linkedMapOf<String, Any>(
      "sender_address" to senderAddress.toFriendly(bounceable = true),
      "slippage" to slippage.fraction,
      "paths" to pathsList,
    )
    usableSecret(referralName)?.let { cleanReferral ->
      payloadMap["referral_name"] = cleanReferral
    }

    val requestJson = mapAdapter.toJson(payloadMap)
    val responseBody =
      withContext(Dispatchers.IO) {
        val reqBuilder =
          Request.Builder()
            .url("${swapCoffeeBaseUrl.trimEnd('/')}/v2/route/transactions")
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
        usableSecret(apiKey)?.let { key ->
          reqBuilder.header("X-Api-Key", key)
        }
        okHttpClient.newCall(reqBuilder.build()).execute().use { res ->
          val bodyStr = res.body?.string().orEmpty()
          if (!res.isSuccessful) {
            throw buildApiException("swap.coffee tx", res.code, bodyStr)
          }
          bodyStr
        }
      }

    parseTransactionsResponse(
      jsonBody = responseBody,
      quote = quote,
    )
  }

  internal fun parseRouteResponse(
    jsonBody: String,
    inputAsset: SwapAsset,
    outputAsset: SwapAsset,
    inputAmount: Money,
    slippage: SlippageTolerance,
  ): SwapQuote {
    val root =
      mapAdapter.fromJson(jsonBody)
        ?: throw InvalidPayloadException("swap.coffee", "Empty route response")

    val paths =
      (root["paths"] as? List<*>)?.takeIf { it.isNotEmpty() }
        ?: throw IllegalStateException("No liquidity route available for this pair and amount.")

    val rawOutStr =
      root["output_amount"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw InvalidPayloadException("swap.coffee", "Missing output_amount in route")

    val outBigDecimal =
      runCatching { BigDecimal(rawOutStr) }.getOrNull()
        ?: throw InvalidPayloadException("swap.coffee", "Invalid output_amount: $rawOutStr")
    require(outBigDecimal > BigDecimal.ZERO) {
      "No liquidity route available for this pair and amount."
    }

    val expectedOutUnits =
      outBigDecimal
        .movePointRight(outputAsset.decimals)
        .setScale(0, RoundingMode.DOWN)
        .toBigInteger()
    require(expectedOutUnits > BigInteger.ZERO) {
      "Output amount is too small for this route."
    }
    val expectedOut = Money(expectedOutUnits, outputAsset.decimals)

    val minUnits =
      expectedOutUnits
        .multiply(BigInteger.valueOf((10_000 - slippage.bps).toLong()))
        .divide(BigInteger.valueOf(10_000L))
        .coerceAtLeast(BigInteger.ZERO)
    val minReceived = Money(minUnits, outputAsset.decimals)

    val inputUsd = parseOptionalDouble(root["input_usd"])
    val outputUsd = parseOptionalDouble(root["output_usd"])
    val priceImpactRaw = parseOptionalDouble(root["price_impact"])
    // swap.coffee reports negative price_impact fraction or percentage; normalize magnitude to %
    val priceImpactPercent =
      priceImpactRaw?.let { rawVal ->
        val absVal = kotlin.math.abs(rawVal)
        if (absVal <= 1.0 && absVal != 0.0) absVal * 100.0 else absVal
      }

    val recommendedGas = parseRecommendedGas(root["recommended_gas"])
    val dexLabels = extractDexLabels(paths)
    val rawPathsJson = listAdapter.toJson(paths)

    return SwapQuote(
      inputAsset = inputAsset,
      outputAsset = outputAsset,
      inputAmount = inputAmount,
      expectedOutputAmount = expectedOut,
      minReceivedAmount = minReceived,
      slippage = slippage,
      inputUsd = inputUsd,
      outputUsd = outputUsd,
      priceImpactPercent = priceImpactPercent,
      recommendedGas = recommendedGas,
      routeDexLabels = dexLabels,
      rawPathsJson = rawPathsJson,
      fetchedAtEpochMs = clockMs(),
    )
  }

  internal fun parseTransactionsResponse(
    jsonBody: String,
    quote: SwapQuote,
  ): List<SwapInternalMessage> {
    val root =
      mapAdapter.fromJson(jsonBody)
        ?: throw InvalidPayloadException("swap.coffee", "Empty transactions response")
    val rawTxs =
      root["transactions"] as? List<*>
        ?: throw InvalidPayloadException("swap.coffee", "Missing transactions array")

    require(rawTxs.isNotEmpty()) { "DEX returned no swap transactions" }
    require(rawTxs.size <= MAX_SPLITS_FOR_TON_WALLET) {
      "DEX returned ${rawTxs.size} split messages (wallet supports max $MAX_SPLITS_FOR_TON_WALLET)"
    }

    val messages =
      rawTxs.map { item ->
        val txMap =
          item as? Map<*, *>
            ?: throw InvalidPayloadException("swap.coffee", "Malformed transaction object")
        val addrStr =
          txMap["address"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw InvalidPayloadException("swap.coffee", "Missing swap destination address")
        val destAddress =
          TonAddress.parse(addrStr, Network.MAINNET).getOrElse { err ->
            throw InvalidPayloadException("swap.coffee", "Invalid swap destination address: ${err.message}")
          }

        val valueNano = parseNanoValue(txMap["value"])
        val cellB64 = txMap["cell"]?.toString()?.trim().orEmpty()
        val payloadCell =
          if (cellB64.isEmpty()) {
            Cell.empty()
          } else {
            decodeAndValidateBocCell(cellB64)
          }

        val sendMode =
          (txMap["send_mode"] as? Number)?.toInt()
            ?: TransferBuilder.DEFAULT_SEND_MODE

        SwapInternalMessage(
          destination = destAddress,
          attachedTonNano = valueNano,
          bounce = true,
          payloadCell = payloadCell,
          sendMode = sendMode,
        )
      }

    // Sanity guard on attached native TON to prevent malicious over-attachment
    val totalAttachedNano = messages.fold(BigInteger.ZERO) { acc, m -> acc + m.attachedTonNano }
    val maxGasCeiling = BigInteger.valueOf(MAX_SWAP_GAS_GUARD_NANO)
    if (quote.inputAsset.isNative) {
      if (totalAttachedNano < quote.inputAmount.units) {
        throw InvalidPayloadException(
          "swap.coffee",
          "Swap transaction attached TON is less than input amount",
        )
      }
      if (totalAttachedNano > quote.inputAmount.units + maxGasCeiling) {
        throw InvalidPayloadException(
          "swap.coffee",
          "Swap transaction attached unexpected excessive TON gas",
        )
      }
    } else {
      if (totalAttachedNano > maxGasCeiling) {
        throw InvalidPayloadException(
          "swap.coffee",
          "Jetton swap attached unexpected excessive TON gas",
        )
      }
    }

    return messages
  }

  internal fun parseStonFiAssetsResponse(jsonBody: String): List<SwapAsset> {
    val root = mapAdapter.fromJson(jsonBody) ?: return emptyList()
    val rawList = root["asset_list"] as? List<*> ?: return emptyList()
    val result = mutableListOf<SwapAsset>()
    for (entry in rawList) {
      val map = entry as? Map<*, *> ?: continue
      val blacklisted = map["blacklisted"] as? Boolean ?: false
      val deprecated = map["deprecated"] as? Boolean ?: false
      if (blacklisted || deprecated) continue

      val kind = map["kind"]?.toString()?.trim()?.lowercase().orEmpty()
      val contract = map["contract_address"]?.toString()?.trim().orEmpty()
      val symbolRaw = map["symbol"]?.toString()
      val displayNameRaw = map["display_name"]?.toString() ?: symbolRaw
      val decimals = (map["decimals"] as? Number)?.toInt() ?: continue
      if (decimals !in 0..MAX_JETTON_DECIMALS) continue

      if (kind == "ton") {
        val tonUsd = parseOptionalBigDecimal(map["dex_price_usd"] ?: map["third_party_price_usd"])
        result.add(
          SwapAsset(
            addressOrNative = SwapAsset.NATIVE_TOKEN_ADDRESS,
            rawAddress = null,
            symbol = "GRAM",
            name = "Toncoin (GRAM)",
            decimals = GRAM_DECIMALS,
            imageUrl =
              JettonUrlSanitizer.sanitizeImageUrl(map["image_url"]?.toString())
                ?: OFFICIAL_TON_LOGO_URL,
            verification = JettonVerificationStatus.ALLOWLISTED,
            priceUsd = tonUsd,
          )
        )
        continue
      }

      val parsedAddr = TonAddress.parse(contract, Network.MAINNET).getOrNull() ?: continue
      if (JettonAllowList.isLikelyScamMetadata(displayNameRaw, symbolRaw)) continue
      val allowSpec = JettonAllowList.findVerified(parsedAddr, decimals)

      val symbol =
        allowSpec?.symbol ?: JettonMetadataSanitizer.sanitizeSymbol(symbolRaw, parsedAddr.toFriendly(true).take(6))
      val name =
        allowSpec?.name ?: JettonMetadataSanitizer.sanitizeName(displayNameRaw, symbol)
      val community = map["community"] as? Boolean ?: false
      val defaultSymbol = map["default_symbol"] as? Boolean ?: false
      val verification =
        when {
          allowSpec != null -> JettonVerificationStatus.ALLOWLISTED
          defaultSymbol && !community -> JettonVerificationStatus.VERIFIED
          else -> JettonVerificationStatus.UNVERIFIED
        }
      if (verification == JettonVerificationStatus.UNVERIFIED) continue

      result.add(
        SwapAsset(
          addressOrNative = allowSpec?.masterFriendly ?: parsedAddr.toFriendly(bounceable = true),
          rawAddress = parsedAddr.raw,
          symbol = symbol,
          name = name,
          decimals = allowSpec?.decimals ?: decimals,
          imageUrl = JettonUrlSanitizer.sanitizeImageUrl(map["image_url"]?.toString()),
          verification = verification,
          priceUsd = parseOptionalBigDecimal(map["dex_price_usd"] ?: map["third_party_price_usd"]),
        )
      )
    }
    return result
  }

  private fun fetchRemoteCatalog(): List<SwapAsset> {
    val stonFiResult =
      runCatching {
        val req =
          Request.Builder()
            .url(stonFiAssetsUrl)
            .get()
            .header("Accept", "application/json")
            .build()
        okHttpClient.newCall(req).execute().use { res ->
          if (!res.isSuccessful) return@runCatching emptyList()
          val body = res.body?.string().orEmpty()
          parseStonFiAssetsResponse(body)
        }
      }.getOrDefault(emptyList())
    return stonFiResult
  }

  private fun decodeAndValidateBocCell(base64Cell: String): Cell =
    try {
      val normalized = base64Cell.trim().replace('-', '+').replace('_', '/')
      val bytes = Base64.getDecoder().decode(normalized)
      val roots = BagOfCells(bytes).roots
      roots.firstOrNull()
        ?: throw InvalidPayloadException("swap.coffee", "Swap cell BOC has no root cell")
    } catch (e: InvalidPayloadException) {
      throw e
    } catch (t: Throwable) {
      throw InvalidPayloadException("swap.coffee", "Invalid swap BOC cell: ${t.message}")
    }

  private fun parseNanoValue(raw: Any?): BigInteger {
    val str = raw?.toString()?.trim().orEmpty()
    if (str.isEmpty()) return BigInteger.ZERO
    return try {
      if (str.contains('.')) {
        val coins = BigDecimal(str)
        require(coins >= BigDecimal.ZERO) { "Swap tx value cannot be negative" }
        coins.movePointRight(GRAM_DECIMALS).setScale(0, RoundingMode.UP).toBigInteger()
      } else {
        val nano = BigInteger(str)
        require(nano >= BigInteger.ZERO) { "Swap tx value cannot be negative" }
        nano
      }
    } catch (e: Throwable) {
      throw InvalidPayloadException("swap.coffee", "Invalid swap tx value '$str': ${e.message}")
    }
  }

  private fun parseRecommendedGas(raw: Any?): Money {
    val str = raw?.toString()?.trim().orEmpty()
    if (str.isEmpty()) return Money.ofNano(TransferBuilder.LOCAL_SWAP_FALLBACK_GAS_NANO)
    val dec = runCatching { BigDecimal(str) }.getOrNull()
      ?: return Money.ofNano(TransferBuilder.LOCAL_SWAP_FALLBACK_GAS_NANO)
    if (dec <= BigDecimal.ZERO) return Money.ofNano(TransferBuilder.LOCAL_SWAP_FALLBACK_GAS_NANO)
    return if (str.contains('.') || dec < BigDecimal("10000")) {
      val nano = dec.movePointRight(GRAM_DECIMALS).setScale(0, RoundingMode.UP).toBigInteger()
      Money(nano.coerceAtLeast(BigInteger.ZERO), GRAM_DECIMALS)
    } else {
      Money(dec.toBigInteger(), GRAM_DECIMALS)
    }
  }

  private fun parseOptionalDouble(raw: Any?): Double? =
    raw?.toString()?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }

  private fun parseOptionalBigDecimal(raw: Any?): BigDecimal? =
    raw?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
      runCatching { BigDecimal(it).takeIf { bd -> bd > BigDecimal.ZERO } }.getOrNull()
    }

  private fun extractDexLabels(node: Any?): List<String> {
    val labels = linkedSetOf<String>()
    fun walk(current: Any?) {
      when (current) {
        is Map<*, *> -> {
          current["dex"]?.toString()?.let { dexName ->
            labels.add(formatDexDisplayName(dexName))
          }
          current.values.forEach { walk(it) }
        }
        is List<*> -> current.forEach { walk(it) }
      }
    }
    walk(node)
    return labels.toList()
  }

  private fun formatDexDisplayName(raw: String): String =
    when (raw.trim().lowercase()) {
      "stonfi", "stonfi_v2", "ston.fi" -> "STON.fi"
      "dedust" -> "DeDust"
      "tonco" -> "TONCO"
      "coffee" -> "swap.coffee"
      "megaton" -> "Megaton"
      else -> raw.trim().replaceFirstChar { it.uppercase() }
    }

  private fun usableSecret(raw: String): String? {
    val clean = raw.trim().trim('"')
    if (clean.isEmpty()) return null
    if (clean.startsWith("YOUR_", ignoreCase = true) || clean.startsWith("MY_", ignoreCase = true)) {
      return null
    }
    return clean
  }

  private fun buildApiException(provider: String, code: Int, body: String): Exception =
    when {
      code == 429 -> RateLimitedException(provider)
      code in 500..599 -> ServerErrorException(provider, code)
      else -> InvalidPayloadException(provider, "HTTP $code: ${body.take(160)}")
    }

  companion object {
    const val DEFAULT_SWAP_COFFEE_BASE_URL = "https://backend.swap.coffee"
    const val DEFAULT_STONFI_ASSETS_URL = "https://api.ston.fi/v1/assets"
    const val MAX_SPLITS_FOR_TON_WALLET = 4
    const val MAX_SWAP_GAS_GUARD_NANO = 2_000_000_000L // 2.0 TON generous safety cap for multi-split swap gas
    const val OFFICIAL_TON_LOGO_URL = "https://ton.org/download/ton_symbol.png"
    private const val CATALOG_TTL_MS = 5 * 60_000L
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    fun curatedDefaultAssets(): List<SwapAsset> {
      val usdtSpec = JettonAllowList.MAINNET_USDT
      val notAddr = TonAddress.parse("EQAvlWFDxGF2lXm67y4yzC17wYKD9A0guwPkMs1gOsM__NOT", Network.MAINNET).getOrThrow()
      val stonAddr = TonAddress.parse("EQA2kCVNwVsil2EM2mB0SkXytxCqQjS4mttjDpnXmwG9T6bO", Network.MAINNET).getOrThrow()
      val dogsAddr = TonAddress.parse("EQCvxJy4eG8hyHBFsZ7eePxrRsUQSFE_jpptRAYBmcG_DOGS", Network.MAINNET).getOrThrow()

      return listOf(
        SwapAsset(
          addressOrNative = SwapAsset.NATIVE_TOKEN_ADDRESS,
          rawAddress = null,
          symbol = "GRAM",
          name = "Toncoin (GRAM)",
          decimals = GRAM_DECIMALS,
          imageUrl = OFFICIAL_TON_LOGO_URL,
          verification = JettonVerificationStatus.ALLOWLISTED,
        ),
        SwapAsset(
          addressOrNative = usdtSpec.masterFriendly,
          rawAddress = usdtSpec.masterAddress.raw,
          symbol = usdtSpec.symbol,
          name = usdtSpec.name,
          decimals = USDT_DECIMALS,
          verification = JettonVerificationStatus.ALLOWLISTED,
        ),
        SwapAsset(
          addressOrNative = notAddr.toFriendly(bounceable = true),
          rawAddress = notAddr.raw,
          symbol = "NOT",
          name = "Notcoin",
          decimals = GRAM_DECIMALS,
          verification = JettonVerificationStatus.VERIFIED,
        ),
        SwapAsset(
          addressOrNative = stonAddr.toFriendly(bounceable = true),
          rawAddress = stonAddr.raw,
          symbol = "STON",
          name = "STON.fi",
          decimals = GRAM_DECIMALS,
          verification = JettonVerificationStatus.VERIFIED,
        ),
        SwapAsset(
          addressOrNative = dogsAddr.toFriendly(bounceable = true),
          rawAddress = dogsAddr.raw,
          symbol = "DOGS",
          name = "DOGS",
          decimals = GRAM_DECIMALS,
          verification = JettonVerificationStatus.VERIFIED,
        ),
      )
    }
  }
}
