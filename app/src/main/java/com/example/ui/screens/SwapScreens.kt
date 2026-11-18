package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.model.BalanceState
import com.example.core.model.JettonVerificationStatus
import com.example.core.model.Money
import com.example.core.model.SlippageTolerance
import com.example.core.model.SwapAsset
import com.example.core.model.SwapPreview
import com.example.core.model.SwapQuote
import com.example.ui.components.BottomThumbActionBar
import com.example.ui.components.JettonAvatar
import com.example.ui.components.JettonVerificationInlineBadge
import com.example.ui.components.OFFICIAL_TON_LOGO_URL
import com.example.ui.components.SecureScreenEffect
import com.example.ui.components.TonOfficialLogo
import com.example.ui.components.VaultGlassCard
import com.example.ui.components.tactileClickable
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.TonkeeperAmber
import com.example.ui.theme.TonkeeperRed
import com.example.ui.theme.UsdtGreen
import com.example.ui.theme.VaultBlue
import com.example.ui.theme.VaultDarkCard
import com.example.ui.theme.VaultDarkCardElevated
import com.example.ui.theme.VaultHairlineBorder
import com.example.ui.theme.VaultTextSecondary
import com.gramvault.wallet.R
import java.math.BigInteger
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SwapFormScreen(
  balanceState: BalanceState,
  tokens: List<SwapAsset>,
  inputAsset: SwapAsset?,
  outputAsset: SwapAsset?,
  amountInput: String,
  slippage: SlippageTolerance,
  quote: SwapQuote?,
  isQuoting: Boolean,
  isBusy: Boolean,
  errorMessage: String?,
  onSelectInputAsset: (SwapAsset) -> Unit,
  onSelectOutputAsset: (SwapAsset) -> Unit,
  onFlipPair: () -> Unit,
  onAmountChange: (String) -> Unit,
  onSlippageChange: (SlippageTolerance) -> Unit,
  onRefreshQuote: () -> Unit,
  onReviewSwap: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(enabled = !isBusy, onBack = onBack)

  var pickerTarget by remember { mutableStateOf<SwapPickerTarget?>(null) }

  val inputBalance: Money? =
    remember(inputAsset, balanceState) { resolveBalanceForSwapAsset(inputAsset, balanceState) }
  val outputBalance: Money? =
    remember(outputAsset, balanceState) { resolveBalanceForSwapAsset(outputAsset, balanceState) }

  val canReview = !isBusy && !isQuoting && quote != null && inputAsset != null && outputAsset != null

  pickerTarget?.let { target ->
    SwapTokenPickerDialog(
      tokens = tokens,
      balanceState = balanceState,
      selectedAssetId = if (target == SwapPickerTarget.INPUT) inputAsset?.id else outputAsset?.id,
      onSelect = { asset ->
        if (target == SwapPickerTarget.INPUT) {
          onSelectInputAsset(asset)
        } else {
          onSelectOutputAsset(asset)
        }
        pickerTarget = null
      },
      onDismiss = { pickerTarget = null },
    )
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      SwapTopBar(
        title = stringResource(R.string.swap_title),
        backTag = "swap_back_button",
        onBack = onBack,
      )
    },
    bottomBar = {
      BottomThumbActionBar {
        Button(
          onClick = onReviewSwap,
          enabled = canReview,
          shape = RoundedCornerShape(18.dp),
          colors = ButtonDefaults.buttonColors(containerColor = VaultBlue),
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("swap_review_button"),
        ) {
          if (isBusy) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              strokeWidth = 2.dp,
              color = Color(0xFF001E30),
            )
          } else {
            Text(
              text = stringResource(R.string.swap_review_button),
              fontWeight = FontWeight.Bold,
              color = Color(0xFF001E30),
            )
          }
        }
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.widthIn(max = 560.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Pay card + Flip + Receive card stack
        Box(modifier = Modifier.fillMaxWidth()) {
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            // YOU PAY CARD
            VaultGlassCard(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(22.dp),
              containerColor = VaultDarkCard,
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = stringResource(R.string.swap_pay_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = VaultTextSecondary,
                  )
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    if (inputAsset != null && inputBalance != null) {
                      Text(
                        text =
                          stringResource(
                            R.string.swap_available_format,
                            inputBalance.format(),
                            inputAsset.symbol,
                          ),
                        style = MaterialTheme.typography.labelMedium,
                        color = VaultTextSecondary,
                        modifier = Modifier.testTag("swap_input_balance_text"),
                      )
                      if (inputBalance.units > BigInteger.ZERO) {
                        Surface(
                          shape = RoundedCornerShape(999.dp),
                          color = VaultBlue.copy(alpha = 0.16f),
                          modifier =
                            Modifier.clip(RoundedCornerShape(999.dp))
                              .tactileClickable {
                                val maxStr =
                                  if (inputAsset.isNative) {
                                    // Keep small buffer for network & router gas on native TON
                                    val buffer = BigInteger.valueOf(350_000_000L)
                                    val spendable =
                                      if (inputBalance.units > buffer) inputBalance.units - buffer
                                      else BigInteger.ZERO
                                    Money(spendable, inputAsset.decimals)
                                      .format(minFractionDigits = 0, trimTrailingZeros = true)
                                  } else {
                                    inputBalance.format(
                                      minFractionDigits = 0,
                                      trimTrailingZeros = true,
                                    )
                                  }
                                onAmountChange(maxStr)
                              }
                              .minimumInteractiveComponentSize()
                              .testTag("swap_max_button"),
                        ) {
                          Text(
                            text = stringResource(R.string.swap_max_button),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = VaultBlue,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                          )
                        }
                      }
                    }
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  SwapTokenSelectorPill(
                    asset = inputAsset,
                    tag = "swap_select_input_token",
                    onClick = { pickerTarget = SwapPickerTarget.INPUT },
                  )
                  OutlinedTextField(
                    value = amountInput,
                    onValueChange = onAmountChange,
                    placeholder = {
                      Text(
                        text = "0.0",
                        style = MaterialTheme.typography.titleLarge,
                        color = VaultTextSecondary.copy(alpha = 0.5f),
                      )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle =
                      MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = JetBrainsMonoFontFamily,
                      ),
                    shape = RoundedCornerShape(14.dp),
                    colors =
                      OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VaultBlue.copy(alpha = 0.7f),
                        unfocusedBorderColor = VaultHairlineBorder,
                        focusedContainerColor = VaultDarkCardElevated,
                        unfocusedContainerColor = VaultDarkCardElevated,
                      ),
                    modifier = Modifier.weight(1f).testTag("swap_amount_input"),
                  )
                }

                quote?.inputUsd?.let { usd ->
                  Text(
                    text = String.format(Locale.US, "≈ $%.2f USD", usd),
                    style = MaterialTheme.typography.labelMedium,
                    color = VaultTextSecondary,
                  )
                }
              }
            }

            // YOU RECEIVE CARD
            VaultGlassCard(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(22.dp),
              containerColor = VaultDarkCard,
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = stringResource(R.string.swap_receive_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = VaultTextSecondary,
                  )
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    if (outputAsset != null && outputBalance != null) {
                      Text(
                        text =
                          stringResource(
                            R.string.swap_available_format,
                            outputBalance.format(),
                            outputAsset.symbol,
                          ),
                        style = MaterialTheme.typography.labelMedium,
                        color = VaultTextSecondary,
                      )
                    }
                    if (amountInput.isNotBlank()) {
                      IconButton(
                        onClick = onRefreshQuote,
                        enabled = !isQuoting && !isBusy,
                        modifier = Modifier.size(36.dp).testTag("swap_refresh_quote_button"),
                      ) {
                        Icon(
                          imageVector = Icons.Filled.Refresh,
                          contentDescription = stringResource(R.string.action_refresh_balance),
                          tint = VaultBlue,
                          modifier = Modifier.size(18.dp),
                        )
                      }
                    }
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  SwapTokenSelectorPill(
                    asset = outputAsset,
                    tag = "swap_select_output_token",
                    onClick = { pickerTarget = SwapPickerTarget.OUTPUT },
                  )
                  Box(
                    modifier =
                      Modifier.weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(VaultDarkCardElevated)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterEnd,
                  ) {
                    if (isQuoting) {
                      CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = VaultBlue,
                      )
                    } else {
                      Text(
                        text =
                          quote?.expectedOutputAmount?.format(
                            minFractionDigits = 0,
                            trimTrailingZeros = true,
                          ) ?: "0.0",
                        style =
                          MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = JetBrainsMonoFontFamily,
                          ),
                        color =
                          if (quote != null) UsdtGreen else VaultTextSecondary.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("swap_output_amount"),
                      )
                    }
                  }
                }

                quote?.outputUsd?.let { usd ->
                  Text(
                    text = String.format(Locale.US, "≈ $%.2f USD", usd),
                    style = MaterialTheme.typography.labelMedium,
                    color = VaultTextSecondary,
                  )
                }
              }
            }
          }

          // Floating centered swap direction flip button
          Surface(
            shape = CircleShape,
            color = VaultDarkCardElevated,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.background),
            shadowElevation = 4.dp,
            modifier =
              Modifier.align(Alignment.Center)
                .size(48.dp)
                .clip(CircleShape)
                .tactileClickable(enabled = !isBusy, onClick = onFlipPair)
                .testTag("swap_flip_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Filled.SwapVert,
                contentDescription = stringResource(R.string.swap_flip_cd),
                tint = VaultBlue,
                modifier = Modifier.size(24.dp),
              )
            }
          }
        }

        // Slippage Tolerance selector
        VaultGlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(18.dp),
          containerColor = VaultDarkCard,
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = stringResource(R.string.swap_slippage_label),
              style = MaterialTheme.typography.labelLarge,
              color = VaultTextSecondary,
            )
            FlowRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              SlippageTolerance.entries.forEach { option ->
                val selected = option == slippage
                FilterChip(
                  selected = selected,
                  onClick = { onSlippageChange(option) },
                  label = {
                    Text(
                      text = option.label,
                      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                  },
                  colors =
                    FilterChipDefaults.filterChipColors(
                      selectedContainerColor = VaultBlue.copy(alpha = 0.2f),
                      selectedLabelColor = VaultBlue,
                    ),
                  modifier = Modifier.testTag("swap_slippage_${option.bps}"),
                )
              }
            }
          }
        }

        // Quote breakdown card
        AnimatedVisibility(
          visible = isQuoting || quote != null,
          enter = fadeIn() + expandVertically(),
          exit = fadeOut() + shrinkVertically(),
        ) {
          VaultGlassCard(
            modifier = Modifier.fillMaxWidth().testTag("swap_quote_details_card"),
            shape = RoundedCornerShape(18.dp),
            containerColor = VaultDarkCard,
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              if (isQuoting) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = VaultBlue,
                  )
                  Text(
                    text = stringResource(R.string.swap_fetching_quote),
                    style = MaterialTheme.typography.bodySmall,
                    color = VaultTextSecondary,
                  )
                }
              } else if (quote != null) {
                SwapDetailRow(
                  label = stringResource(R.string.swap_rate_label),
                  value = quote.formatExchangeRate(),
                )
                SwapDetailRow(
                  label = stringResource(R.string.swap_min_received_label),
                  value = "${quote.minReceivedAmount.format()} ${quote.outputAsset.symbol}",
                )
                SwapDetailRow(
                  label = stringResource(R.string.swap_route_label),
                  value =
                    quote.routeDexLabels.joinToString(" → ").ifEmpty {
                      stringResource(R.string.swap_route_aggregator)
                    },
                )
                quote.priceImpactPercent?.let { impact ->
                  val impactText = String.format(Locale.US, "%.2f%%", impact)
                  val highImpact = kotlin.math.abs(impact) >= 3.0
                  SwapDetailRow(
                    label = stringResource(R.string.swap_price_impact_label),
                    value = impactText,
                    valueColor = if (highImpact) TonkeeperAmber else MaterialTheme.colorScheme.onSurface,
                  )
                  if (highImpact) {
                    Surface(
                      shape = RoundedCornerShape(12.dp),
                      color = Color(0xFF332612),
                      border = BorderStroke(0.8.dp, TonkeeperAmber.copy(alpha = 0.4f)),
                      modifier = Modifier.fillMaxWidth().testTag("swap_high_impact_warning"),
                    ) {
                      Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Icon(
                          imageVector = Icons.Outlined.WarningAmber,
                          contentDescription = null,
                          tint = TonkeeperAmber,
                          modifier = Modifier.size(16.dp),
                        )
                        Text(
                          text = stringResource(R.string.swap_price_impact_high_warning, impactText),
                          style = MaterialTheme.typography.labelSmall,
                          color = Color(0xFFFFDF9E),
                        )
                      }
                    }
                  }
                }
                SwapDetailRow(
                  label = stringResource(R.string.swap_gas_label),
                  value = "~${quote.recommendedGas.format()} GRAM",
                )
                Text(
                  text = stringResource(R.string.swap_gas_note),
                  style = MaterialTheme.typography.labelSmall,
                  color = VaultTextSecondary,
                )
              }
            }
          }
        }

        // Error card
        errorMessage?.let { err ->
          Card(
            modifier = Modifier.fillMaxWidth().testTag("swap_error_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
      }
    }
  }
}

@Composable
fun SwapConfirmScreen(
  preview: SwapPreview,
  isBusy: Boolean,
  errorMessage: String?,
  clockSkewSeconds: Long = 0L,
  onConfirmAndSwap: () -> Unit,
  onBack: () -> Unit,
) {
  SecureScreenEffect()
  BackHandler(enabled = !isBusy, onBack = onBack)

  val quote = preview.quote
  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      SwapTopBar(
        title = stringResource(R.string.swap_confirm_title),
        backTag = "swap_confirm_back_button",
        onBack = { if (!isBusy) onBack() },
      )
    },
    bottomBar = {
      BottomThumbActionBar {
        Button(
          onClick = onConfirmAndSwap,
          enabled = !isBusy,
          shape = RoundedCornerShape(18.dp),
          colors = ButtonDefaults.buttonColors(containerColor = VaultBlue),
          modifier = Modifier.fillMaxWidth().height(56.dp).testTag("swap_confirm_button"),
        ) {
          if (isBusy) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              strokeWidth = 2.dp,
              color = Color(0xFF001E30),
            )
          } else {
            Text(
              text = stringResource(R.string.swap_confirm_button),
              fontWeight = FontWeight.Bold,
              color = Color(0xFF001E30),
            )
          }
        }
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.widthIn(max = 560.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Pair summary card
        VaultGlassCard(
          modifier = Modifier.fillMaxWidth().testTag("swap_confirm_pair_card"),
          shape = RoundedCornerShape(22.dp),
          containerColor = VaultDarkCard,
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            SwapConfirmAssetRow(
              label = stringResource(R.string.swap_pay_label),
              asset = quote.inputAsset,
              amountText = "${quote.inputAmount.format()} ${quote.inputAsset.symbol}",
              amountColor = MaterialTheme.colorScheme.onSurface,
            )
            Box(
              modifier =
                Modifier.size(34.dp).clip(CircleShape).background(VaultDarkCardElevated),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.ArrowDownward,
                contentDescription = null,
                tint = VaultBlue,
                modifier = Modifier.size(18.dp),
              )
            }
            SwapConfirmAssetRow(
              label = stringResource(R.string.swap_receive_label),
              asset = quote.outputAsset,
              amountText = "~${quote.expectedOutputAmount.format()} ${quote.outputAsset.symbol}",
              amountColor = UsdtGreen,
            )
          }
        }

        // Execution details card
        VaultGlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(22.dp),
          containerColor = VaultDarkCard,
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            SwapDetailRow(
              label = stringResource(R.string.swap_rate_label),
              value = quote.formatExchangeRate(),
            )
            SwapDetailRow(
              label = stringResource(R.string.swap_route_label),
              value = preview.routeSummary,
            )
            SwapDetailRow(
              label = stringResource(R.string.swap_slippage_label),
              value = quote.slippage.label,
            )
            SwapDetailRow(
              label = stringResource(R.string.swap_min_received_label),
              value = "${quote.minReceivedAmount.format()} ${quote.outputAsset.symbol}",
              highlight = true,
            )
            quote.priceImpactPercent?.let { impact ->
              val impactText = String.format(Locale.US, "%.2f%%", impact)
              val highImpact = kotlin.math.abs(impact) >= 3.0
              SwapDetailRow(
                label = stringResource(R.string.swap_price_impact_label),
                value = impactText,
                valueColor = if (highImpact) TonkeeperAmber else MaterialTheme.colorScheme.onSurface,
              )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            SwapDetailRow(
              label = stringResource(R.string.swap_gas_label),
              value = "${preview.totalAttachedTon.format()} GRAM",
            )
            SwapDetailRow(
              label = stringResource(R.string.send_confirm_fee_label),
              value = "~${preview.estimatedNetworkFee.format()} GRAM",
            )
            SwapDetailRow(
              label = stringResource(R.string.send_confirm_total_label),
              value = "${preview.totalTonRequired.format()} GRAM",
              highlight = true,
            )
            SwapDetailRow(
              label = stringResource(R.string.swap_messages_count_label),
              value =
                stringResource(R.string.swap_messages_count_value, preview.internalMessages.size),
            )
            SwapDetailRow(
              label = stringResource(R.string.send_confirm_seqno_label),
              value = "#${preview.seqno}",
            )
            SwapDetailRow(
              label = stringResource(R.string.send_confirm_ttl_label),
              value = stringResource(R.string.send_confirm_ttl_value),
            )
            Text(
              text = stringResource(R.string.swap_gas_note),
              style = MaterialTheme.typography.labelSmall,
              color = VaultTextSecondary,
            )
            if (preview.feeIsEstimateFallback) {
              Text(
                text = stringResource(R.string.send_confirm_fee_fallback),
                style = MaterialTheme.typography.labelSmall,
                color = VaultTextSecondary,
              )
            }
          }
        }

        if (clockSkewSeconds > 0L) {
          val formattedSkew =
            remember(clockSkewSeconds) {
              when {
                clockSkewSeconds >= 3600L -> "${clockSkewSeconds / 3600L}h"
                clockSkewSeconds >= 60L -> "${clockSkewSeconds / 60L}m"
                else -> "${clockSkewSeconds}s"
              }
            }
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF332612),
            border = BorderStroke(1.dp, Color(0xFFFFE082).copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth().testTag("swap_clock_skew_warning"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFFFE082),
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = stringResource(R.string.send_clock_skew_warning, formattedSkew),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFE082),
              )
            }
          }
        }

        errorMessage?.let { err ->
          Card(
            modifier = Modifier.fillMaxWidth().testTag("swap_confirm_error"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }
    }
  }
}

private enum class SwapPickerTarget {
  INPUT,
  OUTPUT,
}

@Composable
private fun SwapTokenSelectorPill(
  asset: SwapAsset?,
  tag: String,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = VaultDarkCardElevated,
    border = BorderStroke(0.8.dp, VaultHairlineBorder),
    modifier =
      Modifier.clip(RoundedCornerShape(14.dp))
        .tactileClickable(onClick = onClick)
        .minimumInteractiveComponentSize()
        .testTag(tag),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (asset != null) {
        SwapAssetIcon(asset = asset, size = 28.dp)
        Text(
          text = asset.symbol,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
        )
      } else {
        Text(
          text = stringResource(R.string.swap_select_token),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
        )
      }
      Icon(
        imageVector = Icons.Filled.KeyboardArrowDown,
        contentDescription = stringResource(R.string.swap_select_token),
        tint = VaultTextSecondary,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Composable
private fun SwapAssetIcon(asset: SwapAsset, size: androidx.compose.ui.unit.Dp) {
  if (asset.isNative) {
    TonOfficialLogo(
      imageUrl = asset.imageUrl ?: OFFICIAL_TON_LOGO_URL,
      size = size,
      contentDescription = asset.symbol,
    )
  } else {
    JettonAvatar(
      symbol = asset.symbol,
      imageUrl = asset.imageUrl,
      isUsdtAllowlisted =
        asset.verification == JettonVerificationStatus.ALLOWLISTED &&
          asset.symbol.equals("USDT", ignoreCase = true),
      masterRaw = asset.rawAddress,
      size = size,
    )
  }
}

@Composable
private fun SwapTokenPickerDialog(
  tokens: List<SwapAsset>,
  balanceState: BalanceState,
  selectedAssetId: String?,
  onSelect: (SwapAsset) -> Unit,
  onDismiss: () -> Unit,
) {
  var searchQuery by remember { mutableStateOf("") }
  val filtered =
    remember(tokens, searchQuery) {
      val q = searchQuery.trim().lowercase()
      if (q.isEmpty()) tokens
      else tokens.filter {
        it.symbol.lowercase().contains(q) || it.name.lowercase().contains(q)
      }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = VaultDarkCard,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.swap_select_token),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
          Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.pin_cancel_button),
            tint = VaultTextSecondary,
          )
        }
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text(stringResource(R.string.swap_search_tokens_hint)) },
          leadingIcon = {
            Icon(
              imageVector = Icons.Filled.Search,
              contentDescription = null,
              tint = VaultTextSecondary,
            )
          },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("swap_token_search_input"),
        )

        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(filtered, key = { it.id }) { asset ->
            val bal = remember(asset, balanceState) { resolveBalanceForSwapAsset(asset, balanceState) }
            val isSelected = selectedAssetId != null && selectedAssetId.equals(asset.id, ignoreCase = true)
            Surface(
              shape = RoundedCornerShape(14.dp),
              color =
                if (isSelected) VaultBlue.copy(alpha = 0.14f) else VaultDarkCardElevated,
              border =
                if (isSelected) BorderStroke(1.dp, VaultBlue.copy(alpha = 0.45f))
                else BorderStroke(0.5.dp, VaultHairlineBorder),
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .tactileClickable { onSelect(asset) }
                  .testTag("swap_token_item_${asset.symbol.lowercase()}"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                SwapAssetIcon(asset = asset, size = 34.dp)
                Column(modifier = Modifier.weight(1f)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                  ) {
                    Text(
                      text = asset.symbol,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.Bold,
                    )
                    if (!asset.isNative) {
                      JettonVerificationInlineBadge(verification = asset.verification)
                    }
                  }
                  Text(
                    text = asset.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = VaultTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                if (bal != null && bal.units > BigInteger.ZERO) {
                  Text(
                    text = bal.format(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {},
  )
}

@Composable
private fun SwapConfirmAssetRow(
  label: String,
  asset: SwapAsset,
  amountText: String,
  amountColor: Color,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    SwapAssetIcon(asset = asset, size = 40.dp)
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = VaultTextSecondary,
      )
      Text(
        text = asset.name,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Text(
      text = amountText,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = amountColor,
    )
  }
}

@Composable
private fun SwapDetailRow(
  label: String,
  value: String,
  highlight: Boolean = false,
  valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = VaultTextSecondary,
    )
    Text(
      text = value,
      style = if (highlight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
      fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold,
      color = valueColor,
    )
  }
}

@Composable
private fun SwapTopBar(
  title: String,
  backTag: String,
  onBack: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack, modifier = Modifier.testTag(backTag)) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(R.string.swap_back_cd),
        )
      }
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f),
      )
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = VaultBlue.copy(alpha = 0.16f),
        modifier = Modifier.padding(end = 8.dp),
      ) {
        Text(
          text = stringResource(R.string.network_mainnet),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = VaultBlue,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
      }
    }
  }
}

private fun resolveBalanceForSwapAsset(asset: SwapAsset?, balanceState: BalanceState): Money? {
  asset ?: return null
  if (asset.isNative) return balanceState.gramBalance
  val raw = asset.rawAddress
  val matched =
    balanceState.jettonHoldings.firstOrNull { holding ->
      (raw != null && holding.masterRaw.equals(raw, ignoreCase = true)) ||
        holding.symbol.equals(asset.symbol, ignoreCase = true)
    }
  if (matched != null) return matched.balance
  if (asset.symbol.equals("USDT", ignoreCase = true)) return balanceState.usdtBalance
  return Money.zero(asset.decimals)
}
