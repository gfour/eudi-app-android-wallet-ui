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

package eu.europa.ec.issuancefeature.interactor.document

import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.commonfeature.ui.document_details.transformer.DocumentDetailsTransformer.toListItemData
import eu.europa.ec.commonfeature.ui.document_details.transformer.transformToDocumentDetailsDocumentItem
import eu.europa.ec.commonfeature.ui.document_success.model.DocumentSuccessItemUi
import eu.europa.ec.commonfeature.ui.request.model.CollapsedUiItem
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.extension.getLocalizedClaimName
import eu.europa.ec.corelogic.extension.localizedIssuerMetadata
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemMainContentData
import eu.europa.ec.uilogic.component.ListItemTrailingContentData
import eu.europa.ec.uilogic.component.RelyingPartyData
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.net.URI

sealed class DocumentIssuanceSuccessInteractorGetUiItemsPartialState {
    data class Success(
        val documentsUi: List<DocumentSuccessItemUi>,
        val headerConfig: ContentHeaderConfig,
    ) : DocumentIssuanceSuccessInteractorGetUiItemsPartialState()

    data class Failed(
        val errorMessage: String
    ) : DocumentIssuanceSuccessInteractorGetUiItemsPartialState()
}

interface DocumentIssuanceSuccessInteractor {
    fun getUiItems(documentIds: List<DocumentId>): Flow<DocumentIssuanceSuccessInteractorGetUiItemsPartialState>
}

class DocumentIssuanceSuccessInteractorImpl(
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val resourceProvider: ResourceProvider,
) : DocumentIssuanceSuccessInteractor {

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    override fun getUiItems(documentIds: List<DocumentId>): Flow<DocumentIssuanceSuccessInteractorGetUiItemsPartialState> {
        return flow {

            val documentsUi = mutableListOf<DocumentSuccessItemUi>()

            var issuerName =
                resourceProvider.getString(R.string.issuance_success_header_issuer_default_name)
            val issuerIsTrusted = false
            var issuerLogo: URI? = null

            val userLocale = resourceProvider.getLocale()

            documentIds.forEach { documentId ->
                try {
                    val document =
                        walletCoreDocumentsController.getDocumentById(documentId = documentId) as IssuedDocument

                    val localizedIssuerMetadata = document.localizedIssuerMetadata(userLocale)

                    localizedIssuerMetadata?.name?.let { safeIssuerName ->
                        issuerName = safeIssuerName
                    }

                    localizedIssuerMetadata?.logo?.uri?.let { safeIssuerLogo ->
                        issuerLogo = safeIssuerLogo
                    }

                    val detailsDocumentItems = document.data.claims
                        .map { claim ->
                            val displayKey: String = claim.metadata?.display.getLocalizedClaimName(
                                userLocale = userLocale,
                                fallback = claim.identifier
                            )

                            transformToDocumentDetailsDocumentItem(
                                displayKey = displayKey,
                                key = claim.identifier,
                                item = claim.value ?: "",
                                resourceProvider = resourceProvider,
                                documentId = documentId
                            )
                        }
                        .toListItemData()

                    val documentUi = DocumentSuccessItemUi(
                        collapsedUiItem = CollapsedUiItem(
                            uiItem = ListItemData(
                                itemId = documentId,
                                mainContentData = ListItemMainContentData.Text(text = document.name),
                                supportingText = resourceProvider.getString(R.string.document_success_collapsed_supporting_text),
                                trailingContentData = ListItemTrailingContentData.Icon(
                                    iconData = AppIcons.KeyboardArrowDown
                                )
                            ),
                            isExpanded = false
                        ),
                        expandedUiItems = detailsDocumentItems
                    )

                    documentsUi.add(documentUi)
                } catch (_: Exception) {
                }
            }

            val headerConfigDescription = if (documentsUi.isEmpty()) {
                resourceProvider.getString(R.string.issuance_success_header_description_when_error)
            } else {
                resourceProvider.getString(R.string.issuance_success_header_description)
            }
            val headerConfig = ContentHeaderConfig(
                description = headerConfigDescription,
                relyingPartyData = RelyingPartyData(
                    logo = issuerLogo,
                    name = issuerName,
                    isVerified = issuerIsTrusted,
                )
            )

            emit(
                DocumentIssuanceSuccessInteractorGetUiItemsPartialState.Success(
                    documentsUi = documentsUi,
                    headerConfig = headerConfig,
                )
            )
        }.safeAsync {
            DocumentIssuanceSuccessInteractorGetUiItemsPartialState.Failed(
                errorMessage = it.localizedMessage ?: genericErrorMsg
            )
        }
    }
}