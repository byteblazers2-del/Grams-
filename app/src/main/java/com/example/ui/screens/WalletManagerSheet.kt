package com.example.ui.screens

import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.model.Network
import com.example.core.model.VaultMetadata
import com.example.core.wallet.AddressCheckResult
import com.example.ui.components.tactileClickable
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.UsdtGreen
import com.example.ui.theme.VaultBlue
import com.example.ui.theme.VaultDarkCard
import com.example.ui.theme.VaultDarkCardElevated
import com.example.ui.theme.VaultHairlineBorder
import com.gramvault.wallet.R

private enum class WalletSheetStep {
  LIST,
  ADD_CHOOSE_TYPE,
  ADD_WATCH_ONLY,
}

@Composable
fun WalletManagerSheetContent(
  wallets: List<VaultMetadata>,
  activeWalletId: String,
  activeNetwork: Network,
  isBusy: Boolean = false,
  errorMessage: String? = null,
  onClearError: () -> Unit = {},
  onSwitchWallet: (String) -> Unit,
  onCreatePersonalWallet: () -> Unit,
  onImportPersonalWallet: () -> Unit,
  onAddWatchOnlyWallet: (addressInput: String, customName: String) -> Unit,
  onRenameWallet: (walletId: String, newName: String) -> Unit,
  onDeleteWallet: (walletId: String) -> Unit,
  onValidateInput: (String) -> AddressCheckResult,
) {
  var step by remember { mutableStateOf(WalletSheetStep.LIST) }
  var showPersonalActions by remember { mutableStateOf(false) }
  var watchAddressInput by remember { mutableStateOf("") }
  var watchNameInput by remember { mutableStateOf("") }
  var walletPendingRename by remember { mutableStateOf<VaultMetadata?>(null) }
  var renameDraft by remember { mutableStateOf("") }
  var walletPendingDelete by remember { mutableStateOf<VaultMetadata?>(null) }

  val context = LocalContext.current

  walletPendingRename?.let { target ->
    AlertDialog(
      onDismissRequest = { walletPendingRename = null },
      containerColor = MaterialTheme.colorScheme.surface,
      title = { Text(text = stringResource(R.string.wallet_rename_title), fontWeight = FontWeight.Bold) },
      text = {
        OutlinedTextField(
          value = renameDraft,
          onValueChange = { if (it.length <= 32) renameDraft = it },
          label = { Text(stringResource(R.string.wallet_rename_label)) },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("wallet_rename_input"),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onRenameWallet(target.walletId, renameDraft)
            walletPendingRename = null
          },
          enabled = renameDraft.trim().isNotEmpty(),
          modifier = Modifier.testTag("wallet_rename_save_button"),
        ) {
          Text(stringResource(R.string.wallet_rename_save))
        }
      },
      dismissButton = {
        TextButton(onClick = { walletPendingRename = null }) {
          Text(stringResource(R.string.pin_cancel_button))
        }
      },
    )
  }

  walletPendingDelete?.let { target ->
    AlertDialog(
      onDismissRequest = { walletPendingDelete = null },
      containerColor = MaterialTheme.colorScheme.surface,
      title = { Text(text = stringResource(R.string.wallet_delete_title), fontWeight = FontWeight.Bold) },
      text = {
        Text(
          text =
            if (target.isWatchOnly) {
              stringResource(R.string.wallet_delete_body_watch_only, target.walletName)
            } else {
              stringResource(R.string.wallet_delete_body_personal, target.walletName)
            },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onDeleteWallet(target.walletId)
            walletPendingDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("wallet_delete_confirm_button"),
        ) {
          Text(stringResource(R.string.wallet_delete_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { walletPendingDelete = null }) {
          Text(stringResource(R.string.pin_cancel_button))
        }
      },
    )
  }

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    when (step) {
      WalletSheetStep.LIST -> {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.wallet_manager_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Button(
            onClick = {
              onClearError()
              showPersonalActions = false
              step = WalletSheetStep.ADD_CHOOSE_TYPE
            },
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VaultBlue, contentColor = Color(0xFF001E30)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("wallet_manager_add_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Add,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.wallet_manager_add_button),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          wallets.forEach { wallet ->
            val isActive = wallet.walletId == activeWalletId
            val friendlyAddress =
              remember(wallet.walletId, wallet.mainnetRaw, activeNetwork) {
                wallet.addressFor(activeNetwork).toFriendly(bounceable = false)
              }
            val accentColor = if (wallet.isWatchOnly) Color(0xFFFFCA28) else VaultBlue

            Surface(
              shape = RoundedCornerShape(18.dp),
              color = if (isActive) VaultDarkCardElevated else VaultDarkCard,
              border =
                BorderStroke(
                  width = if (isActive) 1.5.dp else 1.dp,
                  color = if (isActive) VaultBlue else VaultHairlineBorder,
                ),
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(18.dp))
                  .tactileClickable(scaleOnPress = 0.985f) { onSwitchWallet(wallet.walletId) }
                  .testTag("wallet_item_${wallet.walletId}"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Box(
                  modifier =
                    Modifier.size(40.dp)
                      .clip(CircleShape)
                      .background(accentColor.copy(alpha = 0.16f)),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector =
                      if (wallet.isWatchOnly) Icons.Outlined.Visibility
                      else Icons.Outlined.AccountBalanceWallet,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp),
                  )
                }

                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Text(
                      text = wallet.walletName,
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isActive) {
                      Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = UsdtGreen,
                        modifier = Modifier.size(16.dp),
                      )
                    }
                  }
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Surface(
                      shape = RoundedCornerShape(999.dp),
                      color = accentColor.copy(alpha = 0.14f),
                    ) {
                      Text(
                        text =
                          if (wallet.isWatchOnly) {
                            stringResource(R.string.wallet_type_watch_only)
                          } else {
                            stringResource(R.string.wallet_type_personal, wallet.walletVersion.displayName)
                          },
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                      )
                    }
                    Text(
                      text = compactAddress(friendlyAddress),
                      style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                    )
                  }
                }

                IconButton(
                  onClick = {
                    renameDraft = wallet.walletName
                    walletPendingRename = wallet
                  },
                  modifier = Modifier.testTag("wallet_edit_${wallet.walletId}"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.wallet_edit_button_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                  )
                }

                IconButton(
                  onClick = { walletPendingDelete = wallet },
                  modifier = Modifier.testTag("wallet_delete_${wallet.walletId}"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.wallet_delete_button_cd),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(19.dp),
                  )
                }
              }
            }
          }
        }
      }

      WalletSheetStep.ADD_CHOOSE_TYPE -> {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          IconButton(
            onClick = {
              onClearError()
              step = WalletSheetStep.LIST
            },
            modifier = Modifier.testTag("wallet_manager_back_button"),
          ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
          Text(
            text = stringResource(R.string.add_wallet_choose_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
        }

        Text(
          text = stringResource(R.string.add_wallet_choose_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
          shape = RoundedCornerShape(18.dp),
          color = VaultDarkCardElevated,
          border =
            BorderStroke(
              width = if (showPersonalActions) 1.5.dp else 1.dp,
              color = if (showPersonalActions) VaultBlue else VaultHairlineBorder,
            ),
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .tactileClickable(scaleOnPress = 0.985f) { showPersonalActions = !showPersonalActions }
              .testTag("add_personal_wallet_option"),
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(42.dp)
                    .clip(CircleShape)
                    .background(VaultBlue.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Outlined.AccountBalanceWallet,
                  contentDescription = null,
                  tint = VaultBlue,
                )
              }
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = stringResource(R.string.add_wallet_personal_title),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = stringResource(R.string.add_wallet_personal_subtitle),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            AnimatedVisibility(visible = showPersonalActions) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                  onClick = onCreatePersonalWallet,
                  enabled = !isBusy,
                  shape = RoundedCornerShape(14.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = VaultBlue, contentColor = Color(0xFF001E30)),
                  modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_personal_create_button"),
                ) {
                  Icon(imageVector = Icons.Filled.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = stringResource(R.string.add_wallet_personal_create),
                    fontWeight = FontWeight.Bold,
                  )
                }
                OutlinedButton(
                  onClick = onImportPersonalWallet,
                  enabled = !isBusy,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_personal_import_button"),
                ) {
                  Icon(imageVector = Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = stringResource(R.string.add_wallet_personal_import),
                    fontWeight = FontWeight.SemiBold,
                  )
                }
              }
            }
          }
        }

        Surface(
          shape = RoundedCornerShape(18.dp),
          color = VaultDarkCardElevated,
          border = BorderStroke(1.dp, VaultHairlineBorder),
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .tactileClickable(scaleOnPress = 0.985f) {
                onClearError()
                step = WalletSheetStep.ADD_WATCH_ONLY
              }
              .testTag("add_watch_only_option"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Box(
              modifier =
                Modifier.size(42.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFFFCA28).copy(alpha = 0.16f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Outlined.Visibility,
                contentDescription = null,
                tint = Color(0xFFFFCA28),
              )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = stringResource(R.string.add_wallet_watch_only_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = stringResource(R.string.add_wallet_watch_only_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      WalletSheetStep.ADD_WATCH_ONLY -> {
        val addressCheck =
          remember(watchAddressInput, activeNetwork) {
            if (watchAddressInput.isBlank()) null else onValidateInput(watchAddressInput)
          }
        val canSubmit = addressCheck is AddressCheckResult.Valid && !isBusy

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          IconButton(
            onClick = {
              onClearError()
              step = WalletSheetStep.ADD_CHOOSE_TYPE
            },
            modifier = Modifier.testTag("wallet_manager_back_button"),
          ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
          Text(
            text = stringResource(R.string.watch_only_add_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
        }

        Surface(
          shape = RoundedCornerShape(14.dp),
          color = Color(0xFF332612),
          border = BorderStroke(1.dp, Color(0x44FFCA28)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Outlined.Visibility,
              contentDescription = null,
              tint = Color(0xFFFFCA28),
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = stringResource(R.string.watch_only_add_notice),
              style = MaterialTheme.typography.bodySmall,
              color = Color(0xFFFFE082),
            )
          }
        }

        OutlinedTextField(
          value = watchAddressInput,
          onValueChange = {
            watchAddressInput = it
            if (errorMessage != null) onClearError()
          },
          label = { Text(stringResource(R.string.watch_only_address_label)) },
          placeholder = { Text(stringResource(R.string.watch_only_address_hint)) },
          trailingIcon = {
            IconButton(
              onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                val clip = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                if (clip.isNotBlank()) {
                  watchAddressInput = clip.trim()
                  if (errorMessage != null) onClearError()
                }
              }
            ) {
              Icon(
                imageVector = Icons.Filled.ContentPaste,
                contentDescription = stringResource(R.string.action_paste),
                tint = VaultBlue,
              )
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          isError = addressCheck is AddressCheckResult.Invalid,
          supportingText = {
            when (addressCheck) {
              is AddressCheckResult.Invalid -> {
                Text(text = addressCheck.reason, color = MaterialTheme.colorScheme.error)
              }
              is AddressCheckResult.Valid -> {
                Text(
                  text = addressCheck.parsedAddress.address.toFriendly(addressCheck.parsedAddress.isBounceable),
                  color = UsdtGreen,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              null -> Unit
            }
          },
          modifier = Modifier.fillMaxWidth().testTag("watch_only_address_input"),
        )

        OutlinedTextField(
          value = watchNameInput,
          onValueChange = { if (it.length <= 32) watchNameInput = it },
          label = { Text(stringResource(R.string.watch_only_name_label)) },
          placeholder = { Text(stringResource(R.string.watch_only_name_hint)) },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("watch_only_name_input"),
        )

        if (!errorMessage.isNullOrBlank()) {
          Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("watch_only_error_text"),
          )
        }

        Button(
          onClick = { onAddWatchOnlyWallet(watchAddressInput, watchNameInput) },
          enabled = canSubmit,
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(containerColor = VaultBlue, contentColor = Color(0xFF001E30)),
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("watch_only_submit_button"),
        ) {
          if (isBusy) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = Color(0xFF001E30),
            )
            Spacer(modifier = Modifier.width(8.dp))
          }
          Text(
            text = stringResource(R.string.watch_only_submit_button),
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))
  }
}

private fun compactAddress(address: String): String {
  if (address.length <= 14) return address
  return "${address.take(6)}...${address.takeLast(5)}"
}
