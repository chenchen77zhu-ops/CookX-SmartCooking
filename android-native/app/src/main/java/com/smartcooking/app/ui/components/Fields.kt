package com.smartcooking.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.smartcooking.app.ui.theme.CookX
import com.smartcooking.app.ui.theme.CookXShapes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CookX.Primary,
    unfocusedBorderColor = CookX.BorderStrong,
    disabledBorderColor = CookX.Border,
    focusedContainerColor = CookX.Surface,
    unfocusedContainerColor = CookX.Surface,
    disabledContainerColor = CookX.SurfaceMuted,
    focusedLabelColor = CookX.Primary,
    cursorColor = CookX.Primary,
)

@Composable
fun CookXTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    supporting: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLength: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    clearable: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValueChange(if (maxLength != null) v.take(maxLength) else v) },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = CookX.TextTertiary) } },
        leadingIcon = leadingIcon?.let { { Icon(it, null, tint = CookX.TextSecondary) } },
        trailingIcon = when {
            password -> { { IconButton({ visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "显示密码", tint = CookX.TextSecondary) } } }
            clearable && value.isNotEmpty() && enabled -> { { IconButton({ onValueChange("") }) { Icon(Icons.Outlined.Close, "清空", tint = CookX.TextSecondary) } } }
            else -> null
        },
        supportingText = when {
            error != null -> { { Text(error) } }
            supporting != null || maxLength != null && !singleLine -> { {
                Row(Modifier.fillMaxWidth()) {
                    Text(supporting.orEmpty(), Modifier.weight(1f))
                    if (maxLength != null && !singleLine) Text("${value.length}/$maxLength")
                }
            } }
            else -> null
        },
        isError = error != null,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        shape = CookXShapes.Input,
        colors = fieldColors(),
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
        modifier = modifier.fillMaxWidth(),
    )
}

data class Choice<T>(val value: T, val label: String)

@Composable
fun <T> DropdownField(
    label: String,
    value: T,
    options: List<Choice<T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.value == value }?.label ?: ""
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            supportingText = supporting?.let { { Text(it) } },
            shape = CookXShapes.Input,
            colors = fieldColors(),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }, containerColor = CookX.Surface) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, fontWeight = if (option.value == value) FontWeight.SemiBold else FontWeight.Normal, color = if (option.value == value) CookX.Primary else CookX.Text) },
                    onClick = { onSelect(option.value); expanded = false },
                )
            }
        }
    }
}

private val localFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
private val displayFormat = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")

/**
 * Local date-time input stored as "yyyy-MM-ddTHH:mm" (the web `datetime-local` format);
 * blank means unknown and can be cleared.
 */
@Composable
fun DateTimeField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dateOnly: Boolean = false,
    supporting: String? = null,
) {
    var pickingDate by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    val parsed = remember(value) {
        runCatching { if (dateOnly) LocalDate.parse(value).atStartOfDay() else LocalDateTime.parse(value, localFormat) }.getOrNull()
    }
    val shown = parsed?.let { if (dateOnly) it.toLocalDate().toString() else displayFormat.format(it) } ?: ""
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            placeholder = { Text("未知", color = CookX.TextTertiary) },
            leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, tint = CookX.TextSecondary) },
            trailingIcon = if (value.isNotBlank() && enabled) { { IconButton({ onValueChange("") }) { Icon(Icons.Outlined.Close, "清空日期", tint = CookX.TextSecondary) } } } else null,
            supportingText = supporting?.let { { Text(it) } },
            shape = CookXShapes.Input,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent tap target over the text area (leaving the clear button reachable).
        Box(Modifier.matchParentSize().padding(end = if (value.isNotBlank()) 52.dp else 0.dp).clip(CookXShapes.Input).clickable(enabled = enabled) { pickingDate = true })
    }
    if (pickingDate) {
        val initial = (parsed ?: LocalDateTime.now()).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton({
                    val millis = state.selectedDateMillis
                    pickingDate = false
                    if (millis != null) {
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        if (dateOnly) onValueChange(date.toString()) else pickedDate = date
                    }
                }) { Text(if (dateOnly) "确定" else "下一步", color = CookX.Primary) }
            },
            dismissButton = { TextButton({ pickingDate = false }) { Text("取消", color = CookX.TextSecondary) } },
        ) { DatePicker(state) }
    }
    pickedDate?.let { date ->
        val initialTime = parsed?.toLocalTime() ?: LocalTime.now(ZoneId.systemDefault())
        val state = rememberTimePickerState(initialHour = initialTime.hour, initialMinute = initialTime.minute, is24Hour = true)
        Dialog(onDismissRequest = { pickedDate = null }) {
            Column(Modifier.clip(CookXShapes.Large).background(CookX.Surface).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("选择时间", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                TimePicker(state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ pickedDate = null }) { Text("取消", color = CookX.TextSecondary) }
                    TextButton({
                        onValueChange(localFormat.format(date.atTime(state.hour, state.minute)))
                        pickedDate = null
                    }) { Text("确定", color = CookX.Primary) }
                }
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, enabled: Boolean = true) {
    Row(
        modifier.fillMaxWidth().clip(CookXShapes.Tile).clickable(enabled) { onChange(!checked) }.padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = CookX.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = CookX.Primary, checkedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFE4E7E3), uncheckedBorderColor = Color.Transparent, uncheckedThumbColor = Color.White),
        )
    }
}

@Composable
fun CheckRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, enabled: Boolean = true) {
    Row(
        modifier.fillMaxWidth().clip(CookXShapes.Small).clickable(enabled) { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onChange, enabled = enabled, colors = CheckboxDefaults.colors(checkedColor = CookX.Primary, uncheckedColor = CookX.BorderStrong))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = CookX.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
        }
    }
}

/** Pill segmented control (e.g. 大家的作品 / 我的作品 / 近七天热门). */
@Composable
fun <T> SegmentedTabs(options: List<Choice<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier.fillMaxWidth().clip(CookXShapes.Button).background(Color(0xFFEDEBE4)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val active = option.value == selected
            val bg by animateColorAsState(if (active) CookX.Surface else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).clip(CookXShapes.Input).background(bg)
                    .then(if (active) Modifier.border(1.dp, CookX.Border, CookXShapes.Input) else Modifier)
                    .clickable(enabled) { onSelect(option.value) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(option.label, fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, color = if (active) CookX.Primary else CookX.TextSecondary, maxLines = 1)
            }
        }
    }
}

/** Horizontally scrolling filter chips with optional counts (fridge categories). */
@Composable
fun <T> FilterChips(options: List<Triple<T, String, Int?>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label, count) ->
            val active = value == selected
            Row(
                Modifier.clip(CookXShapes.Pill)
                    .background(if (active) CookX.Primary else CookX.Surface)
                    .border(1.dp, if (active) CookX.Primary else CookX.BorderStrong, CookXShapes.Pill)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else CookX.TextBody)
                if (count != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.clip(CookXShapes.Pill).background(if (active) Color.White.copy(alpha = 0.2f) else CookX.Mint).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        Text("$count", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else CookX.Primary)
                    }
                }
            }
        }
    }
}

/** Small numeric stepper for integer quantities. */
@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, min: Int = 1, max: Int = 9999, enabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(CookXShapes.Small).border(1.dp, CookX.BorderStrong, CookXShapes.Small)) {
        Box(Modifier.size(34.dp).clickable(enabled && value > min) { onChange(value - 1) }, contentAlignment = Alignment.Center) { Text("−", fontSize = 18.sp, color = CookX.Primary) }
        Text("$value", Modifier.width(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold)
        Box(Modifier.size(34.dp).clickable(enabled && value < max) { onChange(value + 1) }, contentAlignment = Alignment.Center) { Text("+", fontSize = 18.sp, color = CookX.Primary) }
    }
}
