package org.mayday.sociallogins

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.net.Uri
import android.text.TextUtils
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import okio.Buffer
import okio.buffer
import okio.source
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.charset.Charset

class Configuration(private val mContext: Context, private val provider: String) {
    private val mPrefs: SharedPreferences
    private val mResources: Resources

    private var mConfigJson: JSONObject? = null
    private var mConfigHash: String? = null
    /**
     * Returns a description of the configuration error, if the configuration is invalid.
     */
    @get:Nullable
    var configurationError: String? = null
        private set

    @get:Nullable
    var clientId: String? = null
        private set
    @get:NonNull
    var scope: String? = null
        private set
    @get:NonNull
    var redirectUri: Uri? = null

        private set
    @get:Nullable
    var authEndpointUri: Uri? = null
        private set
    @get:Nullable
    var tokenEndpointUri: Uri? = null

        private set
    var isHttpsRequired: Boolean = false
        private set

    /**
     * Indicates whether the current configuration is valid.
     */
    val isValid: Boolean
        get() = configurationError == null

    private// ensure that the redirect URI declared in the configuration is handled by some activity
    // in the app, by querying the package manager speculatively
    val isRedirectUriRegistered: Boolean
        get() {
            val redirectIntent = Intent()
            redirectIntent.setPackage(mContext.packageName)
            redirectIntent.action = Intent.ACTION_VIEW
            redirectIntent.addCategory(Intent.CATEGORY_BROWSABLE)
            redirectIntent.data = redirectUri

            return !mContext.packageManager.queryIntentActivities(redirectIntent, 0).isEmpty()
        }

    init {
        mPrefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        mResources = mContext.resources

        try {
            readConfiguration()
        } catch (ex: InvalidConfigurationException) {
            configurationError = ex.message
        }

    }

    /**
     * Indicates that the current configuration should be accepted as the "last known valid"
     * configuration.
     */
    fun acceptConfiguration() {
        mPrefs.edit().putString(KEY_LAST_HASH, mConfigHash).apply()
    }

    @Throws(InvalidConfigurationException::class)
    private fun readConfiguration() {
        val file = when(provider){
            "facebook" -> R.raw.facebook
            "google" -> R.raw.google
            else -> R.raw.twitter
        }
        val configSource = mResources.openRawResource(file).source().buffer()
        val configData = Buffer()

        try {
            configSource.readAll(configData)
            mConfigJson = JSONObject(configData.readString(Charset.forName("UTF-8")))
        } catch (ex: IOException) {
            throw InvalidConfigurationException(
                "Failed to read configuration: " + ex.message
            )
        } catch (ex: JSONException) {
            throw InvalidConfigurationException(
                "Unable to parse configuration: " + ex.message
            )
        }

        mConfigHash = configData.sha256().base64()
        clientId = getConfigString("client_id")
        scope = getRequiredConfigString("authorization_scope")
        redirectUri = getRequiredConfigUri("redirect_uri")

        if (!isRedirectUriRegistered) {
            throw InvalidConfigurationException(
                "redirect_uri is not handled by any activity in this app! "
                        + "Ensure that the appAuthRedirectScheme in your build.gradle file "
                        + "is correctly configured, or that an appropriate intent filter "
                        + "exists in your app manifest."
            )
        }


        authEndpointUri = getRequiredConfigWebUri("authorization_endpoint_uri")

        tokenEndpointUri = getRequiredConfigWebUri("token_endpoint_uri")

        isHttpsRequired = mConfigJson!!.optBoolean("https_required", true)
    }

    @Nullable
    internal fun getConfigString(propName: String): String? {
        var value: String? = mConfigJson!!.optString(propName) ?: return null

        value = value!!.trim { it <= ' ' }
        return if (TextUtils.isEmpty(value)) {
            null
        } else value

    }

    @NonNull
    @Throws(InvalidConfigurationException::class)
    private fun getRequiredConfigString(propName: String): String {
        return getConfigString(propName) ?: throw InvalidConfigurationException(
            "$propName is required but not specified in the configuration"
        )
    }

    @NonNull
    @Throws(InvalidConfigurationException::class)
    internal fun getRequiredConfigUri(propName: String): Uri {
        val uriStr = getRequiredConfigString(propName)
        val uri: Uri
        try {
            uri = Uri.parse(uriStr)
        } catch (ex: Throwable) {
            throw InvalidConfigurationException("$propName could not be parsed", ex)
        }

        if (!uri.isHierarchical || !uri.isAbsolute) {
            throw InvalidConfigurationException(
                "$propName must be hierarchical and absolute"
            )
        }

        if (!TextUtils.isEmpty(uri.encodedUserInfo)) {
            throw InvalidConfigurationException("$propName must not have user info")
        }

        if (!TextUtils.isEmpty(uri.encodedQuery)) {
            throw InvalidConfigurationException("$propName must not have query parameters")
        }

        if (!TextUtils.isEmpty(uri.encodedFragment)) {
            throw InvalidConfigurationException("$propName must not have a fragment")
        }

        return uri
    }

    @Throws(InvalidConfigurationException::class)
    internal fun getRequiredConfigWebUri(propName: String): Uri {
        val uri = getRequiredConfigUri(propName)
        val scheme = uri.scheme
        if (TextUtils.isEmpty(scheme) || !("http" == scheme || "https" == scheme)) {
            throw InvalidConfigurationException(
                "$propName must have an http or https scheme"
            )
        }

        return uri
    }

    class InvalidConfigurationException : Exception {
        internal constructor(reason: String) : super(reason)

        internal constructor(reason: String, cause: Throwable) : super(reason, cause)
    }

    companion object {
        private const val PREFS_NAME = "config"
        private const val KEY_LAST_HASH = "lastHash"

        private var sInstance = WeakReference<Configuration>(null)

        fun getInstance(context: Context, provider: String): Configuration {
            var config = sInstance.get()
            if (config == null || config.provider != provider) {
                config = Configuration(context, provider)
                sInstance = WeakReference(config)
            }

            return config
        }
    }
}