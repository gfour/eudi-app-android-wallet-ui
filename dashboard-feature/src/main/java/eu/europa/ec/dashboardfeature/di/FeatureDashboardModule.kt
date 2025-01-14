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

package eu.europa.ec.dashboardfeature.di

import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.dashboardfeature.interactor.DashboardInteractor
import eu.europa.ec.dashboardfeature.interactor.DashboardInteractorImpl
import eu.europa.ec.dashboardfeature.interactor.DashboardInteractorNew
import eu.europa.ec.dashboardfeature.interactor.DashboardInteractorNewImpl
import eu.europa.ec.dashboardfeature.interactor.DocumentSignInteractor
import eu.europa.ec.dashboardfeature.interactor.DocumentSignInteractorImpl
import eu.europa.ec.dashboardfeature.interactor.DocumentsInteractor
import eu.europa.ec.dashboardfeature.interactor.DocumentsInteractorImpl
import eu.europa.ec.dashboardfeature.interactor.HomeInteractor
import eu.europa.ec.dashboardfeature.interactor.HomeInteractorImpl
import eu.europa.ec.dashboardfeature.interactor.TransactionsInteractor
import eu.europa.ec.dashboardfeature.interactor.TransactionsInteractorImpl
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module

@Module
@ComponentScan("eu.europa.ec.dashboardfeature")
class FeatureDashboardModule

@Factory
fun provideDashboardInteractor(
    resourceProvider: ResourceProvider,
    walletCoreDocumentsController: WalletCoreDocumentsController,
    walletCoreConfig: WalletCoreConfig,
    configLogic: ConfigLogic,
    logController: LogController,
): DashboardInteractor =
    DashboardInteractorImpl(
        resourceProvider,
        walletCoreDocumentsController,
        walletCoreConfig,
        configLogic,
        logController
    )

@Factory
fun provideDashboardInteractorNew(): DashboardInteractorNew = DashboardInteractorNewImpl()

@Factory
fun provideHomeInteractor(
    resourceProvider: ResourceProvider,
    walletCoreDocumentsController: WalletCoreDocumentsController,
    walletCoreConfig: WalletCoreConfig,
): HomeInteractor = HomeInteractorImpl(
    resourceProvider,
    walletCoreDocumentsController,
    walletCoreConfig,
)

@Factory
fun provideDocumentsInteractor(): DocumentsInteractor = DocumentsInteractorImpl()

@Factory
fun provideTransactionInteractor(): TransactionsInteractor = TransactionsInteractorImpl()

@Factory
fun provideDocumentSignInteractor(
    resourceProvider: ResourceProvider,
): DocumentSignInteractor = DocumentSignInteractorImpl(
    resourceProvider,
)