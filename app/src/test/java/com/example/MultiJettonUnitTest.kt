package com.example

import com.example.core.model.JettonAllowList
import com.example.core.model.JettonMetadataSanitizer
import com.example.core.model.JettonUrlSanitizer
import com.example.core.model.JettonVerificationStatus
import com.example.core.model.Money
import com.example.core.model.Network
import com.example.core.model.TonAddress
import com.example.core.model.TxDirection
import com.example.core.model.USDT_DECIMALS
import com.example.core.network.OnChainMessageStatus
import com.example.core.network.TonApiClient
import com.example.core.network.TonCenterClient
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiJettonUnitTest {

  @Test
  fun url_and_metadata_sanitizers_handle_ipfs_reject_unsafe_schemes_and_detect_scams() {
    assertEquals(
      "https://ipfs.io/ipfs/QmTestHash123/logo.png",
      JettonUrlSanitizer.sanitizeImageUrl("ipfs://QmTestHash123/logo.png"),
    )
    assertEquals(
      "https://ipfs.io/ipfs/bafybei123/token.webp",
      JettonUrlSanitizer.sanitizeImageUrl("ipfs://ipfs/bafybei123/token.webp"),
    )
    assertEquals(
      "https://cdn.example.com/notcoin.png",
      JettonUrlSanitizer.sanitizeImageUrl("https://cdn.example.com/notcoin.png"),
    )
    assertNull(JettonUrlSanitizer.sanitizeImageUrl("http://insecure.example.com/icon.png"))
    assertNull(JettonUrlSanitizer.sanitizeImageUrl("javascript:alert(1)"))
    assertNull(JettonUrlSanitizer.sanitizeImageUrl("file:///etc/passwd"))
    assertNull(JettonUrlSanitizer.sanitizeImageUrl("https://localhost/evil.png"))
    assertNull(JettonUrlSanitizer.sanitizeImageUrl("ipfs://../secret"))
    assertNull(JettonUrlSanitizer.sanitizeImageUrl("https://cdn.example.com/has space.png"))

    assertEquals("NOT", JettonMetadataSanitizer.sanitizeSymbol("  NOT\n\t ", "JTN"))
    assertEquals("0123456789ABCDEF", JettonMetadataSanitizer.sanitizeSymbol("0123456789ABCDEFXYZ"))
    assertEquals("Notcoin Official", JettonMetadataSanitizer.sanitizeName("Notcoin\nOfficial", "NOT"))

    assertTrue(JettonAllowList.isLikelyScamMetadata("Claim AIRDROP at ton-gift.io", "SCAM"))
    assertTrue(JettonAllowList.isLikelyScamMetadata("TON Voucher", "t.me/claimbot"))
    assertFalse(JettonAllowList.isLikelyScamMetadata("Notcoin", "NOT"))
    assertFalse(JettonAllowList.isLikelyScamMetadata("DOGS", "DOGS"))
  }

  @Test
  fun money_format_supports_zero_one_six_and_nine_decimal_jettons() {
    assertEquals("1234", Money(BigInteger.valueOf(1234L), 0).format())
    assertEquals("0", Money(BigInteger.ZERO, 0).format())
    assertEquals("1.5", Money(BigInteger.valueOf(15L), 1).format())
    assertEquals("2.0", Money(BigInteger.valueOf(20L), 1).format())
    assertEquals("1.25", Money(BigInteger.valueOf(1_250_000_000L), 9).format())
    assertEquals("0.000001", Money(BigInteger.ONE, 6).format())
  }

  @Test
  fun tonapi_fetchAllJettons_and_history_parse_multiple_jettons_and_filter_blacklisted_scams() =
    runTest {
      val owner = TonAddress("0:" + "11".repeat(32), Network.MAINNET)
      val usdtMasterRaw = JettonAllowList.MAINNET_USDT.masterAddress.raw
      val notMasterRaw = "0:" + "22".repeat(32)
      val dogsMasterRaw = "0:" + "33".repeat(32)
      val blacklistedMasterRaw = "0:" + "44".repeat(32)
      val scamNameMasterRaw = "0:" + "55".repeat(32)
      val usdtWallet = "0:" + "61".repeat(32)
      val notWallet = "0:" + "62".repeat(32)
      val dogsWallet = "0:" + "63".repeat(32)
      val counterparty = "0:" + "77".repeat(32)

      val client =
        TonApiClient(
          okHttpClient =
            mockHttpClient {
              val path = request().url.encodedPath
              when {
                path.endsWith("/jettons") ->
                  jsonResponse(
                    200,
                    """
                    {
                      "balances": [
                        {
                          "balance": "12500000",
                          "wallet_address": {"address": "$usdtWallet"},
                          "jetton": {
                            "address": "$usdtMasterRaw",
                            "symbol": "USD₮",
                            "name": "Tether USD FakeNameShouldCanonicalize",
                            "decimals": 6,
                            "preview": "https://cdn.tether.to/usdt.png",
                            "verification": "whitelist"
                          }
                        },
                        {
                          "balance": "250000000000",
                          "wallet_address": {"address": "$notWallet"},
                          "jetton": {
                            "address": "$notMasterRaw",
                            "symbol": "NOT",
                            "name": "Notcoin",
                            "decimals": 9,
                            "preview": "ipfs://QmNotcoinLogoCid/not.png",
                            "verification": "whitelist"
                          }
                        },
                        {
                          "balance": "4200",
                          "wallet_address": {"address": "$dogsWallet"},
                          "jetton": {
                            "address": "$dogsMasterRaw",
                            "symbol": "DOGS",
                            "name": "Dogs Community",
                            "decimals": 0,
                            "image": "http://unsafe-http.example.com/dogs.png",
                            "verification": "none"
                          }
                        },
                        {
                          "balance": "999999",
                          "wallet_address": {"address": "0:${"64".repeat(32)}"},
                          "jetton": {
                            "address": "$blacklistedMasterRaw",
                            "symbol": "BAD",
                            "name": "Blacklisted Token",
                            "decimals": 9,
                            "verification": "blacklist"
                          }
                        },
                        {
                          "balance": "777",
                          "wallet_address": {"address": "0:${"65".repeat(32)}"},
                          "jetton": {
                            "address": "$scamNameMasterRaw",
                            "symbol": "TON",
                            "name": "Claim airdrop evil.io",
                            "decimals": 9,
                            "verification": "none"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                  )
                path.endsWith("/jettons/history") ->
                  jsonResponse(
                    200,
                    """
                    {
                      "events": [
                        {
                          "event_id": "evt_not_in",
                          "lt": 9001,
                          "timestamp": 1715000100,
                          "in_progress": false,
                          "is_scam": false,
                          "actions": [
                            {
                              "type": "JettonTransfer",
                              "status": "ok",
                              "JettonTransfer": {
                                "sender": {"address": "$counterparty"},
                                "recipient": {"address": "${owner.raw}"},
                                "amount": "250000000000",
                                "comment": "Notcoin bonus",
                                "jetton": {
                                  "address": "$notMasterRaw",
                                  "symbol": "NOT",
                                  "name": "Notcoin",
                                  "decimals": 9,
                                  "preview": "ipfs://QmNotcoinLogoCid/not.png",
                                  "verification": "whitelist"
                                }
                              }
                            }
                          ]
                        },
                        {
                          "event_id": "evt_dogs_out",
                          "lt": 9002,
                          "timestamp": 1715000200,
                          "in_progress": false,
                          "is_scam": false,
                          "actions": [
                            {
                              "type": "JettonTransfer",
                              "status": "ok",
                              "JettonTransfer": {
                                "sender": {"address": "${owner.raw}"},
                                "recipient": {"address": "$counterparty"},
                                "amount": "500",
                                "jetton": {
                                  "address": "$dogsMasterRaw",
                                  "symbol": "DOGS",
                                  "name": "Dogs Community",
                                  "decimals": 0,
                                  "verification": "none"
                                }
                              }
                            }
                          ]
                        },
                        {
                          "event_id": "evt_blacklisted",
                          "lt": 9003,
                          "timestamp": 1715000300,
                          "in_progress": false,
                          "is_scam": false,
                          "actions": [
                            {
                              "type": "JettonTransfer",
                              "status": "ok",
                              "JettonTransfer": {
                                "sender": {"address": "$counterparty"},
                                "recipient": {"address": "${owner.raw}"},
                                "amount": "1000",
                                "jetton": {
                                  "address": "$blacklistedMasterRaw",
                                  "symbol": "BAD",
                                  "name": "Blacklisted",
                                  "decimals": 9,
                                  "verification": "blacklist"
                                }
                              }
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                  )
                else -> jsonResponse(404, """{}""")
              }
            }
        )

      val holdingsPage = client.fetchAllJettons(owner)
      assertEquals(3, holdingsPage.holdings.size)

      val usdt = holdingsPage.holdings.first { it.masterRaw == usdtMasterRaw }
      assertEquals("USDT", usdt.symbol)
      assertEquals("Tether USD", usdt.name)
      assertEquals(USDT_DECIMALS, usdt.balance.decimals)
      assertEquals("12.50", usdt.balance.format())
      assertEquals(JettonVerificationStatus.ALLOWLISTED, usdt.verification)
      assertEquals("https://cdn.tether.to/usdt.png", usdt.imageUrl)

      val notcoin = holdingsPage.holdings.first { it.masterRaw == notMasterRaw }
      assertEquals("NOT", notcoin.symbol)
      assertEquals("Notcoin", notcoin.name)
      assertEquals(9, notcoin.balance.decimals)
      assertEquals("250.00", notcoin.balance.format())
      assertEquals(JettonVerificationStatus.VERIFIED, notcoin.verification)
      assertEquals("https://ipfs.io/ipfs/QmNotcoinLogoCid/not.png", notcoin.imageUrl)

      val dogs = holdingsPage.holdings.first { it.masterRaw == dogsMasterRaw }
      assertEquals("DOGS", dogs.symbol)
      assertEquals(0, dogs.balance.decimals)
      assertEquals("4200", dogs.balance.format())
      assertEquals(JettonVerificationStatus.UNVERIFIED, dogs.verification)
      assertNull(dogs.imageUrl)

      val history = client.fetchAllJettonHistory(owner, limit = 25)
      assertEquals(2, history.transfers.size)

      val notTransfer = history.transfers[0]
      assertEquals(TxDirection.IN, notTransfer.direction)
      assertEquals("NOT", notTransfer.jettonSymbol)
      assertEquals("Notcoin", notTransfer.jettonName)
      assertEquals("250.00", notTransfer.amount.format())
      assertEquals(JettonVerificationStatus.VERIFIED, notTransfer.jettonVerification)
      assertEquals("https://ipfs.io/ipfs/QmNotcoinLogoCid/not.png", notTransfer.jettonImageUrl)

      val dogsTransfer = history.transfers[1]
      assertEquals(TxDirection.OUT, dogsTransfer.direction)
      assertEquals("DOGS", dogsTransfer.jettonSymbol)
      assertEquals("500", dogsTransfer.amount.format())
      assertEquals(JettonVerificationStatus.UNVERIFIED, dogsTransfer.jettonVerification)
    }

  @Test
  fun toncenter_fetchAllJettons_and_history_join_metadata_regardless_of_json_key_order() =
    runTest {
      val owner = TonAddress("0:" + "12".repeat(32), Network.MAINNET)
      val notMasterRaw = "0:" + "ab".repeat(32)
      val notWallet = "0:" + "cd".repeat(32)
      val scamMasterRaw = "0:" + "ef".repeat(32)
      val scamWallet = "0:" + "ee".repeat(32)
      val sender = "0:" + "99".repeat(32)

      val client =
        TonCenterClient(
          okHttpClient =
            mockHttpClient {
              val path = request().url.encodedPath
              when {
                // Put "metadata" BEFORE "jetton_wallets" to verify two-pass / deferred map joining
                path.endsWith("/api/v3/jetton/wallets") ->
                  jsonResponse(
                    200,
                    """
                    {
                      "metadata": {
                        "$notMasterRaw": {
                          "token_info": [
                            {
                              "type": "jetton_masters",
                              "symbol": "NOT",
                              "name": "Notcoin",
                              "image": "https://cdn.joincommunity.xyz/not.png",
                              "extra": {"decimals": "9"}
                            }
                          ]
                        },
                        "$scamMasterRaw": {
                          "token_info": [
                            {
                              "type": "jetton_masters",
                              "symbol": "GIFT",
                              "name": "Claim airdrop at claim-ton.xyz",
                              "extra": {"decimals": "9"}
                            }
                          ]
                        }
                      },
                      "jetton_wallets": [
                        {
                          "address": "$notWallet",
                          "owner": "${owner.raw}",
                          "jetton": "$notMasterRaw",
                          "balance": "15500000000"
                        },
                        {
                          "address": "$scamWallet",
                          "owner": "${owner.raw}",
                          "jetton": "$scamMasterRaw",
                          "balance": "1000000000"
                        }
                      ]
                    }
                    """.trimIndent(),
                  )
                // Put "metadata" AFTER "jetton_transfers"
                path.endsWith("/api/v3/jetton/transfers") ->
                  jsonResponse(
                    200,
                    """
                    {
                      "jetton_transfers": [
                        {
                          "transaction_hash": "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff",
                          "transaction_lt": "555001",
                          "transaction_now": 1715001000,
                          "source": "$sender",
                          "destination": "${owner.raw}",
                          "jetton_master": "$notMasterRaw",
                          "amount": "5250000000",
                          "tx_aborted": false,
                          "decoded_forward_payload": {"comment": "TC Notcoin"}
                        },
                        {
                          "transaction_hash": "ff112233445566778899aabbccddeeff00112233445566778899aabbccddeeff",
                          "transaction_lt": "555002",
                          "transaction_now": 1715001010,
                          "source": "$sender",
                          "destination": "${owner.raw}",
                          "jetton_master": "$scamMasterRaw",
                          "amount": "9000000000",
                          "tx_aborted": false
                        }
                      ],
                      "metadata": {
                        "$notMasterRaw": {
                          "token_info": [
                            {
                              "type": "jetton_masters",
                              "symbol": "NOT",
                              "name": "Notcoin",
                              "image": "ipfs://QmNotCenter/not.png",
                              "extra": {"decimals": 9}
                            }
                          ]
                        },
                        "$scamMasterRaw": {
                          "token_info": [
                            {
                              "type": "jetton_masters",
                              "symbol": "GIFT",
                              "name": "Claim airdrop at claim-ton.xyz",
                              "extra": {"decimals": 9}
                            }
                          ]
                        }
                      }
                    }
                    """.trimIndent(),
                  )
                else -> jsonResponse(404, """{}""")
              }
            }
        )

      val holdingsPage = client.fetchAllJettons(owner)
      assertEquals(1, holdingsPage.holdings.size)
      val notHolding = holdingsPage.holdings.first()
      assertEquals("NOT", notHolding.symbol)
      assertEquals("Notcoin", notHolding.name)
      assertEquals(9, notHolding.balance.decimals)
      assertEquals("15.50", notHolding.balance.format())
      assertEquals("https://cdn.joincommunity.xyz/not.png", notHolding.imageUrl)

      val history = client.fetchAllJettonHistory(owner, limit = 25)
      assertEquals(1, history.transfers.size)
      val transfer = history.transfers.first()
      assertEquals(TxDirection.IN, transfer.direction)
      assertEquals("NOT", transfer.jettonSymbol)
      assertEquals("5.25", transfer.amount.format())
      assertEquals("TC Notcoin", transfer.comment)
      assertEquals("https://ipfs.io/ipfs/QmNotCenter/not.png", transfer.jettonImageUrl)
      assertEquals(OnChainMessageStatus.CONFIRMED, transfer.status)
    }

  private fun mockHttpClient(handler: Interceptor.Chain.() -> Response): OkHttpClient =
    OkHttpClient.Builder()
      .addInterceptor(Interceptor { chain -> chain.handler() })
      .build()

  private fun Interceptor.Chain.jsonResponse(code: Int, body: String): Response =
    Response.Builder()
      .request(request())
      .protocol(Protocol.HTTP_1_1)
      .code(code)
      .message("HTTP $code")
      .body(body.toResponseBody("application/json".toMediaType()))
      .build()
}
