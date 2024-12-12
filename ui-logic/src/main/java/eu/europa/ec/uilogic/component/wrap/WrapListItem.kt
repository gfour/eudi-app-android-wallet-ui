/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.uilogic.component.wrap

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.europa.ec.uilogic.component.ListItem
import eu.europa.ec.uilogic.component.ListItemData

@Composable
fun <T> WrapListItem(
    item: ListItemData<T>,
    modifier: Modifier = Modifier,
    hideSensitiveContent: Boolean = false,
    mainTextVerticalPadding: Int? = null,
    onEventSend: (T) -> Unit
) {
    WrapCard(
        modifier = modifier,
        onClick = { onEventSend(item.event) }, //TODO is this ok, 2 times the same event?
    ) {
        ListItem(
            item = item,
            hideSensitiveContent = hideSensitiveContent,
            mainTextVerticalPadding = mainTextVerticalPadding,
            onEventSend = null,
            //clickAreas = emptyList()
        )
    }
}


/*
@ThemeModePreviews
@Composable
private fun WrapListItemPreview(
    @PreviewParameter(TextLengthPreviewProvider::class) text: String
) {
    PreviewTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
        ) {
            WrapListItem(
                modifier = Modifier.fillMaxWidth(),
                item = ListItemData(
                    mainText = "Main text $text",
                )
            )
            WrapListItem(
                modifier = Modifier.fillMaxWidth(),
                item = ListItemData(
                    mainText = "Main text $text",
                    overlineText = "",
                    supportingText = "",
                )
            )
            WrapListItem(
                modifier = Modifier.fillMaxWidth(),
                item = ListItemData(
                    mainText = "Main text $text",
                    overlineText = "Overline text $text",
                    supportingText = "Supporting text $text",
                    leadingIcon = AppIcons.Sign,
                    trailingContentData = ListItemTrailingContentData.Icon(
                        iconData = AppIcons.KeyboardArrowRight,
                    ),
                )
            )
            WrapListItem(
                modifier = Modifier.fillMaxWidth(),
                item = ListItemData(
                    mainText = "Main text $text",
                    supportingText = "Supporting text $text",
                    trailingContentData = ListItemTrailingContentData.Icon(
                        iconData = AppIcons.KeyboardArrowRight,
                    ),
                )
            )
        }
    }
}*/
