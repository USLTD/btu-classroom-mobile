package com.example.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.design.theme.LocalIsDarkTheme
import com.example.design.theme.StatusError
import com.example.design.theme.StatusErrorBgDark
import com.example.design.theme.StatusErrorBgLight
import com.example.design.theme.StatusNeutralBgDark
import com.example.design.theme.StatusNeutralBgLight
import com.example.design.theme.StatusSuccess
import com.example.design.theme.StatusSuccessBgDark
import com.example.design.theme.StatusSuccessBgLight
import com.example.design.theme.StatusWarning
import com.example.design.theme.StatusWarningBgDark
import com.example.design.theme.StatusWarningBgLight

enum class BadgeTone {
  NEUTRAL,
  PRIMARY,
  SUCCESS,
  WARNING,
  ERROR,
}

/**
 * Accessible status badge pairing an explicit icon and label so status is never communicated by color alone.
 */
@Composable
fun StatusBadge(
  label: String,
  icon: ImageVector,
  tone: BadgeTone,
  modifier: Modifier = Modifier,
) {
  val isDark = LocalIsDarkTheme.current
  val (bg, fg) = when (tone) {
    BadgeTone.SUCCESS -> (if (isDark) StatusSuccessBgDark else StatusSuccessBgLight) to
      (if (isDark) Color(0xFF6EE7B7) else StatusSuccess)
    BadgeTone.WARNING -> (if (isDark) StatusWarningBgDark else StatusWarningBgLight) to
      (if (isDark) Color(0xFFFCD34D) else StatusWarning)
    BadgeTone.ERROR -> (if (isDark) StatusErrorBgDark else StatusErrorBgLight) to
      (if (isDark) Color(0xFFFCA5A5) else StatusError)
    BadgeTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    BadgeTone.NEUTRAL -> (if (isDark) StatusNeutralBgDark else StatusNeutralBgLight) to
      MaterialTheme.colorScheme.onSurfaceVariant
  }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(999.dp),
    color = bg,
    border = BorderStroke(1.dp, fg.copy(alpha = 0.28f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = fg,
        modifier = Modifier.size(14.dp),
      )
      Spacer(modifier = Modifier.width(5.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
