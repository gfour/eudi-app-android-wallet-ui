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

package eu.europa.ec.dashboardfeature.ui.dashboard_new

import android.net.Uri
import eu.europa.ec.commonfeature.config.OfferUiConfig
import eu.europa.ec.commonfeature.config.PresentationMode
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.corelogic.di.getOrCreatePresentationScope
import eu.europa.ec.dashboardfeature.interactor.DashboardInteractorNew
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemLeadingContentData
import eu.europa.ec.uilogic.component.ListItemMainContentData
import eu.europa.ec.uilogic.component.ListItemTrailingContentData
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.helper.DeepLinkType
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.hasDeepLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import org.koin.android.annotation.KoinViewModel

data class State(

    // side menu
    val isSideMenuVisible: Boolean = false,
    val sideMenuTitle: String = "",
    val sideMenuOptions: List<ListItemData>
) : ViewState

sealed class Event : ViewEvent {
    data class Init(val deepLinkUri: Uri?) : Event()
    data object Pop : Event()

    // side menu events
    sealed class SideMenu : Event() {
        data object Show : SideMenu()
        data object Hide : SideMenu()
        data object OpenChangeQuickPin : SideMenu()
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

        data class OpenDeepLinkAction(val deepLinkUri: Uri, val arguments: String?) :
            Navigation()

        data object OnAppSettings : Navigation()
        data object OnSystemSettings : Navigation()
    }
}

@KoinViewModel
class DashboardViewModelNew(
    private val interactor: DashboardInteractorNew,
    private val uiSerializer: UiSerializer,
    private val resourceProvider: ResourceProvider,
) : MviViewModel<Event, State, Effect>() {
    override fun setInitialState(): State {
        return State(
            sideMenuTitle = resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.dashboard_side_menu_title),
            sideMenuOptions = listOf(
                ListItemData(
                    itemId = "changePinId",
                    mainContentData = ListItemMainContentData.Text(
                        text = resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.dashboard_side_menu_change_pin)
                    ),
                    leadingContentData = ListItemLeadingContentData.Icon(
                        iconData = AppIcons.ChangePin
                    ),
                    trailingContentData = ListItemTrailingContentData.Icon(
                        iconData = AppIcons.KeyboardArrowRight
                    )
                )
            )
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> handleDeepLink(event.deepLinkUri)
            is Event.Pop -> setEffect { Effect.Navigation.Pop }
            Event.SideMenu.OpenChangeQuickPin -> {
                // TODO navigate to change pin
            }

            Event.SideMenu.Hide -> {
                setState { copy(isSideMenuVisible = false) }
            }

            Event.SideMenu.Show -> {
                setState { copy(isSideMenuVisible = true) }
            }
        }
    }

    private fun handleDeepLink(deepLinkUri: Uri?) {
        deepLinkUri?.let { uri ->
            hasDeepLink(uri)?.let {
                val arguments: String? = when (it.type) {
                    DeepLinkType.OPENID4VP -> {
                        getOrCreatePresentationScope()
                        generateComposableArguments(
                            mapOf(
                                RequestUriConfig.serializedKeyName to uiSerializer.toBase64(
                                    RequestUriConfig(
                                        PresentationMode.OpenId4Vp(
                                            uri.toString(),
                                            DashboardScreens.Dashboard.screenRoute
                                        )
                                    ),
                                    RequestUriConfig.Parser
                                )
                            )
                        )
                    }

                    DeepLinkType.CREDENTIAL_OFFER -> generateComposableArguments(
                        mapOf(
                            OfferUiConfig.serializedKeyName to uiSerializer.toBase64(
                                OfferUiConfig(
                                    offerURI = it.link.toString(),
                                    onSuccessNavigation = ConfigNavigation(
                                        navigationType = NavigationType.PopTo(
                                            screen = DashboardScreens.Dashboard
                                        )
                                    ),
                                    onCancelNavigation = ConfigNavigation(
                                        navigationType = NavigationType.Pop
                                    )
                                ),
                                OfferUiConfig.Parser
                            )
                        )
                    )

                    else -> null
                }
                setEffect {
                    Effect.Navigation.OpenDeepLinkAction(
                        deepLinkUri = uri,
                        arguments = arguments
                    )
                }
            }
        }
    }
}