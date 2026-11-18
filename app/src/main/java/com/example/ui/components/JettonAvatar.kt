package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.core.model.JettonVerificationStatus
import com.example.ui.theme.UsdtGreen
import com.example.ui.theme.VaultBlue
import com.gramvault.wallet.R
import kotlin.math.abs

private val AvatarPalettes =
  listOf(
    listOf(Color(0xFF0077B6), VaultBlue),
    listOf(Color(0xFF0F9D58), UsdtGreen),
    listOf(Color(0xFF5E35B1), Color(0xFF8E24AA)),
    listOf(Color(0xFFD84315), Color(0xFFFF7043)),
    listOf(Color(0xFF00838F), Color(0xFF26C6DA)),
    listOf(Color(0xFFC2185B), Color(0xFFF06292)),
    listOf(Color(0xFF2E7D32), Color(0xFF66BB6A)),
    listOf(Color(0xFFF57C00), Color(0xFFFFB74D)),
  )

const val OFFICIAL_TON_LOGO_URL = "https://ton.org/download/ton_symbol.png"

@Composable
fun TonOfficialLogo(
  imageUrl: String? = OFFICIAL_TON_LOGO_URL,
  size: Dp = 44.dp,
  contentDescription: String? = null,
  modifier: Modifier = Modifier,
) {
  val remoteUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
  Box(
    modifier = modifier.size(size).clip(CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Image(
      painter = painterResource(id = R.drawable.ic_ton_official),
      contentDescription = contentDescription,
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )
    if (remoteUrl != null) {
      val context = LocalContext.current
      val request =
        remember(remoteUrl, context) {
          ImageRequest.Builder(context)
            .data(remoteUrl)
            .crossfade(true)
            .build()
        }
      AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().clip(CircleShape),
      )
    }
  }
}

@Composable
fun JettonAvatar(
  symbol: String,
  imageUrl: String?,
  isUsdtAllowlisted: Boolean = false,
  masterRaw: String? = null,
  size: Dp = 42.dp,
  modifier: Modifier = Modifier,
) {
  val cleanSymbol = symbol.trim().ifEmpty { "?" }
  val monogram = remember(cleanSymbol) { cleanSymbol.take(2).uppercase() }
  val palette =
    remember(masterRaw, cleanSymbol, isUsdtAllowlisted) {
      if (isUsdtAllowlisted || cleanSymbol.equals("USDT", ignoreCase = true)) {
        listOf(Color(0xFF147A5E), UsdtGreen)
      } else {
        val seed = abs((masterRaw ?: cleanSymbol).hashCode())
        AvatarPalettes[seed % AvatarPalettes.size]
      }
    }

  Box(
    modifier =
      modifier
        .size(size)
        .clip(CircleShape)
        .background(Brush.linearGradient(palette)),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = monogram,
      style =
        if (size <= 24.dp) MaterialTheme.typography.labelSmall
        else MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = Color.White,
    )
    if (!imageUrl.isNullOrBlank()) {
      val context = LocalContext.current
      val request =
        remember(imageUrl, context) {
          ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .build()
        }
      AsyncImage(
        model = request,
        contentDescription = cleanSymbol,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().clip(CircleShape),
      )
    }
  }
}

@Composable
fun JettonVerificationInlineBadge(
  verification: JettonVerificationStatus,
  modifier: Modifier = Modifier,
) {
  when (verification) {
    JettonVerificationStatus.ALLOWLISTED,
    JettonVerificationStatus.VERIFIED -> {
      val tint = if (verification == JettonVerificationStatus.ALLOWLISTED) UsdtGreen else VaultBlue
      Icon(
        imageVector = Icons.Filled.Verified,
        contentDescription = stringResource(R.string.jetton_verified_badge),
        tint = tint,
        modifier = modifier.size(15.dp),
      )
    }
    JettonVerificationStatus.UNVERIFIED -> {
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFF332612),
        border = BorderStroke(0.8.dp, Color(0x44FFCA28)),
        modifier = modifier,
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Text(
            text = stringResource(R.string.jetton_unverified_badge),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFFFE082),
          )
        }
      }
    }
    JettonVerificationStatus.BLACKLISTED -> Unit
  }
}
