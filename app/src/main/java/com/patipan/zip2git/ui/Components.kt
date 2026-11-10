package com.patipan.zip2git.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CardShape = RoundedCornerShape(28.dp)
internal val FieldShape = RoundedCornerShape(22.dp)

private fun glassFill() = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f)))
private fun glassEdge() = Brush.linearGradient(
    listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.20f)),
)

/** Frosted-glass card: translucent tint + light gradient + bright gradient hairline border. */
@Composable
internal fun CardBlock(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(bottom = 14.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(GlassTint)
            .background(glassFill())
            .border(1.dp, glassEdge(), CardShape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Accent, AccentDeep))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
        }
        content()
    }
}

@Composable
private fun glassButtonColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(
    containerColor = Color.White.copy(alpha = 0.09f),
    contentColor = TextPrimary,
    disabledContainerColor = Color.White.copy(alpha = 0.04f),
    disabledContentColor = Color.White.copy(alpha = 0.35f),
)

private fun glassButtonBorder(enabled: Boolean) = BorderStroke(
    1.dp,
    Brush.verticalGradient(
        listOf(Color.White.copy(alpha = if (enabled) 0.42f else 0.14f), Color.White.copy(alpha = 0.08f)),
    ),
)

@Composable
internal fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = CircleShape,
        colors = glassButtonColors(),
        border = glassButtonBorder(enabled),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, maxLines = 1)
    }
}

/** Primary action: blue → violet gradient pill. */
@Composable
internal fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 54.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .background(Brush.horizontalGradient(listOf(Color(0xFF2F8CFF), Violet)), CircleShape),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Color.White,
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
internal fun glassFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = Color.White.copy(alpha = 0.07f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.07f),
    cursorColor = AccentLight,
    focusedBorderColor = AccentLight,
    unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
    focusedLabelColor = AccentLight,
    unfocusedLabelColor = Muted,
    focusedTrailingIconColor = TextPrimary,
    unfocusedTrailingIconColor = TextPrimary,
)

@Composable
internal fun PickerField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = FieldShape,
            colors = glassButtonColors(),
            border = glassButtonBorder(enabled),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, color = if (enabled) Muted else Muted.copy(alpha = 0.5f), fontSize = 12.sp)
                Text(value, maxLines = 1, fontSize = 16.sp)
            }
            Icon(Icons.Default.KeyboardArrowDown, null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
internal fun SummaryChip(text: String, tint: Color) {
    Surface(
        color = tint.copy(alpha = 0.16f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.40f)),
    ) {
        Text(
            text,
            color = tint,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
