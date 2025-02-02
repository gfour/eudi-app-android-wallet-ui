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

package eu.europa.ec.corelogic.config

import android.content.Context
import eu.europa.ec.corelogic.BuildConfig
import eu.europa.ec.corelogic.controller.WalletCoreLogController
import eu.europa.ec.eudi.wallet.EudiWalletConfig
import eu.europa.ec.eudi.wallet.issue.openid4vci.OpenId4VciManager
import eu.europa.ec.eudi.wallet.transfer.openid4vp.ClientIdScheme
import eu.europa.ec.eudi.wallet.transfer.openid4vp.EncryptionAlgorithm
import eu.europa.ec.eudi.wallet.transfer.openid4vp.EncryptionMethod
import eu.europa.ec.eudi.wallet.transfer.openid4vp.PreregisteredVerifier
import eu.europa.ec.resourceslogic.R

import android.annotation.SuppressLint
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.logging.Logging
import java.security.SecureRandom
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.security.cert.CertificateException


object ProvideKtorHttpClient {

    @SuppressLint("TrustAllX509TrustManager", "CustomX509TrustManager")
    fun client(): HttpClient {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                @Throws(CertificateException::class)
                override fun checkClientTrusted(
                    chain: Array<java.security.cert.X509Certificate>,
                    authType: String
                ) {
                }

                @Throws(CertificateException::class)
                override fun checkServerTrusted(
                    chain: Array<java.security.cert.X509Certificate>,
                    authType: String
                ) {
                }

                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
                    return arrayOf()
                }
            }
        )

        return HttpClient(Android) {
            install(Logging)
            engine {
                requestConfig
                sslManager = { httpsURLConnection ->
                    httpsURLConnection.sslSocketFactory = SSLContext.getInstance("TLS").apply {
                        init(null, trustAllCerts, SecureRandom())
                    }.socketFactory
                    httpsURLConnection.hostnameVerifier = HostnameVerifier { _, _ -> true }
                }
            }
        }
    }

}

internal class WalletCoreConfigImpl(
    private val context: Context,
    private val walletCoreLogController: WalletCoreLogController
) : WalletCoreConfig {

    private companion object {
        const val OPENID4VP_VERIFIER_API_URI = "https://dev.verifier.eudiw.dev"
        const val OPENID4VP_VERIFIER_LEGAL_NAME = "EUDI Remote Verifier"
        const val OPENID4VP_VERIFIER_CLIENT_ID = "Verifier"
        const val VCI_ISSUER_URL = "https://192.168.134.214:5000"
        const val VCI_CLIENT_ID = "wallet-dev"
        const val AUTHENTICATION_REQUIRED = false
    }

    private var _config: EudiWalletConfig? = null

    override val config: EudiWalletConfig
        get() {
            if (_config == null) {
                _config = EudiWalletConfig.Builder(context)
                    .logger(walletCoreLogController)
                    .userAuthenticationRequired(AUTHENTICATION_REQUIRED)
                    .openId4VpConfig {
                        withEncryptionAlgorithms(listOf(EncryptionAlgorithm.ECDH_ES))
                        withEncryptionMethods(
                            listOf(
                                EncryptionMethod.A128CBC_HS256,
                                EncryptionMethod.A256GCM
                            )
                        )

                        withClientIdSchemes(
                            listOf(
                                ClientIdScheme.X509SanDns,
                                ClientIdScheme.Preregistered(
                                    listOf(
                                        PreregisteredVerifier(
                                            clientId = OPENID4VP_VERIFIER_CLIENT_ID,
                                            verifierApi = OPENID4VP_VERIFIER_API_URI,
                                            legalName = OPENID4VP_VERIFIER_LEGAL_NAME
                                        )
                                    )
                                )
                            )
                        )
                        withScheme(
                            listOf(
                                BuildConfig.OPENID4VP_SCHEME,
                                BuildConfig.EUDI_OPENID4VP_SCHEME,
                                BuildConfig.MDOC_OPENID4VP_SCHEME
                            )
                        )
                    }
                    .openId4VciConfig {
                        issuerUrl(issuerUrl = VCI_ISSUER_URL)
                        clientId(clientId = VCI_CLIENT_ID)
                        authFlowRedirectionURI(BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK)
                        useStrongBoxIfSupported(true)
                        useDPoP(true)
                        parUsage(OpenId4VciManager.Config.ParUsage.IF_SUPPORTED)
                        proofTypes(
                            OpenId4VciManager.Config.ProofType.JWT,
                            OpenId4VciManager.Config.ProofType.CWT
                        )
                    }
                    .ktorHttpClientFactory {
                        ProvideKtorHttpClient.client()
                    }
                    .trustedReaderCertificates(R.raw.eudi_pid_issuer_ut)
                    .build()
            }
            return _config!!
        }
}