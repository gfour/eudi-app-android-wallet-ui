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

package eu.europa.ec.businesslogic.extension

import eu.europa.ec.businesslogic.validator.model.FilterableItem
import eu.europa.ec.businesslogic.validator.model.FilterableList
import eu.europa.ec.businesslogic.validator.model.SortOrder

fun FilterableList.filterByQuery(searchQuery: String): FilterableList {
    return copy(
        items = items.filter { item ->
            item.attributes.searchTags.any { searchTag ->
                searchTag.contains(
                    other = searchQuery,
                    ignoreCase = true
                )
            }
        }
    )
}

fun FilterableList.sortByOrder(
    sortOrder: SortOrder,
    selector: (FilterableItem) -> String
): FilterableList {
    val sortedItems = when (sortOrder) {
        SortOrder.ASCENDING -> this.items.sortedBy { selector(it) }
        SortOrder.DESCENDING -> this.items.sortedByDescending { selector(it) }
    }
    return this.copy(items = sortedItems)
}