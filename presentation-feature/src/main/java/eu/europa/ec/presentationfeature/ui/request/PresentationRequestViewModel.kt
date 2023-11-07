/*
 *
 *  * Copyright (c) 2023 European Commission
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package eu.europa.ec.presentationfeature.ui.request

import androidx.lifecycle.viewModelScope
import eu.europa.ec.commonfeature.config.BiometricUiConfig
import eu.europa.ec.presentationfeature.interactor.PresentationInteractor
import eu.europa.ec.presentationfeature.interactor.PresentationInteractorPartialState
import eu.europa.ec.presentationfeature.ui.request.model.PresentationRequestDataUi
import eu.europa.ec.presentationfeature.ui.request.transformer.PresentationRequestTransformer
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.CommonScreens
import eu.europa.ec.uilogic.navigation.PresentationScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

data class State(
    val isLoading: Boolean = true,
    val isShowingFullUserInfo: Boolean = false,
    val error: ContentErrorConfig? = null,
    val isBottomSheetOpen: Boolean = false,
    val sheetContent: PresentationRequestBottomSheetContent = PresentationRequestBottomSheetContent.SUBTITLE,

    val screenTitle: String,
    val screenSubtitle: String,
    val screenClickableSubtitle: String,
    val warningText: String,
    val biometrySubtitle: String,
    val quickPinSubtitle: String,

    val items: List<PresentationRequestDataUi<Event>> = emptyList(),
) : ViewState

sealed class Event : ViewEvent {
    data object Init : Event()
    data object DismissError : Event()
    data object GoBack : Event()
    data object ChangeContentVisibility : Event()
    data object ExpandOrCollapseRequiredDataList : Event()
    data class UserIdentificationClicked(
        val itemId: Int
    ) : Event()

    data object SubtitleClicked : Event()
    data object PrimaryButtonPressed : Event()
    data object SecondaryButtonPressed : Event()

    sealed class BottomSheet : Event() {
        data class UpdateBottomSheetState(val isOpen: Boolean) : Event()
        sealed class Cancel : BottomSheet() {
            data object PrimaryButtonPressed : Event()
            data object SecondaryButtonPressed : Event()
        }

        sealed class Subtitle : BottomSheet() {
            data object PrimaryButtonPressed : Event()
        }
    }
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(
            val screenRoute: String
        ) : Navigation()

        data object Pop : Navigation()
    }

    data object ShowBottomSheet : Effect()
    data object CloseBottomSheet : Effect()
}

enum class PresentationRequestBottomSheetContent {
    SUBTITLE, CANCEL
}

@KoinViewModel
class PresentationRequestViewModel(
    private val interactor: PresentationInteractor,
    private val resourceProvider: ResourceProvider,
    private val uiSerializer: UiSerializer
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State {
        return State(
            screenTitle = resourceProvider.getString(R.string.online_authentication_userData_title),
            screenSubtitle = resourceProvider.getString(R.string.online_authentication_userData_subtitle_one),
            screenClickableSubtitle = resourceProvider.getString(R.string.online_authentication_userData_subtitle_two),
            warningText = resourceProvider.getString(R.string.online_authentication_userData_warning_text),
            biometrySubtitle = resourceProvider.getString(R.string.online_authentication_biometry_share_subtitle),
            quickPinSubtitle = resourceProvider.getString(R.string.online_authentication_quick_pin_share_subtitle)
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> {
                fetchUserData(event)
            }

            is Event.DismissError -> {
                setState {
                    copy(error = null)
                }
            }

            is Event.GoBack -> {
                setState {
                    copy(error = null)
                }
                setEffect {
                    Effect.Navigation.Pop
                }
            }

            is Event.ChangeContentVisibility -> {
                setState {
                    copy(isShowingFullUserInfo = !isShowingFullUserInfo)
                }
            }

            is Event.ExpandOrCollapseRequiredDataList -> {
                expandOrCollapseRequiredDataList(items = viewState.value.items)
            }

            is Event.UserIdentificationClicked -> {
                updateUserIdentificationItem(id = event.itemId)
            }

            is Event.SubtitleClicked -> {
                showBottomSheet(sheetContent = PresentationRequestBottomSheetContent.SUBTITLE)
            }

            is Event.PrimaryButtonPressed -> {
                setEffect {
                    Effect.Navigation.SwitchScreen(
                        screenRoute = generateComposableNavigationLink(
                            screen = CommonScreens.Biometric,
                            arguments = generateComposableArguments(
                                mapOf(
                                    BiometricUiConfig.serializedKeyName to uiSerializer.toBase64(
                                        BiometricUiConfig(
                                            title = viewState.value.screenTitle,
                                            subTitle = viewState.value.biometrySubtitle,
                                            quickPinOnlySubTitle = viewState.value.quickPinSubtitle,
                                            isPreAuthorization = false,
                                            shouldInitializeBiometricAuthOnCreate = true,
                                            onSuccessNavigation = ConfigNavigation(
                                                navigationType = NavigationType.PUSH,
                                                screenToNavigate = PresentationScreens.Loading
                                            ),
                                            onBackNavigation = ConfigNavigation(
                                                navigationType = NavigationType.POP,
                                                screenToNavigate = PresentationScreens.Request
                                            )
                                        ),
                                        BiometricUiConfig.Parser
                                    ).orEmpty()
                                )
                            )
                        )
                    )
                }
            }

            is Event.SecondaryButtonPressed -> {
                showBottomSheet(sheetContent = PresentationRequestBottomSheetContent.CANCEL)
            }

            is Event.BottomSheet.UpdateBottomSheetState -> {
                setState {
                    copy(isBottomSheetOpen = event.isOpen)
                }
            }

            is Event.BottomSheet.Cancel.PrimaryButtonPressed -> {
                hideBottomSheet()
            }

            is Event.BottomSheet.Cancel.SecondaryButtonPressed -> {
                hideBottomSheet()
                setEffect {
                    Effect.Navigation.Pop
                }
            }

            is Event.BottomSheet.Subtitle.PrimaryButtonPressed -> {
                hideBottomSheet()
            }
        }
    }

    private fun expandOrCollapseRequiredDataList(items: List<PresentationRequestDataUi<Event>>) {
        val updatedItems = items.map { item ->
            if (item is PresentationRequestDataUi.RequiredFields) {
                item.copy(
                    requiredFieldsItemUi = item.requiredFieldsItemUi
                        .copy(expanded = !item.requiredFieldsItemUi.expanded)
                )
            } else {
                item
            }
        }
        setState {
            copy(items = updatedItems)
        }
    }

    private fun fetchUserData(event: Event) {
        setState {
            copy(
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            interactor.getUserData().collect { response ->
                when (response) {
                    is PresentationInteractorPartialState.Failure -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = ContentErrorConfig(
                                    onRetry = { setEvent(event) },
                                    errorSubTitle = response.error,
                                    onCancel = { setEvent(Event.GoBack) }
                                )
                            )
                        }
                    }

                    is PresentationInteractorPartialState.Success -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = null,
                                items = PresentationRequestTransformer.transformToUiItems(
                                    userDataDomain = response.userDataDomain
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun updateUserIdentificationItem(id: Int) {
        val items: List<PresentationRequestDataUi<Event>> = viewState.value.items
        val updatedList = items.map { item ->
            if (item is PresentationRequestDataUi.OptionalField
                && id == item.optionalFieldItemUi.userIdentificationUi.id
            ) {
                val itemCurrentCheckedState = item.optionalFieldItemUi.userIdentificationUi.checked
                val updatedUiItem = item.optionalFieldItemUi.userIdentificationUi.copy(
                    checked = !itemCurrentCheckedState
                )
                item.copy(
                    optionalFieldItemUi = item.optionalFieldItemUi
                        .copy(userIdentificationUi = updatedUiItem)
                )
            } else {
                item
            }
        }
        setState {
            copy(items = updatedList)
        }
    }

    private fun showBottomSheet(sheetContent: PresentationRequestBottomSheetContent) {
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