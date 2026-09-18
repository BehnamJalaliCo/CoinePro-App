package com.coinepro.core.update

import com.coinepro.core.common.BrandConfig
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Asks the brand host what the newest published build is.
 *
 * Every failure answers **null**, exactly as `EntitlementsGateway` in `:core:account` does and for
 * the same reason: there is one useful thing a caller can do with «the update check did not work»,
 * and it is nothing. A 404 on a host that does not serve the document yet, a timeout on a
 * filtered network, a body that will not parse — all of them mean the reader carries on with the
 * build they have, which is the correct outcome and not an error state anybody needs to see.
 */
interface AppUpdateGateway {
    /** The published build, or null where the host did not answer with one. */
    suspend fun latest(): AppRelease?
}

internal interface AppUpdateApi {
    @GET
    suspend fun latest(@Url url: String): Response<AppReleaseDto>
}

/**
 * The document, field for field.
 *
 * Every field is nullable and defaulted, because this is the one route in the app served by a host
 * that does not exist yet: it will be written against this class rather than the other way round,
 * and a document that arrives with a field missing should be a release this app declines to offer
 * rather than a parse exception in the middle of the safety screen.
 *
 * The names are Gson's `LOWER_CASE_WITH_UNDERSCORES`, set in `NetworkFactory.retrofit`, so
 * `versionCode` on the wire is `version_code`. `docs/web/SERVER.md §4.6` is the contract.
 */
internal data class AppReleaseDto(
    val versionCode: Long? = null,
    val versionName: String? = null,
    val url: String? = null,
    val sha256: String? = null,
    val notesFa: String? = null,
    val notesEn: String? = null,
    val mandatory: Boolean? = null,
)

class NetworkAppUpdateGateway internal constructor(
    private val api: AppUpdateApi,
    private val url: String,
) : AppUpdateGateway {

    override suspend fun latest(): AppRelease? = try {
        val response = api.latest(url)
        response.body()?.takeIf { response.isSuccessful }?.toRelease()
    } catch (error: Throwable) {
        null
    }

    companion object {
        /**
         * Where the document lives.
         *
         * On the brand host rather than on either backend, because it is neither backend's
         * business what an Android app's newest build is — TradeYar serves crypto and CoinePro-FX
         * serves forex, and a reader on one platform must not stop hearing about updates because
         * the other one is down. `docs/web/SERVER.md` gives it to the one server that belongs to
         * the product itself.
         */
        const val MANIFEST_URL: String = "${BrandConfig.WEB_URL}/api/app/latest"

        /**
         * @param retrofit **must not carry the session's bearer token.** The brand host is a third
         *   host as far as either backend's credentials are concerned, and a client that attaches
         *   `Authorization` to every request would hand TradeYar's token to it on a route that has
         *   no use for one. `AppModule` builds an unauthenticated client for exactly this call.
         */
        fun create(retrofit: Retrofit, url: String = MANIFEST_URL): NetworkAppUpdateGateway =
            NetworkAppUpdateGateway(api = retrofit.create(AppUpdateApi::class.java), url = url)
    }
}

private fun AppReleaseDto.toRelease(): AppRelease? {
    val code = versionCode ?: return null
    return AppRelease(
        versionCode = code,
        versionName = versionName.orEmpty(),
        url = url.orEmpty(),
        sha256 = sha256.orEmpty(),
        // Notes are the one part a release may honestly not have. A build published without a line
        // about what changed is still a build worth offering; the card simply shows the version.
        notesFa = notesFa.orEmpty(),
        notesEn = notesEn.orEmpty(),
        mandatory = mandatory ?: false,
    )
}
