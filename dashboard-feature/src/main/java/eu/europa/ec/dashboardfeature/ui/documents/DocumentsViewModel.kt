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
import eu.europa.ec.businesslogic.extension.isBeyondNextDays
import eu.europa.ec.businesslogic.extension.isExpired
import eu.europa.ec.businesslogic.extension.isWithinNextDays
import eu.europa.ec.businesslogic.model.FilterAction
import eu.europa.ec.businesslogic.model.FilterGroup
import eu.europa.ec.businesslogic.model.FilterItem
import eu.europa.ec.businesslogic.model.FilterableList
import eu.europa.ec.businesslogic.model.Filters
import eu.europa.ec.businesslogic.model.SortOrder
import eu.europa.ec.commonfeature.config.IssuanceFlowUiConfig
import eu.europa.ec.commonfeature.config.QrScanFlow
import eu.europa.ec.commonfeature.config.QrScanUiConfig
import eu.europa.ec.commonfeature.model.DocumentUiIssuanceState
import eu.europa.ec.corelogic.model.DeferredDocumentData
import eu.europa.ec.corelogic.model.FormatType
import eu.europa.ec.dashboardfeature.interactor.DocumentInteractorDeleteDocumentPartialState
import eu.europa.ec.dashboardfeature.interactor.DocumentInteractorFilterPartialState
import eu.europa.ec.dashboardfeature.interactor.DocumentInteractorGetDocumentsPartialState
import eu.europa.ec.dashboardfeature.interactor.DocumentInteractorRetryIssuingDeferredDocumentsPartialState
import eu.europa.ec.dashboardfeature.interactor.DocumentsInteractor
import eu.europa.ec.dashboardfeature.model.DocumentItemUi
import eu.europa.ec.dashboardfeature.model.DocumentsFilterableAttributes
import eu.europa.ec.dashboardfeature.model.FilterIds
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
import java.time.Instant

data class State(
    val isLoading: Boolean,
    val error: ContentErrorConfig? = null,
    val isBottomSheetOpen: Boolean = false,
    val sheetContent: DocumentsBottomSheetContent = DocumentsBottomSheetContent.Filters(filters = emptyList()),

    val documentsUi: List<DocumentItemUi> = emptyList(),
    val deferredFailedDocIds: List<DocumentId> = emptyList(),
    val searchText: String = "",
    val allowUserInteraction: Boolean = true, //TODO
    val isInitialDocumentLoading: Boolean = true,
    val shouldRevertFilterChanges: Boolean = true,

    val filtersUi: List<ExpandableListItemData> = emptyList(),
    val sortOrder: DualSelectorButtonData,
    val isFilteringActive: Boolean,

    val filters: Filters,
) : ViewState

sealed class Event : ViewEvent {
    data object Init : Event()
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

    data object ResumeOnApplyFilter : Effect()
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

    override fun setInitialState(): State {
        return State(
            isLoading = true,
            sortOrder = DualSelectorButtonData(
                first = resourceProvider.getString(R.string.documents_screen_filters_ascending),
                second = resourceProvider.getString(R.string.documents_screen_filters_descending),
                selectedButton = DualSelectorButton.FIRST,
            ),
            isFilteringActive = false,
            filters = createFilters()
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> {
                filterStateChanged()
            }

            is Event.GetDocuments -> {
                getDocuments(
                    event = event,
                    deferredFailedDocIds = viewState.value.deferredFailedDocIds,
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
                setEvent(Event.OnPause)
                showBottomSheet(sheetContent = DocumentsBottomSheetContent.Filters(filters = emptyList()))
            }

            is Event.OnSearchQueryChanged -> {
                applySearch(event.query)
            }

            is Event.OnFilterSelectionChanged -> {
                updateFilter(event.filterId, event.groupId)
            }

            is Event.OnFiltersApply -> {
                applySelectedFilters()
            }

            is Event.OnFiltersReset -> {
                resetFilters()
            }

            is Event.OnSortingOrderChanged -> {
                sortOrderChanged(event.sortingOrder)
            }

            is Event.BottomSheet.UpdateBottomSheetState -> {
                if (viewState.value.sheetContent is DocumentsBottomSheetContent.Filters
                    && !event.isOpen
                ) {
                    setEffect { Effect.ResumeOnApplyFilter }
                }
                revertFilters(event.isOpen)
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

    private fun filterStateChanged() {
        viewModelScope.launch {
            interactor.onFilterStateChange().collect { result ->
                when (result) {
                    is DocumentInteractorFilterPartialState.FilterApplyResult -> {
                        setState {
                            copy(
                                isFilteringActive = result.hasMoreThanDefaultFilterApplied,
                                documentsUi = result.documents,
                                filtersUi = result.filters,
                                sortOrder = sortOrder.copy(selectedButton = result.sortOrder)
                            )
                        }
                    }

                    is DocumentInteractorFilterPartialState.FilterUpdateResult -> {
                        setState {
                            copy(
                                filtersUi = result.filters,
                                sortOrder = sortOrder.copy(selectedButton = result.sortOrder)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun getDocuments(
        event: Event,
        deferredFailedDocIds: List<DocumentId>,
    ) {
        setState {
            copy(
                isLoading = documentsUi.isEmpty(),
                error = null
            )
        }
        viewModelScope.launch {
            interactor.getDocuments()
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
                            response.allDocuments.items.filter { document ->
                                with(document.payload as DocumentItemUi) {
                                    documentIssuanceState == DocumentUiIssuanceState.Pending
                                }
                            }.forEach { documentItem ->
                                with(documentItem.payload as DocumentItemUi) {
                                    deferredDocs[uiData.itemId] =
                                        documentIdentifier.formatType
                                }
                            }
                            val documentsWithFailed =
                                response.allDocuments.generateFailedDeferredDocs(
                                    deferredFailedDocIds
                                )

                            addIssuerFilters(documentsWithFailed)

                            if (viewState.value.isInitialDocumentLoading) {
                                interactor.initializeFilters(
                                    viewState.value.filters,
                                    documentsWithFailed
                                )
                            } else {
                                interactor.updateLists(documentsWithFailed, viewState.value.filters)
                            }

                            interactor.applyFilters()

                            setState {
                                copy(
                                    isLoading = false,
                                    error = null,
                                    deferredFailedDocIds = deferredFailedDocIds,
                                    allowUserInteraction = response.shouldAllowUserInteraction,
                                    isInitialDocumentLoading = false
                                )
                            }
                            setEffect { Effect.DocumentsFetched(deferredDocs) }
                        }
                    }
                }
        }
    }

    private fun FilterableList.generateFailedDeferredDocs(deferredFailedDocIds: List<DocumentId>): FilterableList {
        return copy(items = items.map { filterableItem ->
            val data = filterableItem.payload as DocumentItemUi
            val failedUiItem = if (data.uiData.itemId in deferredFailedDocIds) {
                data.copy(
                    documentIssuanceState = DocumentUiIssuanceState.Failed,
                    uiData = data.uiData.copy(
                        supportingText = resourceProvider.getString(R.string.dashboard_document_deferred_failed),
                        trailingContentData = ListItemTrailingContentData.Icon(
                            iconData = AppIcons.ErrorFilled,
                            tint = ThemeColors.error
                        )
                    )
                )
            } else {
                data
            }

            filterableItem.copy(payload = failedUiItem)
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
                            deferredFailedDocIds = response.failedIssuedDeferredDocuments,
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
                            deferredFailedDocIds = viewState.value.deferredFailedDocIds,
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

    private fun addIssuerFilters(documents: FilterableList) {
        setState {
            copy(
                filters = filters.copy(filterGroups = filters.filterGroups.map { filterGroup ->
                    if (filterGroup.id == FilterIds.FILTER_BY_ISSUER_GROUP_ID) {
                        filterGroup.copy(
                            filters = documents.items.distinctBy { (it.attributes as DocumentsFilterableAttributes).issuer }
                                .mapNotNull { filterableItem ->
                                    with(filterableItem.attributes as DocumentsFilterableAttributes) {
                                        if (issuer != null) {
                                            FilterItem(
                                                id = issuer,
                                                name = issuer,
                                                selected = false,
                                                filterableAction = FilterAction.Filter<DocumentsFilterableAttributes> { attributes, filter ->
                                                    attributes.issuer == filter.name
                                                }
                                            )
                                        } else {
                                            null
                                        }
                                    }
                                }.toMutableList().apply {
                                    add(
                                        0,
                                        FilterItem(
                                            id = FilterIds.FILTER_BY_ISSUER_ALL,
                                            name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_issuer_all),
                                            selected = true,
                                            filterableAction = FilterAction.Filter<DocumentsFilterableAttributes> { _, _ ->
                                                true // Get all
                                            }
                                        )
                                    )
                                }
                        )
                    } else {
                        filterGroup
                    }
                })
            )
        }
    }

    private fun createFilters(): Filters = Filters(
        filterGroups = listOf(
            // Filter by expiry period
            FilterGroup(
                id = FilterIds.FILTER_BY_PERIOD_GROUP_ID,
                name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_expiry_period),
                filters = listOf(
                    FilterItem(
                        id = FilterIds.FILTER_BY_PERIOD_NEXT_7,
                        name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_expiry_period_1),
                        selected = false,
                        filterableAction = FilterAction.Filter<DocumentsFilterableAttributes> { attributes, _ ->
                            attributes.expiryDate?.isWithinNextDays(7) == true
                        }
                    ),
                    FilterItem(
                        id = FilterIds.FILTER_BY_PERIOD_NEXT_30,
                        name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_expiry_period_2),
                        selected = false,
                        filterableAction = FilterAction.Filter<DocumentsFilterableAttributes> { attributes, _ ->
                            attributes.expiryDate?.isWithinNextDays(30) == true
                        }
                    ),
                    FilterItem(
                        id = FilterIds.FILTER_BY_PERIOD_BEYOND_30,
                        name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_expiry_period_3),
                        selected = false,
                        filterableAction = FilterAction.Filter<DocumentsFilterableAttributes> { attributes, _ ->
                            attributes.expiryDate?.isBeyondNextDays(30) == true
                        }
                    ),
                    FilterItem(
                        id = FilterIds.FILTER_BY_PERIOD_EXPIRED,
                        name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_expiry_period_4),
                        selected = false,
                        filterableAction = FilterAction.Filter<DocumentsFilterableAttributes> { attributes, _ ->
                            attributes.expiryDate?.isExpired() == true
                        }
                    )
                )
            ),
            // Sort
            FilterGroup(
                id = FilterIds.FILTER_SORT_GROUP_ID,
                name = resourceProvider.getString(R.string.documents_screen_filters_sort_by),
                filters = listOf(
                    FilterItem(
                        id = FilterIds.FILTER_SORT_DEFAULT,
                        name = resourceProvider.getString(R.string.documents_screen_filters_sort_default),
                        selected = true,
                        filterableAction = FilterAction.Sort<DocumentsFilterableAttributes, String> { attributes ->
                            attributes.name.lowercase()
                        }
                    ),
                    FilterItem(
                        id = FilterIds.FILTER_SORT_DATE_ISSUED,
                        name = resourceProvider.getString(R.string.documents_screen_filters_sort_date_issued),
                        selected = false,
                        filterableAction = FilterAction.Sort<DocumentsFilterableAttributes, Instant> { attributes ->
                            attributes.issuedDate
                        }
                    ),
                    FilterItem(
                        id = FilterIds.FILTER_SORT_EXPIRY_DATE,
                        name = resourceProvider.getString(R.string.documents_screen_filters_sort_expiry_date),
                        selected = false,
                        filterableAction = FilterAction.Sort<DocumentsFilterableAttributes, Instant> { attributes ->
                            attributes.expiryDate
                        }
                    )
                )
            ),
            // Filter by Issuer
            FilterGroup(
                id = FilterIds.FILTER_BY_ISSUER_GROUP_ID,
                name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_issuer),
                filters = emptyList()
            ),
            // Filter by State
            FilterGroup(
                id = FilterIds.FILTER_BY_STATE_GROUP_ID,
                name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_state),
                filters = listOf(
                    FilterItem(
                        id = FilterIds.FILTER_BY_STATE_VALID,
                        name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_state_valid),
                        selected = false,
                        filterableAction = FilterAction.Filter<DocumentsFilterableAttributes> { attributes, _ ->
                            attributes.expiryDate?.isExpired() == false
                        }
                    ),
                    FilterItem(
                        id = FilterIds.FILTER_BY_STATE_EXPIRED,
                        name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_state_expired),
                        selected = false,
                        filterableAction = FilterAction.Filter<DocumentsFilterableAttributes> { attributes, _ ->
                            attributes.expiryDate?.isExpired() == true
                        }
                    )
                )
            )
        ),
        sortOrder = SortOrder.ASCENDING
    )

    private fun applySearch(queryText: String) {
        interactor.applySearch(queryText)
        setState {
            copy(searchText = queryText)
        }
    }

    private fun updateFilter(filterId: String, groupId: String) {
        setState { copy(shouldRevertFilterChanges = true) }
        interactor.updateFilter(filterGroupId = groupId, filterId = filterId)
    }

    private fun applySelectedFilters() {
        interactor.applyFilters()
        setState {
            copy(
                shouldRevertFilterChanges = false
            )
        }
        hideBottomSheet()
    }

    private fun resetFilters() {
        interactor.resetFilters()
        setState {
            copy(
                isFilteringActive = false,
            )
        }
        hideBottomSheet()
    }

    private fun revertFilters(isOpening: Boolean) {
        if (viewState.value.sheetContent is DocumentsBottomSheetContent.Filters
            && !isOpening
            && viewState.value.shouldRevertFilterChanges
        ) {
            interactor.revertFilters()
            setState { copy(shouldRevertFilterChanges = true) }
        }

        setState {
            copy(isBottomSheetOpen = isOpening)
        }
    }

    private fun sortOrderChanged(orderButton: DualSelectorButton) {
        val sortOrder = when (orderButton) {
            DualSelectorButton.FIRST -> SortOrder.ASCENDING
            DualSelectorButton.SECOND -> SortOrder.DESCENDING
        }
        setState { copy(shouldRevertFilterChanges = true) }
        interactor.updateSortOrder(sortOrder)
    }
}