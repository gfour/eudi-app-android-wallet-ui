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

package eu.europa.ec.dashboardfeature.ui.documents

import androidx.lifecycle.viewModelScope
import eu.europa.ec.commonfeature.config.IssuanceFlowUiConfig
import eu.europa.ec.commonfeature.config.QrScanFlow
import eu.europa.ec.commonfeature.config.QrScanUiConfig
import eu.europa.ec.commonfeature.model.DocumentUiIssuanceState
import eu.europa.ec.corelogic.model.DeferredDocumentData
import eu.europa.ec.corelogic.model.FormatType
import eu.europa.ec.dashboardfeature.extensions.getEmptyUIifEmptyList
import eu.europa.ec.dashboardfeature.extensions.search
import eu.europa.ec.dashboardfeature.interactor.DocumentInteractorDeleteDocumentPartialState
import eu.europa.ec.dashboardfeature.interactor.DocumentInteractorGetDocumentsPartialState
import eu.europa.ec.dashboardfeature.interactor.DocumentInteractorRetryIssuingDeferredDocumentsPartialState
import eu.europa.ec.dashboardfeature.interactor.DocumentsInteractor
import eu.europa.ec.dashboardfeature.model.DocumentDetailsItemUi
import eu.europa.ec.dashboardfeature.model.FilterableDocuments
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.DualSelectorButton
import eu.europa.ec.uilogic.component.DualSelectorButtonData
import eu.europa.ec.uilogic.component.ListItemTrailingContentData
import eu.europa.ec.uilogic.component.ModalOptionUi
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.component.wrap.ExpandableListItemData
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.CommonScreens
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.StartupScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

data class State(
    val isLoading: Boolean,
    val error: ContentErrorConfig? = null,
    val isBottomSheetOpen: Boolean = false,
    val sheetContent: DocumentsBottomSheetContent = DocumentsBottomSheetContent.Filters(filters = emptyList()),

    val documents: List<DocumentDetailsItemUi> = emptyList(),
    val deferredFailedDocIds: List<DocumentId> = emptyList(),
    val allowUserInteraction: Boolean = true, //TODO

    val filters: List<ExpandableListItemData> = emptyList(),
    val isFilteringActive: Boolean,
    val queryText: String = "",
    val sortingOrderButtonData: DualSelectorButtonData,
) : ViewState

sealed class Event : ViewEvent {
    data object GetDocuments : Event()
    data object OnPause : Event()
    data class TryIssuingDeferredDocuments(val deferredDocs: Map<DocumentId, FormatType>) : Event()
    data object Pop : Event()
    data class GoToDocumentDetails(val docId: DocumentId) : Event()
    data class OnSearchQueryChanged(val query: String) : Event()
    data class OnFilterSelectionChanged(val filterId: String, val groupId: String) : Event()
    data object OnFiltersReset : Event()
    data object OnFiltersApply : Event()
    data class OnSortingOrderChanged(val sortingOrder: DualSelectorButton) : Event()

    data object AddDocumentPressed : Event()
    data object FiltersPressed : Event()

    sealed class BottomSheet : Event() {
        data class UpdateBottomSheetState(val isOpen: Boolean) : BottomSheet()
        data object Close : BottomSheet()

        sealed class AddDocument : BottomSheet() {
            data object FromList : AddDocument()
            data object ScanQr : AddDocument()
        }

        sealed class DeferredDocument : BottomSheet() {
            sealed class DeferredNotReadyYet(
                open val documentId: DocumentId,
            ) : DeferredDocument() {
                data class DocumentSelected(
                    override val documentId: DocumentId,
                ) : DeferredNotReadyYet(documentId)

                data class PrimaryButtonPressed(
                    override val documentId: DocumentId,
                ) : DeferredNotReadyYet(documentId)

                data class SecondaryButtonPressed(
                    override val documentId: DocumentId,
                ) : DeferredNotReadyYet(documentId)
            }

            data class OptionListItemForSuccessfullyIssuingDeferredDocumentSelected(
                val documentId: DocumentId,
            ) : DeferredDocument()
        }
    }
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data object Pop : Navigation()
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String = DashboardScreens.Dashboard.screenRoute,
            val inclusive: Boolean = false,
        ) : Navigation()
    }

    data class DocumentsFetched(val deferredDocs: Map<DocumentId, FormatType>) : Effect()

    data object ShowBottomSheet : Effect()
    data object CloseBottomSheet : Effect()
}

sealed class DocumentsBottomSheetContent {
    data class Filters(val filters: List<ExpandableListItemData>) : DocumentsBottomSheetContent()
    data object AddDocument : DocumentsBottomSheetContent()
    data class DeferredDocumentPressed(val documentId: DocumentId) : DocumentsBottomSheetContent()
    data class DeferredDocumentsReady(
        val successfullyIssuedDeferredDocuments: List<DeferredDocumentData>,
        val options: List<ModalOptionUi<Event>>,
    ) : DocumentsBottomSheetContent()
}

@KoinViewModel
class DocumentsViewModel(
    val interactor: DocumentsInteractor,
    private val resourceProvider: ResourceProvider,
    private val uiSerializer: UiSerializer,
) : MviViewModel<Event, State, Effect>() {

    private var retryDeferredDocsJob: Job? = null
    private var initialFilters: List<ExpandableListItemData> = emptyList()
    private var allDocuments: FilterableDocuments? = null
    private var filterableDocuments: FilterableDocuments? = null
    private var query: String = ""

    override fun setInitialState(): State {
        return State(
            isLoading = true,
            sortingOrderButtonData = DualSelectorButtonData(
                first = resourceProvider.getString(R.string.documents_screen_filters_ascending),
                second = resourceProvider.getString(R.string.documents_screen_filters_descending),
                selectedButton = DualSelectorButton.FIRST,
            ),
            isFilteringActive = false
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.GetDocuments -> {
                getDocuments(
                    event = event,
                    deferredFailedDocIds = viewState.value.deferredFailedDocIds
                )
            }

            is Event.OnPause -> {
                retryDeferredDocsJob?.cancel()
            }

            is Event.TryIssuingDeferredDocuments -> {
                tryIssuingDeferredDocuments(event, event.deferredDocs)
            }

            is Event.Pop -> setEffect { Effect.Navigation.Pop }

            is Event.GoToDocumentDetails -> {
                goToDocumentDetails(event.docId)
            }

            is Event.AddDocumentPressed -> {
                showBottomSheet(sheetContent = DocumentsBottomSheetContent.AddDocument)
            }

            is Event.FiltersPressed -> {
                //TODO Clear filters and dont apply filtering if user closes filter dialog
                /*setState { copy(showFiltersBottomSheet = event.isOpen) }
                if (!event.isOpen) {
                    interactor.clearFilters {
                        setState { copy(filters = it) }
                    }
                }*/
                showBottomSheet(sheetContent = DocumentsBottomSheetContent.Filters(filters = emptyList()))
            }

            is Event.OnSearchQueryChanged -> {
                query = event.query
                val searchResult =
                    filterableDocuments?.let { interactor.searchDocuments(event.query, it) }
                setState {
                    copy(
                        documents = searchResult?.documents?.documents?.map { it.itemUi }
                            ?: emptyList(),
                        filters = searchResult?.filters ?: emptyList(),
                        queryText = query
                    )
                }
            }

            is Event.OnFilterSelectionChanged -> {
                val updatedFilters =
                    interactor.updateFilters(event.filterId, event.groupId, viewState.value.filters)
                setState { copy(filters = updatedFilters.filters) }
            }

            is Event.OnFiltersApply -> {
                val applied =
                    allDocuments?.let { interactor.applyFilters(it, viewState.value.filters) }
                filterableDocuments = applied?.documents
                val appliedFilters = applied?.filters ?: emptyList()
                setState {
                    copy(
                        documents = filterableDocuments?.search(query)?.documents?.map { it.itemUi }
                            ?: emptyList(),
                        filters = appliedFilters,
                        isFilteringActive = true
                    )
                }
                hideBottomSheet()
            }

            is Event.OnFiltersReset -> {
                val applied = allDocuments?.let { interactor.resetFilters(it, initialFilters) }
                filterableDocuments = allDocuments
                val appliedFilters = applied?.filters ?: emptyList()
                setState {
                    copy(
                        documents = filterableDocuments?.search(query)?.documents?.map { it.itemUi }
                            ?: emptyList(),
                        filters = appliedFilters,
                        isFilteringActive = false
                    )
                }
                hideBottomSheet()
            }

            is Event.OnSortingOrderChanged -> {
                filterableDocuments = filterableDocuments?.copy(sortingOrder = event.sortingOrder)
                setState { copy(sortingOrderButtonData = sortingOrderButtonData.copy(selectedButton = event.sortingOrder)) }
            }

            is Event.BottomSheet.UpdateBottomSheetState -> {
                setState {
                    copy(isBottomSheetOpen = event.isOpen)
                }
            }

            is Event.BottomSheet.Close -> {
                hideBottomSheet()
            }

            is Event.BottomSheet.AddDocument.FromList -> {
                hideBottomSheet()
                goToAddDocument()
            }

            is Event.BottomSheet.AddDocument.ScanQr -> {
                hideBottomSheet()
                goToQrScan()
            }

            is Event.BottomSheet.DeferredDocument.DeferredNotReadyYet.DocumentSelected -> {
                showBottomSheet(
                    sheetContent = DocumentsBottomSheetContent.DeferredDocumentPressed(
                        documentId = event.documentId
                    )
                )
            }

            is Event.BottomSheet.DeferredDocument.DeferredNotReadyYet.PrimaryButtonPressed -> {
                hideBottomSheet()
                deleteDocument(event = event, documentId = event.documentId)
            }

            is Event.BottomSheet.DeferredDocument.DeferredNotReadyYet.SecondaryButtonPressed -> {
                hideBottomSheet()
            }

            is Event.BottomSheet.DeferredDocument.OptionListItemForSuccessfullyIssuingDeferredDocumentSelected -> {
                hideBottomSheet()
                goToDocumentDetails(docId = event.documentId)
            }
        }
    }

    private fun getDocuments(
        event: Event,
        deferredFailedDocIds: List<DocumentId>,
    ) {
        setState {
            copy(
                isLoading = documents.isEmpty(),
                error = null
            )
        }
        viewModelScope.launch {
            interactor.getDocuments(
                viewState.value.sortingOrderButtonData.selectedButton,
                viewState.value.filters,
                query
            )
                .collect { response ->
                    when (response) {
                        is DocumentInteractorGetDocumentsPartialState.Failure -> {
                            setState {
                                copy(
                                    isLoading = false,
                                    error = ContentErrorConfig(
                                        onRetry = { setEvent(event) },
                                        errorSubTitle = response.error,
                                        onCancel = {
                                            setState { copy(error = null) }
                                            setEvent(Event.Pop)
                                        }
                                    )
                                )
                            }
                        }

                        is DocumentInteractorGetDocumentsPartialState.Success -> {
                            val deferredDocs: MutableMap<DocumentId, FormatType> = mutableMapOf()
                            response.allDocuments.documents.filter { document ->
                                document.itemUi.documentIssuanceState == DocumentUiIssuanceState.Pending
                            }.forEach { documentUi ->
                                deferredDocs[documentUi.itemUi.uiData.itemId] =
                                    documentUi.itemUi.documentIdentifier.formatType
                            }

                            filterableDocuments =
                                response.filterableDocuments.generateFailedDeferredDocs(
                                    deferredFailedDocIds
                                )
                            allDocuments = response.allDocuments.generateFailedDeferredDocs(
                                deferredFailedDocIds
                            )
                            val filters =
                                filterableDocuments?.let { interactor.getFilters(it).filters }
                                    ?: emptyList()
                            initialFilters = filters
                            setState {
                                copy(
                                    isLoading = false,
                                    error = null,
                                    documents = filterableDocuments?.search(query)
                                        ?.getEmptyUIifEmptyList(resourceProvider)?.documents?.map { it.itemUi }
                                        ?: emptyList(),
                                    filters = filters,
                                    deferredFailedDocIds = deferredFailedDocIds,
                                    allowUserInteraction = response.shouldAllowUserInteraction,
                                )
                            }

                            setEffect { Effect.DocumentsFetched(deferredDocs) }
                        }
                    }
                }
        }
    }

    private fun FilterableDocuments.generateFailedDeferredDocs(deferredFailedDocIds: List<DocumentId>): FilterableDocuments {
        return copy(documents = documents.map { documentUi ->
            if (documentUi.itemUi.uiData.itemId in deferredFailedDocIds) {
                documentUi.copy(
                    itemUi = documentUi.itemUi.copy(
                        documentIssuanceState = DocumentUiIssuanceState.Failed,
                        uiData = documentUi.itemUi.uiData.copy(
                            supportingText = resourceProvider.getString(R.string.dashboard_document_deferred_failed),
                            trailingContentData = ListItemTrailingContentData.Icon(
                                iconData = AppIcons.ErrorFilled,
                                tint = ThemeColors.error
                            )
                        )
                    )
                )
            } else {
                documentUi
            }
        })
    }

    private fun tryIssuingDeferredDocuments(
        event: Event,
        deferredDocs: Map<DocumentId, FormatType>,
    ) {
        setState {
            copy(
                isLoading = false,
                error = null
            )
        }

        retryDeferredDocsJob?.cancel()
        retryDeferredDocsJob = viewModelScope.launch {
            if (deferredDocs.isEmpty()) {
                return@launch
            }

            delay(5000L)

            interactor.tryIssuingDeferredDocumentsFlow(deferredDocs).collect { response ->
                when (response) {
                    is DocumentInteractorRetryIssuingDeferredDocumentsPartialState.Failure -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = ContentErrorConfig(
                                    onRetry = { setEvent(event) },
                                    errorSubTitle = response.errorMessage,
                                    onCancel = {
                                        setState { copy(error = null) }
                                    }
                                )
                            )
                        }
                    }

                    is DocumentInteractorRetryIssuingDeferredDocumentsPartialState.Result -> {
                        val successDocs = response.successfullyIssuedDeferredDocuments
                        if (successDocs.isNotEmpty()
                            && (!viewState.value.isBottomSheetOpen
                                    || (viewState.value.isBottomSheetOpen
                                    && viewState.value.sheetContent !is DocumentsBottomSheetContent.DeferredDocumentsReady)
                                    )
                        ) {
                            showBottomSheet(
                                sheetContent = DocumentsBottomSheetContent.DeferredDocumentsReady(
                                    successfullyIssuedDeferredDocuments = successDocs,
                                    options = getBottomSheetOptions(
                                        deferredDocumentsData = successDocs
                                    )
                                )
                            )
                        }

                        getDocuments(
                            event = event,
                            deferredFailedDocIds = response.failedIssuedDeferredDocuments
                        )
                    }
                }
            }
        }
    }

    private fun getBottomSheetOptions(deferredDocumentsData: List<DeferredDocumentData>): List<ModalOptionUi<Event>> {
        return deferredDocumentsData.map {
            ModalOptionUi(
                title = it.docName,
                trailingIcon = AppIcons.KeyboardArrowRight,
                event = Event.BottomSheet.DeferredDocument.OptionListItemForSuccessfullyIssuingDeferredDocumentSelected(
                    documentId = it.documentId
                )
            )
        }
    }

    private fun deleteDocument(event: Event, documentId: DocumentId) {
        setState {
            copy(
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            interactor.deleteDocument(
                documentId = documentId
            ).collect { response ->
                when (response) {
                    is DocumentInteractorDeleteDocumentPartialState.AllDocumentsDeleted -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = null
                            )
                        }

                        setEffect {
                            Effect.Navigation.SwitchScreen(
                                screenRoute = StartupScreens.Splash.screenRoute,
                                popUpToScreenRoute = DashboardScreens.Dashboard.screenRoute,
                                inclusive = true
                            )
                        }
                    }

                    is DocumentInteractorDeleteDocumentPartialState.SingleDocumentDeleted -> {
                        getDocuments(
                            event = event,
                            deferredFailedDocIds = viewState.value.deferredFailedDocIds
                        )
                    }

                    is DocumentInteractorDeleteDocumentPartialState.Failure -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = ContentErrorConfig(
                                    onRetry = { setEvent(event) },
                                    errorSubTitle = response.errorMessage,
                                    onCancel = {
                                        setState {
                                            copy(error = null)
                                        }
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun goToDocumentDetails(docId: DocumentId) {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    screen = IssuanceScreens.DocumentDetails,
                    arguments = generateComposableArguments(
                        mapOf(
                            "detailsType" to IssuanceFlowUiConfig.EXTRA_DOCUMENT,
                            "documentId" to docId
                        )
                    )
                )
            )
        }
    }

    private fun goToAddDocument() {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    screen = IssuanceScreens.AddDocument,
                    arguments = generateComposableArguments(
                        mapOf("flowType" to IssuanceFlowUiConfig.EXTRA_DOCUMENT)
                    )
                )
            )
        }
    }

    private fun goToQrScan() {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    screen = CommonScreens.QrScan,
                    arguments = generateComposableArguments(
                        mapOf(
                            QrScanUiConfig.serializedKeyName to uiSerializer.toBase64(
                                QrScanUiConfig(
                                    title = resourceProvider.getString(R.string.issuance_qr_scan_title),
                                    subTitle = resourceProvider.getString(R.string.issuance_qr_scan_subtitle),
                                    qrScanFlow = QrScanFlow.Issuance(IssuanceFlowUiConfig.EXTRA_DOCUMENT)
                                ),
                                QrScanUiConfig.Parser
                            )
                        )
                    )
                ),
                inclusive = false
            )
        }
    }

    private fun showBottomSheet(sheetContent: DocumentsBottomSheetContent) {
        setState {
            copy(sheetContent = sheetContent)
        }
        setEffect {
            Effect.ShowBottomSheet
        }
    }

    private fun hideBottomSheet() {
        setEffect {
            Effect.CloseBottomSheet
        }
    }
}