package io.github.pixivnext.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import io.github.pixivnext.designsystem.PixivSpacing

/** Official modal sheet with a scrollable body and always reachable actions. */
@Composable
fun ActionSheet(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    ModalBottomSheet(onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )) {
        Column(Modifier.fillMaxWidth().heightIn(max = height * .85f)
            .imePadding().padding(horizontal = PixivSpacing.content),
            verticalArrangement = Arrangement.spacedBy(PixivSpacing.related)) {
            title?.let { ProvideTextStyle(MaterialTheme.typography.headlineSmall) { it() } }
            text?.let {
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    ProvideTextStyle(MaterialTheme.typography.bodyLarge) { it() }
                }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                dismissButton?.invoke()
                confirmButton()
            }
            Spacer(Modifier.navigationBarsPadding().height(PixivSpacing.compact))
        }
    }
}

@Composable
fun DateSelectionSheet(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) = ActionSheet(onDismissRequest, confirmButton, dismissButton,
    title = { Text("选择日期") }, text = content)
