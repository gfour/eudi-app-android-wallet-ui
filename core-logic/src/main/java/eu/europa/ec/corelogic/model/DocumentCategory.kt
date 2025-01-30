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

package eu.europa.ec.corelogic.model

import androidx.annotation.StringRes
import eu.europa.ec.resourceslogic.R

/**
 * Represents a collection of document categories and their associated document identifiers.
 *
 * This class is a value class, meaning it provides a type-safe wrapper around a `Map<DocumentCategory, List<DocumentIdentifier>>`.
 * It allows you to organize documents into categories, where each category is associated with a list of unique document identifiers.
 *
 * The primary purpose of this class is to provide a structured and type-safe way to manage documents categorized by different criteria.
 * It ensures that each category is associated with a distinct list of document identifiers, avoiding potential conflicts or ambiguities.
 *
 * @property value A map where:
 * - The keys are [DocumentCategory] instances, representing the different categories.
 * - The values are lists of [DocumentIdentifier] instances, representing the documents belonging to each category.
 * Each list contains unique identifiers.
 *
 * @constructor Creates a [DocumentCategories] instance from a map of [DocumentCategory] to a list of [DocumentIdentifier].
 * It is recommended to ensure that document identifiers within each list are unique, though this is not enforced by the class itself.
 *
 */
@JvmInline
value class DocumentCategories(
    val value: Map<DocumentCategory, List<DocumentIdentifier>>
)

/**
 * Represents the category of a document.
 * Each category is associated with a string resource ID for localization.
 *
 * @property stringResId The string resource ID associated with this document category.
 * This is used for displaying a user-friendly name for the category in the UI.
 */
sealed class DocumentCategory(
    @StringRes val stringResId: Int,
) {
    data object Government : DocumentCategory(
        stringResId = R.string.document_category_government
    )

    data object Travel : DocumentCategory(
        stringResId = R.string.document_category_travel
    )

    data object Finance : DocumentCategory(
        stringResId = R.string.document_category_finance
    )

    data object Education : DocumentCategory(
        stringResId = R.string.document_category_education
    )

    data object Health : DocumentCategory(
        stringResId = R.string.document_category_health
    )

    data object SocialSecurity : DocumentCategory(
        stringResId = R.string.document_category_social_security
    )

    data object Retail : DocumentCategory(
        stringResId = R.string.document_category_retail
    )

    data object Other : DocumentCategory(
        stringResId = R.string.document_category_other
    )
}