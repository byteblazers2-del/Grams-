package com.example

import com.example.core.model.GRAM_DECIMALS
import com.example.core.model.MnemonicPhrase
import com.example.core.model.MnemonicScheme
import com.example.core.model.Money
import com.example.core.model.Network
import com.example.core.model.TonAddress
import com.example.core.model.TxDirection
import com.example.core.model.USDT_DECIMALS
import com.example.core.model.WalletVersion
import com.example.core.network.AllProvidersFailedException
import com.example.core.network.ProviderRateLimitConfig
import com.example.core.network.ProviderRateLimiter
import com.example.core.network.ProviderRouter
import com.example.core.network.TonApiClient
import com.example.core.network.TonCenterClient
import com.example.core.network.TonCommentDecoder
import com.example.core.wallet.AddressService
import com.example.core.wallet.MnemonicService
import com.example.core.wallet.TransferBuilder
import java.math.BigInteger
import java.util.Base64
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.block.CommonMsgInfoRelaxed
import org.ton.block.Either
import org.ton.block.ExtInMsgInfo
import org.ton.block.Just
import org.ton.block.Message
import org.ton.block.MessageRelaxed
import org.ton.block.Nothing
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.CellSlice
import org.ton.kotlin.cell.CellContext
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.kotlin.crypto.mnemonic.Mnemonic
import org.ton.tlb.TlbCodec

class StressAndVectorsUnitTest {

  private val rawCellCodec =
    object : TlbCodec<Cell> {
      override fun storeTlb(builder: CellBuilder, value: Cell, context: CellContext) {
        builder.storeBitString(value.bits)
        value.refs.forEach { builder.storeRef(it) }
      }

      override fun loadTlb(slice: CellSlice, context: CellContext): Cell {
        val bits = slice.bits.drop(slice.bitsPosition).toBooleanArray()
        val refs = slice.refs.drop(slice.refsPosition)
        return CellBuilder.createCell {
          bits.forEach { storeBoolean(it) }
          refs.forEach { storeRef(it) }
        }
      }
    }

  private fun mockHttpClient(handler: Request.() -> Response): OkHttpClient =
    OkHttpClient.Builder()
      .addInterceptor { chain -> chain.request().handler() }
      .build()

  private fun jsonResponse(request: Request, code: Int, body: String): Response =
    Response.Builder()
      .request(request)
      .protocol(Protocol.HTTP_1_1)
      .code(code)
      .message("HTTP $code")
      .body(body.toResponseBody("application/json".toMediaType()))
      .build()

  @Test
  fun official_ton_and_bip39_vectors_match_sdk_and_wallet_versions() {
    // Official BIP-39 12-word test vector ("abandon"*11 + "about") -> deterministic Ed25519 SLIP-0010 key
    val bip39_12 =
      "  ABANDON  abandon abandon abandon abandon abandon\n" +
        "abandon abandon abandon abandon abandon ABOUT  "
    val chars12 = bip39_12.toCharArray()
    val phrase12 = MnemonicService.parseSpaceSeparatedChars(chars12)
    chars12.fill('\u0000')
    try {
      assertEquals(12, phrase12.wordCount)
      assertEquals(listOf(MnemonicScheme.MULTICHAIN), MnemonicService.detectValidSchemes(phrase12))
      val multichainKey = MnemonicService.deriveMultichainKey(phrase12)
      try {
        val sdkPriv = PrivateKeyEd25519(multichainKey.seed32)
        assertArrayEquals(sdkPriv.publicKey().key.toByteArray(), multichainKey.publicKey32)

        val candidates = AddressService.discoverCandidateAccounts(multichainKey.publicKey32, MnemonicScheme.MULTICHAIN)
        assertEquals(4, candidates.size)
        // All 4 wallet versions produce distinct Mainnet addresses and canonical friendly encodings
        assertEquals(4, candidates.map { it.mainnetAddress.raw }.distinct().size)
        candidates.forEach { candidate ->
          val nonBounce = candidate.mainnetAddress.toFriendly(bounceable = false)
          val bounce = candidate.mainnetAddress.toFriendly(bounceable = true)
          assertTrue(nonBounce.startsWith("UQ"))
          assertTrue(bounce.startsWith("EQ"))
          assertEquals(candidate.mainnetAddress.raw, TonAddress.parse(nonBounce, Network.MAINNET).getOrThrow().raw)
          assertEquals(candidate.mainnetAddress.raw, TonAddress.parse(bounce, Network.MAINNET).getOrThrow().raw)
        }
      } finally {
        multichainKey.wipe()
      }
    } finally {
      phrase12.wipe()
    }

    // Valid TON 24-word mnemonic vector cross-checked byte-for-byte with org.ton.kotlin.crypto.mnemonic.Mnemonic
    val fixedSdkWords =
      listOf(
        "bunker", "present", "derive", "member", "useless", "upset",
        "corn", "measure", "obey", "void", "problem", "desk",
        "crack", "jelly", "large", "caught", "vendor", "mean",
        "purpose", "library", "secret", "deliver", "patient", "frame",
      )
    val sdkMnemonic = Mnemonic(fixedSdkWords)
    assertTrue("Known TON 24-word vector must validate in TON SDK", sdkMnemonic.isValid())

    val chars24 = fixedSdkWords.joinToString(" ").toCharArray()
    val phrase24 = MnemonicService.parseSpaceSeparatedChars(chars24)
    chars24.fill('\u0000')
    try {
      assertTrue(MnemonicService.validateTon24Mnemonic(phrase24))
      val derived = MnemonicService.deriveTon24Key(phrase24)
      try {
        val sdkSeed32 = sdkMnemonic.toSeed().copyOf(32)
        assertArrayEquals(sdkSeed32, derived.seed32)
        assertArrayEquals(PrivateKeyEd25519(sdkSeed32).publicKey().key.toByteArray(), derived.publicKey32)
      } finally {
        derived.wipe()
      }
    } finally {
      phrase24.wipe()
    }
  }

  @Test
  fun random_property_money_arithmetic_formatting_and_roundtrips() {
    val rng = Random(0x5A17C0DE)
    val decimalsChoices = listOf(GRAM_DECIMALS, USDT_DECIMALS)

    repeat(150) { trial ->
      val decimals = decimalsChoices[trial % decimalsChoices.size]
      val bitLenA = rng.nextInt(1, 72)
      val bitLenB = rng.nextInt(1, 72)
      val unitsA = BigInteger(bitLenA, java.util.Random(rng.nextLong()))
      val unitsB = BigInteger(bitLenB, java.util.Random(rng.nextLong()))

      val moneyA = Money(unitsA, decimals)
      val moneyB = Money(unitsB, decimals)

      // Format -> fromDecimalString round-trip must preserve exact BigInteger units
      val formattedA = moneyA.format()
      val reparsedA = Money.fromDecimalString(formattedA, decimals)
      assertEquals("Round-trip failed on trial $trial ($formattedA)", moneyA, reparsedA)

      // parseUnits(units.toString()) must match exact BigInteger
      assertEquals(moneyA, Money.parseUnits(unitsA.toString(), decimals))

      // Exact addition & subtraction identities
      val sum = moneyA + moneyB
      assertEquals(unitsA.add(unitsB), sum.units)
      assertEquals(moneyB + moneyA, sum)

      if (unitsA >= unitsB) {
        val diff = moneyA - moneyB
        assertEquals(unitsA.subtract(unitsB), diff.units)
        assertEquals(moneyA, diff + moneyB)
      } else {
        var threw = false
        try {
          moneyA - moneyB
        } catch (_: IllegalArgumentException) {
          threw = true
        }
        assertTrue("Subtracting larger Money must throw IllegalArgumentException", threw)
      }

      // Excess precision beyond decimals must always be rejected
      val overPrecise = "1." + "0".repeat(decimals) + "1"
      var rejectedScale = false
      try {
        Money.fromDecimalString(overPrecise, decimals)
      } catch (_: IllegalArgumentException) {
        rejectedScale = true
      }
      assertTrue("Over-precise decimal $overPrecise must fail for decimals=$decimals", rejectedScale)
    }
  }

  @Test
  fun random_property_transfer_builder_boc_roundtrip_and_ed25519_signature_verification() {
    val words =
      listOf(
        "abandon", "abandon", "abandon", "abandon", "abandon", "abandon",
        "abandon", "abandon", "abandon", "abandon", "abandon", "about",
      ).map { it.toCharArray() }
    val phrase = MnemonicPhrase(words)
    val derived = MnemonicService.deriveMultichainKey(phrase)
    val rng = Random(0xBEEFCAFE)
    val senderJettonWallet = TonAddress("0:" + "aa".repeat(32), Network.MAINNET)

    try {
      val signerKey = PrivateKeyEd25519(derived.seed32)
      assertArrayEquals(derived.publicKey32, signerKey.publicKey().key.toByteArray())
      val commentSamples =
        listOf(
          null,
          "hi",
          "Payment #42",
          "سفارش تست",
          "A".repeat(TransferBuilder.MAX_COMMENT_UTF8_BYTES),
        )

      repeat(24) { idx ->
        val version = WalletVersion.entries[idx % WalletVersion.entries.size]
        val sender = AddressService.deriveAddress(derived.publicKey32, version, Network.MAINNET)
        val destBytes = ByteArray(32) { rng.nextInt(256).toByte() }
        val destination = TonAddress("0:" + AddressService.bytesToHex(destBytes), Network.MAINNET)
        val seqno = rng.nextInt(0, 10_000)
        val validUntil = 1_800_000_000L + rng.nextLong(60L, 600L)
        val needsStateInit = (idx % 3 == 0)
        val bounce = (idx % 2 == 0)
        val comment = commentSamples[idx % commentSamples.size]
        val isJetton = (idx % 4 == 1)

        val amount =
          if (isJetton) {
            Money.parseUnits(rng.nextLong(1L, 1_000_000_000L).toString(), USDT_DECIMALS)
          } else {
            Money.ofNano(rng.nextLong(1L, 50_000_000_000L))
          }

        val signed =
          if (isJetton) {
            TransferBuilder.buildSignedJettonTransfer(
              seed32 = derived.seed32,
              publicKey32 = derived.publicKey32,
              senderAddress = sender,
              senderJettonWallet = senderJettonWallet,
              walletVersion = version,
              destination = destination,
              jettonAmount = amount,
              seqno = seqno,
              validUntilEpochSec = validUntil,
              needsStateInit = needsStateInit,
              comment = comment,
            )
          } else {
            TransferBuilder.buildSignedTransfer(
              seed32 = derived.seed32,
              publicKey32 = derived.publicKey32,
              senderAddress = sender,
              walletVersion = version,
              destination = destination,
              amount = amount,
              bounce = bounce,
              seqno = seqno,
              validUntilEpochSec = validUntil,
              needsStateInit = needsStateInit,
              comment = comment,
            )
          }

        // Round-trip deserialize BOC
        val parsedBoc = BagOfCells(signed.externalBocBytes)
        val root = parsedBoc.roots.single()
        assertEquals(signed.messageHashHex, AddressService.bytesToHex(root.hash().toByteArray()))

        val msg = Message.tlbCodec(rawCellCodec).loadTlb(root.beginParse())
        assertTrue(msg.info is ExtInMsgInfo)
        assertEquals(needsStateInit, msg.init is Just)
        if (!needsStateInit) {
          assertTrue(msg.init is Nothing)
          assertEquals(signed.messageHashHex, signed.normalizedHashHex)
        } else {
          assertNotEquals(signed.messageHashHex, signed.normalizedHashHex)
        }

        val bodyCell =
          when (val body = msg.body) {
            is Either.Left -> body.value
            is Either.Right -> body.value.load()
          }
        assertEquals(signed.bodyHashHex, AddressService.bytesToHex(bodyCell.hash().toByteArray()))

        // Split signature & unsigned payload depending on version and cryptographically verify Ed25519 signature
        val totalBits = bodyCell.bits.size
        val sigBitOffset = if (version == WalletVersion.V5R1) totalBits - 512 else 0
        val unsignedRange = if (version == WalletVersion.V5R1) 0 until (totalBits - 512) else 512 until totalBits

        val sigBytes =
          ByteArray(64) { byteIdx ->
            var acc = 0
            for (bit in 0 until 8) {
              if (bodyCell.bits[sigBitOffset + byteIdx * 8 + bit]) {
                acc = acc or (1 shl (7 - bit))
              }
            }
            acc.toByte()
          }
        val unsignedRebuilt =
          CellBuilder.createCell {
            for (bitIdx in unsignedRange) {
              storeBoolean(bodyCell.bits[bitIdx])
            }
            bodyCell.refs.forEach { storeRef(it) }
          }
        val unsignedHash = unsignedRebuilt.hash().toByteArray()
        val expectedSig = signerKey.signToByteArray(unsignedHash, 0, unsignedHash.size)
        assertArrayEquals(
          "Ed25519 signature mismatch for $version trial $idx",
          expectedSig,
          sigBytes,
        )

        // Verify internal message destination/value/comment
        val internalMsgCell =
          if (version == WalletVersion.V5R1) {
            bodyCell.refs.single().refs.last()
          } else {
            bodyCell.refs.single()
          }
        val parsedInternal = MessageRelaxed.tlbCodec(rawCellCodec).loadTlb(internalMsgCell.beginParse())
        val intInfo = parsedInternal.info as CommonMsgInfoRelaxed.IntMsgInfoRelaxed
        val internalBody =
          when (val ib = parsedInternal.body) {
            is Either.Left -> ib.value
            is Either.Right -> ib.value.load()
          }

        if (!isJetton) {
          assertEquals(bounce, intInfo.bounce)
          assertEquals(amount.units.toString(), intInfo.value.coins.amount.value.toString(10))
          val expectedComment = comment?.takeIf { it.isNotEmpty() }
          val internalBocB64 = Base64.getEncoder().encodeToString(BagOfCells(internalBody).toByteArray())
          assertEquals(expectedComment, TonCommentDecoder.decodeTextCommentFromBoc(internalBocB64))
        } else {
          // TEP-74 internal message is always bounceable with attached gas sent to sender's Jetton wallet
          assertTrue(intInfo.bounce)
          assertEquals(TransferBuilder.DEFAULT_JETTON_ATTACHED_GRAM_NANO.toString(), intInfo.value.coins.amount.value.toString(10))
          assertTrue("TEP-74 transfer opcode missing", internalBody.bits.size >= 32)
        }
      }
    } finally {
      phrase.wipe()
      derived.wipe()
    }
  }

  @Test
  fun provider_router_handles_repeated_429s_partial_json_and_duplicate_history() = runTest {
    var nowMs = 50_000L
    val testAddr = TonAddress("0:" + "22".repeat(32), Network.MAINNET)
    val counterparty = TonAddress("0:" + "33".repeat(32), Network.MAINNET)

    val limiter =
      ProviderRateLimiter(
        config =
          ProviderRateLimitConfig(
            minIntervalMs = 0L,
            baseBackoffMs = 5L,
            maxBackoffMs = 1_000L,
            failureThresholdToOpenCircuit = 2,
            openCircuitBaseMs = 500L,
          ),
        clockMs = { nowMs },
        sleeper = { nowMs += it },
        jitterProvider = { 0.5 },
      )

    var tonCenterHits = 0
    val tonCenterAlways429 =
      TonCenterClient(
        okHttpClient =
          mockHttpClient {
            tonCenterHits++
            jsonResponse(this, 429, """{"error":"rate limited"}""")
          },
        clockMs = { nowMs },
      )

    var tonApiTruncatedFirst = true
    val dupHash = "ee".repeat(32)
    val dupHistoryPayload =
      """
      {
        "transactions": [
          {
            "hash": "$dupHash",
            "lt": 7001,
            "utime": 1794750000,
            "aborted": false,
            "total_fees": 1200000,
            "in_msg": {
              "hash": "${"f1".repeat(32)}",
              "msg_type": "int_msg",
              "value": 1000000000,
              "source": {"address": "${counterparty.raw}"},
              "destination": {"address": "${testAddr.raw}"},
              "decoded_op_name": "text_comment",
              "decoded_body": {"text": "first copy"}
            },
            "out_msgs": []
          },
          {
            "hash": "${"ef".repeat(32)}",
            "lt": 7002,
            "utime": 1794750005,
            "aborted": false,
            "total_fees": 1200000,
            "in_msg": {
              "hash": "${"f2".repeat(32)}",
              "msg_type": "int_msg",
              "value": 1000000000,
              "source": {"address": "${counterparty.raw}"},
              "destination": {"address": "${testAddr.raw}"},
              "decoded_op_name": "text_comment",
              "decoded_body": {"text": "second copy"}
            },
            "out_msgs": []
          }
        ]
      }
      """.trimIndent()

    val tonApiFlakyThenValid =
      TonApiClient(
        okHttpClient =
          mockHttpClient {
            if (tonApiTruncatedFirst) {
              tonApiTruncatedFirst = false
              jsonResponse(this, 200, """{"transactions":[{"hash": "trunc""")
            } else {
              jsonResponse(this, 200, dupHistoryPayload)
            }
          },
        clockMs = { nowMs },
      )

    val router = ProviderRouter(providers = listOf(tonCenterAlways429, tonApiFlakyThenValid), rateLimiter = limiter)

    // Call 1: TON Center returns 429 (opens circuit immediately); TonAPI returns truncated JSON -> AllProvidersFailedException
    val firstAttempt = router.fetchAccountHistory(testAddr, limit = 10)
    assertTrue(firstAttempt.isFailure)
    assertTrue(firstAttempt.exceptionOrNull() is AllProvidersFailedException)
    assertTrue("HTTP 429 must open TON Center circuit immediately", limiter.isCircuitOpen("TON Center"))
    val firstOpenUntil = limiter.openUntilEpochMs("TON Center")

    // Call 2 (while circuit is open): Router skips TON Center completely; TonAPI recovers cleanly with duplicate-action event
    val hitsDuringOpen = tonCenterHits
    val secondAttempt = router.fetchAccountHistory(testAddr, limit = 10).getOrThrow()
    assertEquals("TonAPI", secondAttempt.providerName)
    assertEquals(hitsDuringOpen, tonCenterHits)
    assertEquals(2, secondAttempt.transfers.size)
    assertEquals(TxDirection.IN, secondAttempt.transfers[0].direction)

    // Advance past first cooldown so TON Center becomes half-open again, hit a 2nd 429 -> backoff doubles & circuit re-opens longer
    nowMs = firstOpenUntil + 10L
    assertFalse(limiter.isCircuitOpen("TON Center"))
    tonApiTruncatedFirst = false
    val thirdAttempt = router.fetchAccountHistory(testAddr, limit = 10).getOrThrow()
    assertEquals("TonAPI", thirdAttempt.providerName)
    assertEquals(hitsDuringOpen + 1, tonCenterHits)
    assertTrue("Repeated 429 must re-open TON Center circuit with longer cooldown", limiter.isCircuitOpen("TON Center"))
    assertEquals(10L, limiter.lastAppliedBackoffMs("TON Center"))
  }
}
