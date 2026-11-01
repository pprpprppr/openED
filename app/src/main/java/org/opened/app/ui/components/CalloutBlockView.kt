package org.opened.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.opened.app.ui.theme.CalloutCautionBackground
import org.opened.app.ui.theme.CalloutCautionBorder
import org.opened.app.ui.theme.CalloutImportantBackground
import org.opened.app.ui.theme.CalloutImportantBorder
import org.opened.app.ui.theme.CalloutNoteBackground
import org.opened.app.ui.theme.CalloutNoteBorder
import org.opened.app.ui.theme.CalloutTipBackground
import org.opened.app.ui.theme.CalloutTipBorder
import org.opened.app.ui.theme.CalloutWarningBackground
import org.opened.app.ui.theme.CalloutWarningBorder

enum class CalloutType(
    val title: String,
    val borderColor: Color,
    val backgroundColor: Color
) {
    NOTE("NOTE", CalloutNoteBorder, CalloutNoteBackground),
    TIP("TIP", CalloutTipBorder, CalloutTipBackground),
    IMPORTANT("IMPORTANT", CalloutImportantBorder, CalloutImportantBackground),
    WARNING("WARNING", CalloutWarningBorder, CalloutWarningBackground),
    CAUTION("CAUTION", CalloutCautionBorder, CalloutCautionBackground)
}

@Composable
fun CalloutBlockView(
    type: CalloutType,
    customTitle: String? = null,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (type) {
        CalloutType.NOTE -> Icons.Default.Info
        CalloutType.TIP -> Icons.Default.Lightbulb
        CalloutType.IMPORTANT -> Icons.Default.Star
        CalloutType.WARNING -> Icons.Default.Warning
        CalloutType.CAUTION -> Icons.Default.Dangerous
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        color = type.backgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left colored stripe
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(type.borderColor)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = type.title,
                        tint = type.borderColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = customTitle ?: type.title,
                        color = type.borderColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                content()
            }
        }
    }
}
