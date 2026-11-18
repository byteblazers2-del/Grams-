package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.keystore.KeySecurityLevel
import com.example.core.model.BalanceState
import com.example.core.model.ChainMonitorState
import com.example.core.model.JettonAllowList
import com.example.core.model.JettonHolding
import com.example.core.model.JettonVerificationStatus
import com.example.core.model.Money
import com.example.core.model.Network
import com.example.core.model.TonAddress
import com.example.core.model.TxAssetKind
import com.example.core.model.TxDirection
import com.example.core.model.TxRecord
import com.example.core.model.TxStatus
import com.example.core.model.VaultMetadata
import com.example.core.network.FiatRates
import com.example.core.wallet.AddressCheckResult
import com.example.core.wallet.AppSettings
import com.example.core.wallet.TonTransferUri
import com.example.ui.components.AnimatedBalanceText
import com.example.ui.components.IosSheetHost
import com.example.ui.components.JettonAvatar
import com.example.ui.components.JettonVerificationInlineBadge
import com.example.ui.components.SecureClipboard
import com.example.ui.components.TonOfficialLogo
import com.example.ui.components.TonkeeperActionCircle
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class HomeSheetKind {
  RECEIVE,
  BUY,
  SETTINGS,
  WALLET_MANAGER,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletHomeScreen(
  metadata: VaultMetadata,
  activeNetwork: Network = Network.MAINNET,
  bannerMessage: String?,
  onClearBanner: () -> Unit,
  onOpenBackup: () -> Unit,
  onLockWallet: () -> Unit,
  balanceState: BalanceState = BalanceState(),
  onRefreshBalance: () -> Unit = {},
  onBuildTransferUri: (amount: Money?, comment: String?) -> String? = { amount, comment ->
    TonTransferUri.build(
      recipient = metadata.addressFor(activeNetwork),
      bounceable = false,
      amount = amount,
      comment = comment,
    )
  },
  onOpenSend: () -> Unit = {},
  onOpenSwap: () -> Unit = {},
  onOpenAssetSend: (TxAssetKind) -> Unit = { onOpenSend() },
  onOpenJettonSend: (String) -> Unit = { onOpenAssetSend(TxAssetKind.JETTON) },
  txRecords: List<TxRecord> = emptyList(),
  onOpenTxStatus: (String) -> Unit = {},
  monitorState: ChainMonitorState = ChainMonitorState(),
  onToggleMonitor: () -> Unit = {},
  onSyncNow: () -> Unit = {},
  onOpenHistory: () -> Unit = {},
  fiatRates: FiatRates = FiatRates(),
  appSettings: AppSettings = AppSettings(autoLockOnBackground = true, liveMonitorEnabled = true),
  onOpenAddressBook: () -> Unit = {},
  onToggleAutoLock: (Boolean) -> Unit = {},
  onToggleLiveMonitor: (Boolean) -> Unit = { onToggleMonitor() },
  keySecurityLevel: KeySecurityLevel = KeySecurityLevel.UNKNOWN,
  isDeviceSecure: Boolean = true,
  onSetOrChangePin: () -> Unit = {},
  onSwitchToDeviceBiometrics: () -> Unit = {},
  wallets: List<VaultMetadata> = listOf(metadata),
  onSwitchWallet: (String, () -> Unit) -> Unit = { _, _ -> },
  onAddWatchOnlyWallet: (addressInput: String, customName: String, onSuccess: () -> Unit) -> Unit = { _, _, _ -> },
  onCreateAdditionalWallet: () -> Unit = {},
  onImportAdditionalWallet: () -> Unit = {},
  onRenameWallet: (walletId: String, newName: String) -> Unit = { _, _ -> },
  onDeleteWallet: (walletId: String) -> Unit = {},
  walletManagerError: String? = null,
  onClearWalletManagerError: () -> Unit = {},
  isWalletActionBusy: Boolean = false,
  onValidateReceiveInput: (String) -> AddressCheckResult = { input ->
    val trimmed = input.trim()
    if (trimmed.startsWith("ton://", ignoreCase = true)) {
      TonTransferUri.parse(trimmed, activeNetwork).fold(
        onSuccess = { AddressCheckResult.Valid(it.parsedRecipient, it.amount, it.comment) },
        onFailure = { AddressCheckResult.Invalid(it.message ?: "Invalid TON transfer link") },
      )
    } else {
      TonAddress.parseDetailed(trimmed, activeNetwork).fold(
        onSuccess = { AddressCheckResult.Valid(it) },
        onFailure = { AddressCheckResult.Invalid(it.message ?: "Invalid TON address") },
      )
    }
  },
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val copiedText = stringResource(R.string.address_copied)
  var activeSheet by remember { mutableStateOf<HomeSheetKind?>(null) }
  var lastSheetKind by remember { mutableStateOf(HomeSheetKind.RECEIVE) }
  val pullToRefreshState = rememberPullToRefreshState()

  val tonAddress = metadata.addressFor(activeNetwork)
  val friendlyAddress = tonAddress.toFriendly(bounceable = false)
  val rawAddress = tonAddress.raw

  var enterPlayed by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { enterPlayed = true }
  val contentEntryProgress by
    animateFloatAsState(
      targetValue = if (enterPlayed) 1f else 0.94f,
      animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
      label = "homeContentEntry",
    )

  LaunchedEffect(bannerMessage) {
    if (!bannerMessage.isNullOrBlank()) {
      snackbarHostState.showSnackbar(bannerMessage)
      onClearBanner()
    }
  }

  val copyAddress: (String) -> Unit = { address ->
    SecureClipboard.copyText(context, label = "TON Address", text = address, isSensitive = false)
    scope.launch { snackbarHostState.showSnackbar(copiedText) }
  }

  IosSheetHost(
    visible = activeSheet != null,
    onDismiss = { activeSheet = null },
    stageColor = Color.Black,
    sheetContainerColor = MaterialTheme.colorScheme.surface,
    sheetContent = {
      when (lastSheetKind) {
        HomeSheetKind.RECEIVE -> {
          ReceiveSheetContent(
            network = activeNetwork,
            friendlyAddress = friendlyAddress,
            rawAddress = rawAddress,
            onCopyAddress = { label, value, toastMsg ->
              SecureClipboard.copyText(context, label = label, text = value, isSensitive = false)
              activeSheet = null
              scope.launch { snackbarHostState.showSnackbar(toastMsg) }
            },
            onSharePayload = { payload ->
              val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_TEXT, payload)
                }
              context.startActivity(Intent.createChooser(shareIntent, null))
            },
            onBuildTransferUri = { amount, comment ->
              onBuildTransferUri(amount, comment)
                ?: TonTransferUri.build(tonAddress, bounceable = false, amount = amount, comment = comment)
            },
            onValidateInput = onValidateReceiveInput,
          )
        }
        HomeSheetKind.BUY -> {
          BuySheetContent(
            walletAddress = tonAddress,
            onCopyAddress = { addr -> copyAddress(addr) },
            onBrowserError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
            onDismiss = { activeSheet = null },
          )
        }
        HomeSheetKind.SETTINGS -> {
          SettingsSheetContent(
            activeNetwork = activeNetwork,
            securityMode = metadata.securityMode,
            appSettings = appSettings,
            onToggleAutoLock = onToggleAutoLock,
            onToggleLiveMonitor = onToggleLiveMonitor,
            onOpenAddressBook = {
              activeSheet = null
              onOpenAddressBook()
            },
            onCopyUsdtMaster = { master ->
              SecureClipboard.copyText(context, label = "USDT Master", text = master, isSensitive = false)
              activeSheet = null
              scope.launch { snackbarHostState.showSnackbar(copiedText) }
            },
            keySecurityLevel = keySecurityLevel,
            isDeviceSecure = isDeviceSecure,
            isWatchOnly = metadata.isWatchOnly,
            onSetOrChangePin = {
              activeSheet = null
              onSetOrChangePin()
            },
            onSwitchToDeviceBiometrics = {
              activeSheet = null
              onSwitchToDeviceBiometrics()
            },
          )
        }
        HomeSheetKind.WALLET_MANAGER -> {
          WalletManagerSheetContent(
            wallets = wallets,
            activeWalletId = metadata.walletId,
            activeNetwork = activeNetwork,
            isBusy = isWalletActionBusy,
            errorMessage = walletManagerError,
            onClearError = onClearWalletManagerError,
            onSwitchWallet = { walletId ->
              onSwitchWallet(walletId) { activeSheet = null }
            },
            onCreatePersonalWallet = {
              activeSheet = null
              onCreateAdditionalWallet()
            },
            onImportPersonalWallet = {
              activeSheet = null
              onImportAdditionalWallet()
            },
            onAddWatchOnlyWallet = { address, label ->
              onAddWatchOnlyWallet(address, label) { activeSheet = null }
            },
            onRenameWallet = onRenameWallet,
            onDeleteWallet = onDeleteWallet,
            onValidateInput = onValidateReceiveInput,
          )
        }
      }
    },
  ) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = MaterialTheme.colorScheme.background,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      topBar = {
        WalletTopBar(
          walletName = metadata.walletName,
          isWatchOnly = metadata.isWatchOnly,
          walletVersionLabel =
            if (metadata.isWatchOnly) stringResource(R.string.wallet_badge_watch_only)
            else stringResource(R.string.wallet_badge_format, metadata.walletVersion.displayName),
          onOpenWalletManager = {
            onClearWalletManagerError()
            lastSheetKind = HomeSheetKind.WALLET_MANAGER
            activeSheet = HomeSheetKind.WALLET_MANAGER
          },
          onLockWallet = onLockWallet,
        )
      },
    ) { innerPadding ->
      PullToRefreshBox(
        isRefreshing = balanceState.isLoading || monitorState.isSyncing,
        onRefresh = {
          onRefreshBalance()
          onSyncNow()
        },
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      ) {
        Box(
          modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
          contentAlignment = Alignment.TopCenter,
        ) {
          Column(
            modifier =
              Modifier.widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(vertical = 8.dp)
                .graphicsLayer {
                  scaleX = contentEntryProgress
                  scaleY = contentEntryProgress
                },
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            AnimatedVisibility(
              visible = !metadata.isWatchOnly && !metadata.isBackedUp,
              enter = fadeIn() + expandVertically(),
              exit = fadeOut() + shrinkVertically(),
            ) {
              Card(
                modifier =
                  Modifier.fillMaxWidth()
                    .tactileClickable(onClick = onOpenBackup)
                    .testTag("backup_warning_banner"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2214)),
                border = BorderStroke(0.75.dp, TonkeeperAmber.copy(alpha = 0.35f)),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = TonkeeperAmber,
                  )
                  Text(
                    text = stringResource(R.string.backup_needed_banner),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFDF9E),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                  )
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TonkeeperAmber,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }

            if (metadata.isWatchOnly) {
              Card(
                modifier =
                  Modifier.fillMaxWidth()
                    .tactileClickable {
                      onClearWalletManagerError()
                      lastSheetKind = HomeSheetKind.WALLET_MANAGER
                      activeSheet = HomeSheetKind.WALLET_MANAGER
                    }
                    .testTag("watch_only_info_banner"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF282115)),
                border = BorderStroke(0.75.dp, TonkeeperAmber.copy(alpha = 0.4f)),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = TonkeeperAmber,
                  )
                  Text(
                    text = stringResource(R.string.watch_only_action_blocked),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFDF9E),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                  )
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TonkeeperAmber,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }

            HeroBalanceCard(
              friendlyAddress = friendlyAddress,
              isBackedUp = metadata.isBackedUp,
              isWatchOnly = metadata.isWatchOnly,
              balanceState = balanceState,
              fiatRates = fiatRates,
              onCopy = { copyAddress(friendlyAddress) },
              onRefreshBalance = onRefreshBalance,
            )

            ActionRow(
              isWatchOnly = metadata.isWatchOnly,
              onSend = onOpenSend,
              onReceive = {
                lastSheetKind = HomeSheetKind.RECEIVE
                activeSheet = HomeSheetKind.RECEIVE
              },
              onSwap = onOpenSwap,
              onBuy = {
                lastSheetKind = HomeSheetKind.BUY
                activeSheet = HomeSheetKind.BUY
              },
              onCopy = { copyAddress(friendlyAddress) },
            )

            UtilityQuickRow(
              isWatchOnly = metadata.isWatchOnly,
              onCopy = { copyAddress(friendlyAddress) },
              onBackup = onOpenBackup,
              onOpenContacts = onOpenAddressBook,
              onOpenSettings = {
                lastSheetKind = HomeSheetKind.SETTINGS
                activeSheet = HomeSheetKind.SETTINGS
              },
            )

            AssetsCard(
              balanceState = balanceState,
              fiatRates = fiatRates,
              isWatchOnly = metadata.isWatchOnly,
              onAssetClick = onOpenAssetSend,
              onJettonClick = onOpenJettonSend,
            )

            RecentTransfersCard(
              txRecords = txRecords,
              activeNetwork = activeNetwork,
              onOpenTxStatus = onOpenTxStatus,
              onOpenHistory = onOpenHistory,
            )

            ChainMonitorCard(
              monitorState = monitorState,
              onToggleMonitor = onToggleMonitor,
              onSyncNow = onSyncNow,
            )

            Spacer(modifier = Modifier.height(14.dp))
          }
        }
      }
    }
  }
}

/**
 * Tonkeeper-style top bar featuring a sleek pill wallet switcher and subtle utility controls.
 */
@Composable
private fun WalletTopBar(
  walletName: String,
  isWatchOnly: Boolean,
  walletVersionLabel: String,
  onOpenWalletManager: () -> Unit,
  onLockWallet: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = VaultDarkCard,
        border = BorderStroke(0.5.dp, VaultHairlineBorder),
        modifier =
          Modifier.weight(1f, fill = false)
            .clip(RoundedCornerShape(999.dp))
            .tactileClickable(scaleOnPress = 0.96f, onClick = onOpenWalletManager)
            .minimumInteractiveComponentSize()
            .testTag("open_wallet_manager_button"),
      ) {
        Row(
          modifier = Modifier.padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
          Box(
            modifier =
              Modifier.size(30.dp)
                .clip(CircleShape)
                .background(
                  if (isWatchOnly) {
                    Brush.linearGradient(listOf(Color(0xFFB28900), TonkeeperAmber))
                  } else {
                    Brush.linearGradient(listOf(VaultBlue, Color(0xFF1B76B8)))
                  }
                ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector =
                if (isWatchOnly) Icons.Outlined.Visibility
                else Icons.Outlined.AccountBalanceWallet,
              contentDescription = stringResource(R.string.wallet_manager_switch_cd),
              tint = if (isWatchOnly) Color(0xFF1F1600) else Color.White,
              modifier = Modifier.size(16.dp),
            )
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = walletName,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.widthIn(max = 136.dp),
            )
            Surface(
              shape = RoundedCornerShape(6.dp),
              color =
                if (isWatchOnly) TonkeeperAmber.copy(alpha = 0.16f)
                else VaultDarkCardElevated,
            ) {
              Text(
                text = walletVersionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (isWatchOnly) TonkeeperAmber else VaultTextSecondary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).testTag("wallet_version_badge"),
              )
            }
            Icon(
              imageVector = Icons.Filled.KeyboardArrowDown,
              contentDescription = stringResource(R.string.wallet_manager_switch_cd),
              tint = VaultTextSecondary,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        IconButton(
          onClick = onLockWallet,
          modifier =
            Modifier.size(38.dp)
              .clip(CircleShape)
              .background(VaultDarkCard)
              .testTag("lock_wallet_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = stringResource(R.string.action_lock),
            tint = VaultTextSecondary,
            modifier = Modifier.size(18.dp),
          )
        }
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = VaultBlue.copy(alpha = 0.14f),
          border = BorderStroke(0.5.dp, VaultBlue.copy(alpha = 0.30f)),
          modifier = Modifier.testTag("network_mainnet_badge"),
        ) {
          Text(
            text = stringResource(R.string.network_mainnet),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = VaultBlue,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
      }
    }
  }
}

/**
 * Authentic Tonkeeper open-canvas hero section with breathing ambient glow, large tabular
 * balance typography, copyable address pill, and status indicators.
 */
@Composable
private fun HeroBalanceCard(
  friendlyAddress: String,
  isBackedUp: Boolean,
  isWatchOnly: Boolean = false,
  balanceState: BalanceState,
  fiatRates: FiatRates = FiatRates(),
  onCopy: () -> Unit,
  onRefreshBalance: () -> Unit,
) {
  val balanceText =
    when {
      balanceState.gramBalance != null ->
        stringResource(R.string.wallet_balance_format, balanceState.gramBalance.format())
      balanceState.isLoading -> stringResource(R.string.wallet_balance_loading)
      else -> stringResource(R.string.wallet_balance_unavailable)
    }

  val glowPulse =
    if (balanceState.isLoading) {
      val transition = rememberInfiniteTransition(label = "heroGlow")
      val alpha by
        transition.animateFloat(
          initialValue = 0.12f,
          targetValue = 0.26f,
          animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
          label = "glowAlpha",
        )
      alpha
    } else {
      0.16f
    }

  Box(
    modifier =
      Modifier.fillMaxWidth()
        .testTag("wallet_hero_card")
        .drawBehind {
          drawCircle(
            brush =
              Brush.radialGradient(
                colors = listOf(VaultBlue.copy(alpha = glowPulse), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.38f),
                radius = size.width * 0.62f,
              ),
            radius = size.width * 0.62f,
            center = Offset(size.width * 0.5f, size.height * 0.38f),
          )
        }
        .padding(horizontal = 8.dp, vertical = 10.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Top status & refresh bar
      Box(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.align(Alignment.Center),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = VaultDarkCard.copy(alpha = 0.78f),
            border = BorderStroke(0.5.dp, VaultHairlineBorder),
            modifier =
              Modifier.clip(RoundedCornerShape(999.dp))
                .tactileClickable(onClick = onCopy)
                .minimumInteractiveComponentSize(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = shortenAddress(friendlyAddress),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                color = VaultTextSecondary,
                modifier = Modifier.testTag("wallet_address_text"),
              )
              Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = stringResource(R.string.action_copy),
                tint = VaultTextSecondary,
                modifier = Modifier.size(13.dp),
              )
            }
          }

          if (isWatchOnly) {
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = TonkeeperAmber.copy(alpha = 0.14f),
              modifier = Modifier.testTag("watch_only_badge"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Visibility,
                  contentDescription = null,
                  tint = TonkeeperAmber,
                  modifier = Modifier.size(13.dp),
                )
                Text(
                  text = stringResource(R.string.wallet_type_watch_only),
                  style = MaterialTheme.typography.labelSmall,
                  color = TonkeeperAmber,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }
          } else if (isBackedUp) {
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = UsdtGreen.copy(alpha = 0.12f),
              modifier = Modifier.testTag("backed_up_badge"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.VerifiedUser,
                  contentDescription = null,
                  tint = UsdtGreen,
                  modifier = Modifier.size(13.dp),
                )
                Text(
                  text = stringResource(R.string.backup_verified_badge),
                  style = MaterialTheme.typography.labelSmall,
                  color = UsdtGreen,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }
          }
        }

        IconButton(
          onClick = onRefreshBalance,
          enabled = !balanceState.isLoading,
          modifier =
            Modifier.align(Alignment.CenterEnd)
              .size(36.dp)
              .testTag("refresh_balance_button"),
        ) {
          if (balanceState.isLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
              color = VaultBlue,
            )
          } else {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = stringResource(R.string.action_refresh_balance),
              tint = VaultTextSecondary,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(2.dp))

      // Centerpiece Tonkeeper Balance
      AnimatedBalanceText(
        text = balanceText,
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("hero_balance_text"),
      )

      val totalUsdText =
        fiatRates.formatUsdValue(
          gramAmount = balanceState.gramBalance,
          usdtAmount = balanceState.usdtBalance,
        )
      AnimatedVisibility(visible = totalUsdText != null) {
        if (totalUsdText != null) {
          Text(
            text = stringResource(R.string.portfolio_total_usd, totalUsdText),
            style = MaterialTheme.typography.bodyMedium,
            color = VaultTextSecondary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.testTag("hero_usd_total"),
          )
        }
      }

      if (balanceState.isStale) {
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = Color(0xFF2B2214),
          border = BorderStroke(0.5.dp, TonkeeperAmber.copy(alpha = 0.4f)),
          modifier = Modifier.tactileClickable(onClick = onRefreshBalance).testTag("balance_stale_badge"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.WarningAmber,
              contentDescription = null,
              tint = TonkeeperAmber,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = stringResource(R.string.balance_stale_badge),
              style = MaterialTheme.typography.labelSmall,
              color = Color(0xFFFFDF9E),
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      } else if (balanceState.providerName != null && balanceState.errorMessage == null) {
        Text(
          text =
            "${stringResource(R.string.network_mainnet)} • ${
              stringResource(R.string.balance_sync_provider, balanceState.providerName)
            }",
          style = MaterialTheme.typography.labelSmall,
          color = VaultTextSecondary.copy(alpha = 0.78f),
          modifier = Modifier.testTag("balance_provider_label"),
        )
      }

      balanceState.errorMessage?.let { errMsg ->
        Text(
          text = "$errMsg • ${stringResource(R.string.balance_retry_hint)}",
          style = MaterialTheme.typography.labelSmall,
          color = TonkeeperRed,
          modifier = Modifier.tactileClickable(onClick = onRefreshBalance).testTag("balance_error_banner"),
        )
      }
    }
  }
}

@Composable
private fun ActionRow(
  isWatchOnly: Boolean = false,
  onSend: () -> Unit,
  onReceive: () -> Unit,
  onSwap: () -> Unit,
  onBuy: () -> Unit,
  onCopy: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
  ) {
    if (!isWatchOnly) {
      TonkeeperActionCircle(
        icon = Icons.Filled.ArrowUpward,
        label = stringResource(R.string.action_send),
        primary = true,
        tag = "send_button",
        modifier = Modifier.weight(1f),
        onClick = onSend,
      )
    }
    TonkeeperActionCircle(
      icon = Icons.Filled.ArrowDownward,
      label = stringResource(R.string.action_receive),
      primary = true,
      tag = "receive_button",
      modifier = Modifier.weight(1f),
      onClick = onReceive,
    )
    if (!isWatchOnly) {
      TonkeeperActionCircle(
        icon = Icons.Filled.SwapHoriz,
        label = stringResource(R.string.action_swap),
        primary = false,
        tag = "swap_button",
        modifier = Modifier.weight(1f),
        onClick = onSwap,
      )
    }
    TonkeeperActionCircle(
      icon = Icons.Outlined.CreditCard,
      label = stringResource(R.string.action_buy),
      primary = false,
      tag = "buy_button",
      modifier = Modifier.weight(1f),
      onClick = onBuy,
    )
    if (isWatchOnly) {
      TonkeeperActionCircle(
        icon = Icons.Filled.ContentCopy,
        label = stringResource(R.string.action_copy),
        primary = false,
        tag = "copy_address_button",
        modifier = Modifier.weight(1f),
        onClick = onCopy,
      )
    }
  }
}

@Composable
private fun UtilityQuickRow(
  isWatchOnly: Boolean = false,
  onCopy: () -> Unit,
  onBackup: () -> Unit,
  onOpenContacts: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (!isWatchOnly) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = VaultDarkCard,
          border = BorderStroke(0.5.dp, VaultHairlineBorder),
          modifier =
            Modifier.weight(1f)
              .height(48.dp)
              .clip(RoundedCornerShape(16.dp))
              .tactileClickable(onClick = onCopy)
              .testTag("copy_address_button"),
        ) {
          Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.ContentCopy,
              contentDescription = null,
              tint = VaultBlue,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.action_copy),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = VaultDarkCard,
          border = BorderStroke(0.5.dp, VaultHairlineBorder),
          modifier =
            Modifier.weight(1f)
              .height(48.dp)
              .clip(RoundedCornerShape(16.dp))
              .tactileClickable(onClick = onBackup)
              .testTag("backup_phrase_button"),
        ) {
          Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.Key,
              contentDescription = null,
              tint = TonkeeperAmber,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.action_backup),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = VaultDarkCard,
        border = BorderStroke(0.5.dp, VaultHairlineBorder),
        modifier =
          Modifier.weight(1f)
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .tactileClickable(onClick = onOpenContacts)
            .testTag("open_address_book_button"),
      ) {
        Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.Contacts,
            contentDescription = null,
            tint = VaultBlue,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.action_contacts),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = VaultDarkCard,
        border = BorderStroke(0.5.dp, VaultHairlineBorder),
        modifier =
          Modifier.weight(1f)
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .tactileClickable(onClick = onOpenSettings)
            .testTag("open_settings_button"),
      ) {
        Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            tint = VaultTextSecondary,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.action_settings),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
  }
}

@Composable
private fun AssetsCard(
  balanceState: BalanceState,
  fiatRates: FiatRates = FiatRates(),
  isWatchOnly: Boolean = false,
  onAssetClick: (TxAssetKind) -> Unit = {},
  onJettonClick: (String) -> Unit = { onAssetClick(TxAssetKind.JETTON) },
) {
  val gramBalanceText =
    balanceState.gramBalance?.let { stringResource(R.string.wallet_balance_format, it.format()) }
      ?: stringResource(R.string.asset_balance_pending)
  val gramUsdText =
    balanceState.gramBalance?.let { fiatRates.formatUsdValue(gramAmount = it, usdtAmount = null) }

  val visibleHoldings =
    remember(balanceState.jettonHoldings, balanceState.usdtBalance) {
      val fromCache = balanceState.jettonHoldings.filter { it.verification != JettonVerificationStatus.BLACKLISTED }
      if (fromCache.any { it.isUsdtAllowlisted }) {
        fromCache
      } else {
        val usdtSpec = JettonAllowList.MAINNET_USDT
        val fallbackUsdtHolding =
          JettonHolding(
            masterAddress = usdtSpec.masterAddress,
            jettonWalletAddress = balanceState.usdtJettonWallet,
            symbol = usdtSpec.symbol,
            name = usdtSpec.name,
            balance = balanceState.usdtBalance ?: Money.zero(usdtSpec.decimals),
            imageUrl = null,
            verification = JettonVerificationStatus.ALLOWLISTED,
          )
        listOf(fallbackUsdtHolding) + fromCache
      }
    }

  VaultGlassCard(
    modifier = Modifier.fillMaxWidth().testTag("assets_card"),
    shape = RoundedCornerShape(18.dp),
    containerColor = VaultDarkCard,
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.assets_section_title),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text =
            if (isWatchOnly) stringResource(R.string.wallet_type_watch_only)
            else stringResource(R.string.assets_tap_to_send),
          style = MaterialTheme.typography.labelSmall,
          color = if (isWatchOnly) TonkeeperAmber else VaultTextSecondary,
        )
      }
      AssetRow(
        leadingAvatar = {
          TonOfficialLogo(
            contentDescription = stringResource(R.string.asset_gram_name),
            size = 44.dp,
          )
        },
        name = stringResource(R.string.asset_gram_name),
        subtitle = stringResource(R.string.asset_gram_subtitle),
        balance = gramBalanceText,
        usdSubtitle = gramUsdText,
        balanceTag = "asset_gram_balance",
        onClick = { onAssetClick(TxAssetKind.GRAM) },
      )

      visibleHoldings.forEach { holding ->
        HorizontalDivider(
          thickness = 0.5.dp,
          color = VaultHairlineBorder,
          modifier = Modifier.padding(start = 76.dp, end = 18.dp),
        )
        val isUsdtRow = holding.isUsdtAllowlisted
        val hasCachedUsdt = balanceState.usdtBalance != null || holding.fetchedAtEpochMs > 0L
        val balanceText =
          when {
            isUsdtRow && !hasCachedUsdt -> stringResource(R.string.asset_balance_pending)
            else -> "${holding.balance.format()} ${holding.symbol}"
          }
        val usdText =
          if (isUsdtRow && (balanceState.usdtBalance != null || hasCachedUsdt)) {
            fiatRates.formatUsdValue(gramAmount = null, usdtAmount = holding.balance)
          } else {
            null
          }
        val rowTag =
          if (isUsdtRow) "asset_usdt_balance"
          else "asset_jetton_balance_${holding.masterRaw.takeLast(8)}"

        AssetRow(
          leadingAvatar = {
            if (isUsdtRow && holding.imageUrl.isNullOrBlank()) {
              Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(UsdtGreen),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Outlined.CurrencyExchange,
                  contentDescription = holding.name,
                  tint = Color.White,
                  modifier = Modifier.size(22.dp),
                )
              }
            } else {
              JettonAvatar(
                symbol = holding.symbol,
                imageUrl = holding.imageUrl,
                isUsdtAllowlisted = isUsdtRow,
                masterRaw = holding.masterRaw,
                size = 44.dp,
              )
            }
          },
          name = holding.name,
          subtitle =
            if (isUsdtRow) stringResource(R.string.asset_usdt_subtitle)
            else stringResource(R.string.jetton_subtitle_format, holding.symbol),
          balance = balanceText,
          usdSubtitle = usdText,
          verificationBadge =
            if (!isUsdtRow) {
              { JettonVerificationInlineBadge(verification = holding.verification) }
            } else {
              null
            },
          balanceTag = rowTag,
          onClick = { onJettonClick(holding.masterRaw) },
        )
      }
    }
  }
}

@Composable
private fun AssetRow(
  leadingAvatar: @Composable () -> Unit,
  name: String,
  subtitle: String,
  balance: String,
  usdSubtitle: String? = null,
  verificationBadge: (@Composable () -> Unit)? = null,
  balanceTag: String,
  onClick: () -> Unit = {},
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .tactileClickable(scaleOnPress = 0.985f, onClick = onClick)
        .minimumInteractiveComponentSize()
        .padding(horizontal = 18.dp, vertical = 13.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    leadingAvatar()
    Spacer(modifier = Modifier.width(14.dp))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = name,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        verificationBadge?.invoke()
      }
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = VaultTextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Spacer(modifier = Modifier.width(8.dp))
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = balance,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics(mergeDescendants = true) {}.testTag(balanceTag),
      )
      usdSubtitle?.let { usd ->
        Text(
          text = usd,
          style = MaterialTheme.typography.bodySmall,
          color = VaultTextSecondary,
        )
      }
    }
  }
}

@Composable
private fun RecentTransfersCard(
  txRecords: List<TxRecord>,
  activeNetwork: Network,
  onOpenTxStatus: (String) -> Unit,
  onOpenHistory: () -> Unit,
) {
  VaultGlassCard(
    modifier = Modifier.fillMaxWidth().testTag("recent_txs_card"),
    shape = RoundedCornerShape(18.dp),
    containerColor = VaultDarkCard,
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.recent_txs_title),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (txRecords.isNotEmpty()) {
          TextButton(
            onClick = onOpenHistory,
            modifier = Modifier.testTag("view_all_history_button"),
          ) {
            Text(
              text = stringResource(R.string.recent_txs_view_all),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = VaultBlue,
            )
          }
        }
      }
      if (txRecords.isEmpty()) {
        Text(
          text = stringResource(R.string.recent_txs_empty),
          style = MaterialTheme.typography.bodySmall,
          color = VaultTextSecondary,
          modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        )
      } else {
        txRecords.take(5).forEachIndexed { index, tx ->
          if (index > 0) {
            HorizontalDivider(
              thickness = 0.5.dp,
              color = VaultHairlineBorder,
              modifier = Modifier.padding(start = 74.dp, end = 18.dp),
            )
          }
          val isIncoming = tx.direction == TxDirection.IN
          val counterpartyDisplay =
            remember(tx.counterpartyRaw, activeNetwork) {
              runCatching {
                TonAddress(tx.counterpartyRaw, activeNetwork).toFriendly(bounceable = false)
              }.getOrDefault(tx.counterpartyRaw)
            }
          val timeStr =
            remember(tx.createdAtEpochMs) {
              SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(tx.createdAtEpochMs))
            }
          val statusColor =
            when (tx.state) {
              TxStatus.CONFIRMED -> UsdtGreen
              TxStatus.BROADCASTED -> VaultBlue
              TxStatus.SIGNED,
              TxStatus.UNCERTAIN,
              TxStatus.EXPIRED,
              TxStatus.DRAFT -> TonkeeperAmber
              TxStatus.FAILED -> TonkeeperRed
            }
          val subtitleText =
            if (isIncoming || tx.id.startsWith("chain_")) {
              "${tx.state.name} • $timeStr"
            } else {
              "${tx.state.name} • #${tx.seqno}"
            }
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .tactileClickable(scaleOnPress = 0.985f) { onOpenTxStatus(tx.id) }
                .padding(horizontal = 18.dp, vertical = 13.dp)
                .testTag("tx_row_${tx.id}"),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (tx.kind == TxAssetKind.JETTON && !tx.jettonImageUrl.isNullOrBlank()) {
              JettonAvatar(
                symbol = tx.assetSymbol,
                imageUrl = tx.jettonImageUrl,
                isUsdtAllowlisted = tx.jettonVerification == JettonVerificationStatus.ALLOWLISTED,
                masterRaw = tx.jettonMaster,
                size = 42.dp,
              )
            } else {
              Box(
                modifier =
                  Modifier.size(42.dp)
                    .clip(CircleShape)
                    .background(VaultDarkCardElevated),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = if (isIncoming) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                  contentDescription = null,
                  tint = if (isIncoming) UsdtGreen else VaultTextSecondary,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text =
                  (if (tx.direction == TxDirection.OUT) "To " else "From ") +
                    shortenAddress(counterpartyDisplay),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                fontWeight = FontWeight.SemiBold,
              )
              tx.comment?.takeIf { it.isNotBlank() }?.let { memo ->
                Text(
                  text = memo,
                  style = MaterialTheme.typography.bodySmall,
                  color = VaultTextSecondary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Text(
                text = subtitleText,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                fontWeight = FontWeight.Medium,
              )
            }
            Text(
              text =
                (if (tx.direction == TxDirection.OUT) "-" else "+") +
                  "${tx.amount.format()} ${tx.assetSymbol}",
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.SemiBold,
              color = if (isIncoming) UsdtGreen else MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ChainMonitorCard(
  monitorState: ChainMonitorState,
  onToggleMonitor: () -> Unit,
  onSyncNow: () -> Unit,
) {
  val isRunning = monitorState.enabled && monitorState.isActive
  val indicatorColor =
    when {
      !monitorState.enabled -> TonkeeperAmber
      monitorState.lastError != null -> TonkeeperRed
      isRunning -> UsdtGreen
      else -> VaultBlue
    }
  val pulseAlpha =
    if (isRunning && monitorState.lastError == null) {
      val transition = rememberInfiniteTransition(label = "monitorPulse")
      val animated by
        transition.animateFloat(
          initialValue = 0.40f,
          targetValue = 1f,
          animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
          label = "pulseAlpha",
        )
      animated
    } else {
      1f
    }

  val titleText =
    if (monitorState.enabled) stringResource(R.string.monitor_title_active)
    else stringResource(R.string.monitor_title_paused)
  val subtitleText =
    if (monitorState.enabled) {
      stringResource(R.string.monitor_subtitle_active, monitorState.pollIntervalSeconds)
    } else {
      stringResource(R.string.monitor_subtitle_paused)
    }
  val formattedLastSync =
    remember(monitorState.lastSyncEpochMs) {
      monitorState.lastSyncEpochMs?.let { ts ->
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ts))
      }
    }

  VaultGlassCard(
    modifier = Modifier.fillMaxWidth().testTag("chain_monitor_card"),
    shape = RoundedCornerShape(18.dp),
    containerColor = VaultDarkCard,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.semantics(mergeDescendants = true) {}.testTag("monitor_status_badge"),
        ) {
          Box(
            modifier =
              Modifier.size(9.dp)
                .graphicsLayer { alpha = pulseAlpha }
                .clip(CircleShape)
                .background(indicatorColor)
          )
          Text(
            text = "$titleText • ${stringResource(R.string.network_mainnet)}",
            style = MaterialTheme.typography.labelLarge,
            color = indicatorColor,
            fontWeight = FontWeight.SemiBold,
          )
        }
        if (monitorState.isSyncing) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = VaultBlue,
          )
        }
      }

      Text(
        text = subtitleText,
        style = MaterialTheme.typography.bodySmall,
        color = VaultTextSecondary,
      )

      Text(
        text =
          if (formattedLastSync != null) {
            stringResource(
              R.string.monitor_last_sync,
              formattedLastSync,
              monitorState.lastProviderName ?: "TON",
            )
          } else {
            stringResource(R.string.monitor_waiting_first_sync)
          },
        style = MaterialTheme.typography.labelSmall,
        color = VaultTextSecondary,
      )

      monitorState.lastError?.let { err ->
        Text(
          text = err,
          style = MaterialTheme.typography.labelSmall,
          color = TonkeeperRed,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        TextButton(
          onClick = onSyncNow,
          enabled = !monitorState.isSyncing,
          shape = RoundedCornerShape(14.dp),
          colors =
            ButtonDefaults.textButtonColors(
              containerColor = VaultDarkCardElevated,
              contentColor = MaterialTheme.colorScheme.onSurface,
              disabledContainerColor = VaultDarkCardElevated.copy(alpha = 0.5f),
            ),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier.weight(1f).height(46.dp).testTag("monitor_sync_now_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.monitor_action_sync_now),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
          )
        }
        TextButton(
          onClick = onToggleMonitor,
          shape = RoundedCornerShape(14.dp),
          colors =
            ButtonDefaults.textButtonColors(
              containerColor = VaultDarkCardElevated,
              contentColor = MaterialTheme.colorScheme.onSurface,
            ),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier.weight(1f).height(46.dp).testTag("monitor_toggle_button"),
        ) {
          Icon(
            imageVector = if (monitorState.enabled) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text =
              if (monitorState.enabled) stringResource(R.string.monitor_action_pause)
              else stringResource(R.string.monitor_action_resume),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
  }
}

private fun shortenAddress(address: String): String {
  if (address.length <= 14) return address
  return "${address.take(6)}...${address.takeLast(6)}"
}
