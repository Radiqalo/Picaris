package io.github.radiqalo.picaris.ui

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.radiqalo.picaris.R
import io.github.radiqalo.picaris.core.*
import io.github.radiqalo.picaris.designsystem.*

@Composable
fun AboutScreen(back: () -> Unit) {
    val context = LocalContext.current
    var licenses by remember { mutableStateOf(false) }
    Column {
        ScreenBar(stringResource(R.string.ui_bed172efc9), back = back, scrollBehavior = null)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = PixivSpacing.section),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.padding(
                    top = PixivSpacing.section,
                    start = PixivSpacing.content,
                    end = PixivSpacing.content,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PixivSpacing.related),
            ) {
                Image(
                    painterResource(R.drawable.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp).clip(MaterialTheme.shapes.large),
                )
                Text("Picaris", style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(PixivSpacing.section))
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = PixivSpacing.content),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.padding(vertical = PixivSpacing.compact)) {
                    AboutInfoRow(
                        stringResource(R.string.about_version),
                        io.github.radiqalo.picaris.BuildConfig.VERSION_NAME,
                    )
                    AboutInfoRow(
                        stringResource(R.string.about_appearance),
                        stringResource(R.string.about_appearance_value),
                    )
                }
            }
            Spacer(Modifier.height(PixivSpacing.section))
            Text(
                stringResource(R.string.ui_d70fcfc258),
                Modifier.fillMaxWidth().padding(horizontal = PixivSpacing.content),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(PixivSpacing.section))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = PixivSpacing.content),
                verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact),
            ) {
                OutlinedButton(
                    {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "https://github.com/Radiqalo/Picaris".toUri(),
                            ),
                        )
                    },
                    Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.about_source_code))
                }
            }
            Spacer(Modifier.height(PixivSpacing.section))
            Text(
                stringResource(R.string.about_license_note),
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { licenses = true }
                    .padding(horizontal = PixivSpacing.content, vertical = PixivSpacing.compact),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
    if (licenses) {
        ActionSheet(
            onDismissRequest = { licenses = false },
            title = { Text(stringResource(R.string.about_license)) },
            text = {
                val text =
                    remember {
                        listOf("THIRD_PARTY_NOTICES.md", "GPL-3.0.txt", "Apache-2.0.txt", "MIT.txt")
                            .joinToString("\n\n") { name ->
                                runCatching {
                                    context.assets
                                        .open(name)
                                        .bufferedReader()
                                        .use { it.readText() }
                                }.getOrDefault("")
                            }
                    }
                Text(text, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall)
            },
            confirmButton = {
                TextButton({ licenses = false }) { Text(stringResource(R.string.ui_33246f6a5e)) }
            },
        )
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String,
) {
    Row(
        Modifier.fillMaxWidth().padding(
            horizontal = PixivSpacing.content,
            vertical = PixivSpacing.compact,
        ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}
