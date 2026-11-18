package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.VaultBlue
import com.example.ui.theme.VaultDarkBackground
import com.example.ui.theme.VaultDarkCard
import com.example.ui.theme.VaultDarkCardElevated
import com.example.ui.theme.VaultHairlineBorder
import com.example.ui.theme.VaultTextPrimary
import com.example.ui.theme.VaultTextSecondary

/**
 * Adds a fluid, spring-animated press scale + subtle vertical micro-shift + haptic tick.
 */
@Composable
fun Modifier.tactileClickable(
  enabled: Boolean = true,
  scaleOnPress: Float = 0.96f,
  withHaptic: Boolean = true,
  onClick: () -> Unit,
): Modifier {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val haptics = LocalHapticFeedback.current
  val currentOnClick by rememberUpdatedState(onClick)
  val currentWithHaptic by rememberUpdatedState(withHaptic)
  val scale by
    animateFloatAsState(
      targetValue = if (isPressed && enabled) scaleOnPress else 1f,
      animationSpec = spring(dampingRatio = 0.58f, stiffness = 520f),
      label = "tactileScale",
    )
  val shiftY by
    animateFloatAsState(
      targetValue = if (isPressed && enabled) 2f else 0f,
      animationSpec = spring(dampingRatio = 0.65f, stiffness = 560f),
      label = "tactileShiftY",
    )

  return this.graphicsLayer {
      scaleX = scale
      scaleY = scale
      translationY = shiftY
    }
    .clickable(
      enabled = enabled,
      interactionSource = interactionSource,
      indication = ripple(),
      onClick = {
        if (currentWithHaptic) {
          runCatching { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        }
        currentOnClick()
      },
    )
}

/**
 * Authentic Tonkeeper-style circular action button (`#1D2633` / `#2E3847` surface) with
 * spring physics press bounce, icon micro-lift, and crisp label underneath.
 */
@Composable
fun TonkeeperActionCircle(
  icon: ImageVector,
  label: String,
  primary: Boolean,
  tag: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val haptics = LocalHapticFeedback.current
  val scale by
    animateFloatAsState(
      targetValue = if (isPressed) 0.90f else 1f,
      animationSpec = spring(dampingRatio = 0.52f, stiffness = 480f),
      label = "actionCircleScale",
    )
  val iconOffsetY by
    animateFloatAsState(
      targetValue = if (isPressed) 2.5f else 0f,
      animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
      label = "actionIconOffset",
    )
  val circleBgColor by
    animateColorAsState(
      targetValue = if (isPressed) VaultDarkCardElevated else VaultDarkCard,
      animationSpec = tween(140),
      label = "actionCircleBg",
    )

  Column(
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
        .clip(RoundedCornerShape(18.dp))
        .clickable(
          interactionSource = interactionSource,
          indication = ripple(bounded = false, radius = 34.dp),
          onClick = {
            runCatching { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            onClick()
          },
        )
        .padding(vertical = 6.dp)
        .testTag(tag),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(
      modifier =
        Modifier.size(56.dp)
          .clip(CircleShape)
          .background(
            Brush.verticalGradient(
              colors =
                if (primary) {
                  listOf(VaultDarkCardElevated.copy(alpha = 0.95f), circleBgColor)
                } else {
                  listOf(circleBgColor.copy(alpha = 0.95f), VaultDarkCard)
                }
            )
          )
          .border(
            width = 0.75.dp,
            brush =
              Brush.verticalGradient(
                colors =
                  if (primary) {
                    listOf(VaultBlue.copy(alpha = 0.35f), VaultHairlineBorder)
                  } else {
                    listOf(Color(0x24FFFFFF), Color(0x08FFFFFF))
                  }
              ),
            shape = CircleShape,
          ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (primary) VaultTextPrimary else VaultTextSecondary,
        modifier =
          Modifier.size(24.dp).graphicsLayer {
            translationY = iconOffsetY
          },
      )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Medium,
      color = if (primary) VaultTextPrimary.copy(alpha = 0.92f) else VaultTextSecondary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/**
 * Tonkeeper grouped content card (`#1D2633` with `18.dp` corner radius and subtle hairline border).
 */
@Composable
fun VaultGlassCard(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(18.dp),
  containerColor: Color = VaultDarkCard,
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(
    modifier = modifier,
    shape = shape,
    colors = CardDefaults.cardColors(containerColor = containerColor),
    border = BorderStroke(0.5.dp, VaultHairlineBorder),
    content = content,
  )
}

/**
 * Thumb-zone bottom bar pinned safely above gesture navigation bar & IME keyboard.
 */
@Composable
fun BottomThumbActionBar(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    color = VaultDarkBackground.copy(alpha = 0.96f),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      HorizontalDivider(thickness = 0.5.dp, color = VaultHairlineBorder)
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          content = content,
        )
      }
    }
  }
}

/**
 * Smoothly animates numeric balance updates with spring-driven vertical slide + scale + fade.
 */
@Composable
fun AnimatedBalanceText(
  text: String,
  style: TextStyle,
  color: Color,
  modifier: Modifier = Modifier,
  fontWeight: FontWeight = FontWeight.Bold,
) {
  AnimatedContent(
    targetState = text,
    transitionSpec = {
      val enterSpring = spring<androidx.compose.ui.unit.IntOffset>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
      )
      (fadeIn(tween(220)) +
        scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f)) +
        slideInVertically(enterSpring) { it / 3 }) togetherWith
        (fadeOut(tween(140)) +
          scaleOut(targetScale = 0.96f, animationSpec = tween(140)) +
          slideOutVertically(tween(150)) { -it / 3 }) using
        SizeTransform(clip = false)
    },
    label = "balanceTextTransition",
  ) { value ->
    Text(
      text = value,
      style = style,
      fontWeight = fontWeight,
      color = color,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = modifier,
    )
  }
}
