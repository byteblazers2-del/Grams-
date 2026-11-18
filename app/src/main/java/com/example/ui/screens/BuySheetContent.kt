package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.model.Network
import com.example.core.model.TonAddress
import com.example.core.wallet.BuyAssetOption
import com.example.core.wallet.BuyOnRampHelper
import com.example.core.wallet.BuyProvider
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.TonkeeperAmber
import com.example.ui.theme.UsdtGreen
import com.example.ui.theme.VaultBlue
import com.example.ui.theme.VaultDarkCard
import com.example.ui.theme.VaultDarkCardElevated
import com.example.ui.theme.VaultHairlineBorder
import com.example.ui.theme.VaultTextSecondary
import com.gramvault.wallet.R

@Composable
fun BuySheetContent(
  walletAddress: TonAddress,
  onCopyAddress: (String) -> Unit,
  onBrowserError: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val browserUnavailableMsg = stringResource(R.string.buy_browser_unavailable)
  var selectedAsset by remember { mutableStateOf(BuyAssetOption.TON) }
  val friendlyAddress =
    remember(walletAddress) { walletAddress.toFriendly(bounceable = false) }
  val isMainnet = walletAddress.network == Network.MAINNET

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 14.dp)
        .testTag("buy_sheet_content"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Text(
      text = stringResource(R.string.buy_sheet_title),
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = stringResource(R.string.buy_sheet_subtitle),
      style = MaterialTheme.typography.bodySmall,
      color = VaultTextSecondary,
    )

    // Asset selector (TON / USDT)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        text = stringResource(R.string.buy_asset_label),
        style = MaterialTheme.typography.labelLarge,
        color = VaultTextSecondary,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        BuyAssetOption.entries.forEach { option ->
          val selected = option == selectedAsset
          val tag = if (option == BuyAssetOption.TON) "buy_asset_ton" else "buy_asset_usdt"
          FilterChip(
            selected = selected,
            onClick = { selectedAsset = option },
            label = {
              Text(
                text = option.tickerLabel,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              )
            },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor =
                  if (option == BuyAssetOption.USDT) UsdtGreen.copy(alpha = 0.2f)
                  else VaultBlue.copy(alpha = 0.2f),
                selectedLabelColor =
                  if (option == BuyAssetOption.USDT) UsdtGreen else VaultBlue,
              ),
            modifier = Modifier.testTag(tag),
          )
        }
      }
    }

    // Mainnet Deposit Address preview
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = VaultDarkCardElevated,
      border = BorderStroke(0.8.dp, VaultHairlineBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = stringResource(R.string.buy_address_preview_label),
          style = MaterialTheme.typography.labelSmall,
          color = VaultTextSecondary,
        )
        Text(
          text = friendlyAddress,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag("buy_deposit_address_text"),
        )
      }
    }

    // Provider cards
    BuyProviderCard(
      provider = BuyProvider.MERCURYO,
      title = stringResource(R.string.buy_provider_mercuryo_title),
      subtitle = stringResource(R.string.buy_provider_mercuryo_subtitle),
      icon = Icons.Outlined.CreditCard,
      accentColor = VaultBlue,
      cardTag = "buy_provider_mercuryo",
      buttonTag = "buy_open_mercuryo_button",
      isConfigured = isMainnet && BuyOnRampHelper.isProviderConfigured(BuyProvider.MERCURYO),
      onLaunchCheckout = {
        val url =
          BuyOnRampHelper.buildCheckoutUrl(
            provider = BuyProvider.MERCURYO,
            walletAddress = walletAddress,
            asset = selectedAsset,
          )
        if (url != null) {
          runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
            onDismiss()
          }.onFailure {
            onBrowserError(browserUnavailableMsg)
          }
        }
      },
    )

    BuyProviderCard(
      provider = BuyProvider.ONRAMPER,
      title = stringResource(R.string.buy_provider_onramper_title),
      subtitle = stringResource(R.string.buy_provider_onramper_subtitle),
      icon = Icons.Outlined.CurrencyExchange,
      accentColor = UsdtGreen,
      cardTag = "buy_provider_onramper",
      buttonTag = "buy_open_onramper_button",
      isConfigured = isMainnet && BuyOnRampHelper.isProviderConfigured(BuyProvider.ONRAMPER),
      onLaunchCheckout = {
        val url =
          BuyOnRampHelper.buildCheckoutUrl(
            provider = BuyProvider.ONRAMPER,
            walletAddress = walletAddress,
            asset = selectedAsset,
          )
        if (url != null) {
          runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
            onDismiss()
          }.onFailure {
            onBrowserError(browserUnavailableMsg)
          }
        }
      },
    )

    Text(
      text = stringResource(R.string.buy_or_deposit_direct),
      style = MaterialTheme.typography.labelMedium,
      color = VaultTextSecondary,
    )

    OutlinedButton(
      onClick = {
        onCopyAddress(friendlyAddress)
        onDismiss()
      },
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, VaultBlue.copy(alpha = 0.5f)),
      modifier = Modifier.fillMaxWidth().height(50.dp).testTag("buy_copy_address_button"),
    ) {
      Icon(
        imageVector = Icons.Filled.ContentCopy,
        contentDescription = null,
        tint = VaultBlue,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = stringResource(R.string.buy_copy_address_button),
        fontWeight = FontWeight.SemiBold,
        color = VaultBlue,
      )
    }
  }
}

@Composable
private fun BuyProviderCard(
  provider: BuyProvider,
  title: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  accentColor: Color,
  cardTag: String,
  buttonTag: String,
  isConfigured: Boolean,
  onLaunchCheckout: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = VaultDarkCard,
    border = BorderStroke(0.8.dp, VaultHairlineBorder),
    modifier = Modifier.fillMaxWidth().testTag(cardTag),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(26.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = VaultTextSecondary,
          )
        }
      }

      if (isConfigured) {
        Button(
          onClick = onLaunchCheckout,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = accentColor),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag(buttonTag),
        ) {
          Text(
            text = stringResource(R.string.buy_provider_open_button),
            fontWeight = FontWeight.Bold,
            color = Color(0xFF001E30),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = Color(0xFF001E30),
            modifier = Modifier.size(16.dp),
          )
        }
      } else {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color(0xFF2B2214),
          border = BorderStroke(0.6.dp, TonkeeperAmber.copy(alpha = 0.35f)),
          modifier = Modifier.fillMaxWidth().testTag("${cardTag}_key_missing"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Outlined.Info,
              contentDescription = null,
              tint = TonkeeperAmber,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = stringResource(R.string.buy_provider_key_missing, provider.secretFieldName),
              style = MaterialTheme.typography.labelSmall,
              color = Color(0xFFFFDF9E),
            )
          }
        }
      }
    }
  }
}
