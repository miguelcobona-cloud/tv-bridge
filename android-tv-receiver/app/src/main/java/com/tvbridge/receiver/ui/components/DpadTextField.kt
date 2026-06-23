package com.tvbridge.receiver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvbridge.receiver.ui.theme.TvBridgeFocusBorder
import com.tvbridge.receiver.ui.theme.TvBridgeSurface
import com.tvbridge.receiver.ui.theme.TvBridgeTextSecondary

/**
 * Campo de texto optimizado para control remoto (D-Pad).
 * Muestra borde de foco visible para navegación con mando a distancia.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DpadTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (isFocused) TvBridgeFocusBorder else TvBridgeTextSecondary.copy(alpha = 0.4f)
    val borderWidth = if (isFocused) 3.dp else 1.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(TvBridgeSurface, shape)
            .border(borderWidth, borderColor, shape)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        if (value.isEmpty() && !isFocused) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = TvBridgeTextSecondary,
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            interactionSource = interactionSource,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onImeAction() },
                onNext = { onImeAction() },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusable(interactionSource = interactionSource)
                .onFocusChanged { /* borde reactivo vía interactionSource */ },
        )
    }
}
