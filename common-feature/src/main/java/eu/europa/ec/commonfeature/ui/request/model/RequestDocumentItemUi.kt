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

package eu.europa.ec.commonfeature.ui.request.model

import eu.europa.ec.commonfeature.util.keyIsBase64
import eu.europa.ec.corelogic.model.DocType
import eu.europa.ec.eudi.wallet.document.Document
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.eudi.wallet.document.ElementIdentifier
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.uilogic.component.ListItemData

//TODO should probably rename data class
data class RequestDocumentItemUi<T>(
    val id: String,
    val domainPayload: DocumentItemDomainPayload,
    val documentDetailsUiItem: ListItemData,
    val event: T? = null
) {
    val keyIsBase64: Boolean
        get() {
            return keyIsBase64(domainPayload.elementIdentifier)
        }
}

data class DocumentItemDomainPayload(
    val docId: String,
    val docType: DocType,
    val namespace: String,
    val elementIdentifier: ElementIdentifier
) {
    // We need to override equals in order for "groupBy" internal comparisons
    override fun equals(other: Any?): Boolean {
        return if (other is DocumentItemDomainPayload) {
            other.docId == this.docId
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return docId.hashCode()
    }
}

fun <T> toRequestDocumentItemUi(
    uID: String,
    docPayload: DocumentItemDomainPayload,
    documentDetailsUiItem: ListItemData,
    event: T?
): RequestDocumentItemUi<T> {
    return RequestDocumentItemUi(
        id = uID,
        domainPayload = docPayload,
        documentDetailsUiItem = documentDetailsUiItem,
        event = event,
    )
}

val Document.docType: String
    get() = (this.format as? MsoMdocFormat)?.docType.orEmpty()

fun produceDocUID(
    elementIdentifier: ElementIdentifier,
    documentId: DocumentId,
    docType: String
): String =
    docType + elementIdentifier + documentId