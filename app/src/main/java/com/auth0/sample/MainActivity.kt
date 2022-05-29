package com.auth0.sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationAPIClient
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.Callback
import com.auth0.android.lock.AuthenticationCallback
import com.auth0.android.lock.Lock
import com.auth0.android.lock.PasswordlessLock
import com.auth0.android.management.ManagementException
import com.auth0.android.management.UsersAPIClient
import com.auth0.android.provider.CustomTabsOptions
import com.auth0.android.provider.WebAuthProvider
import com.auth0.android.result.Credentials
import com.auth0.android.result.UserProfile
import com.auth0.sample.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    private lateinit var account: Auth0

    private lateinit var lock: Lock
    private lateinit var binding: ActivityMainBinding
    private var cachedCredentials: Credentials? = null
    private var cachedUserProfile: UserProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set up the account object with the Auth0 application details
        account = Auth0(
            getString(R.string.com_auth0_client_id),
            getString(R.string.com_auth0_domain)
        )

        // Bind the button click with the login action
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.buttonLogin.setOnClickListener { loginNative() }
//        binding.buttonLogin.setOnClickListener { loginPasswordLessNative() }
//        binding.buttonLogin.setOnClickListener { loginWithBrowser() }
        binding.buttonLogout.setOnClickListener { logout() }
        binding.buttonGetMetadata.setOnClickListener { getUserMetadata() }
        binding.buttonPatchMetadata.setOnClickListener { patchUserMetadata() }
    }

    private fun updateUI() {
        binding.buttonLogout.isEnabled = cachedCredentials != null
        binding.metadataPanel.isVisible = cachedCredentials != null
        binding.buttonLogin.isEnabled = cachedCredentials == null
        binding.userProfile.isVisible = cachedCredentials != null

        binding.userProfile.text =
            "Name: ${cachedUserProfile?.name ?: ""}\n" +
                    "Email: ${cachedUserProfile?.email ?: ""}"

        if (cachedUserProfile == null) {
            binding.inputEditMetadata.setText("")
        }
    }

    private fun loginWithBrowser() {
        val ctOptions = CustomTabsOptions.newBuilder()
            .withToolbarColor(R.color.com_auth0_lock_form_background)
            .showTitle(true)
            .build()

        // Setup the WebAuthProvider, using the custom scheme and scope.
        WebAuthProvider.login(account)
            .withCustomTabsOptions(ctOptions)
            .withScheme(getString(R.string.com_auth0_scheme))
            .withScope("openid profile email read:current_user update:current_user_metadata")
            .withAudience("https://${getString(R.string.com_auth0_domain)}/api/v2/")

            // Launch the authentication passing the callback where the results will be received
            .start(this, object : Callback<Credentials, AuthenticationException> {
                override fun onFailure(error: AuthenticationException) {
                    SnackBar.show(applicationContext, binding.root, "Failure: ${error.getCode()}")
                }

                override fun onSuccess(result: Credentials) {
                    cachedCredentials = result
                    SnackBar.show(applicationContext, binding.root, "Success: ${result.accessToken}")
                    updateUI()
                    showUserProfile()
                }
            })
    }

    private fun loginNative() {
        lock = Lock.newBuilder(account, nativeCallback)
            .withScheme(getString(R.string.com_auth0_scheme))
            .withScope("openid profile email read:current_user update:current_user_metadata")
            .withAudience("https://${getString(R.string.com_auth0_domain)}/api/v2/")
            .closable(true)
            .setShowTerms(true)
            .setMustAcceptTerms(true)
            .build(this)

        startActivity(lock.newIntent(this))
    }

    private fun loginPasswordLessNative() {
        val lock = PasswordlessLock.newBuilder(account, nativeCallback)
            .withScheme(getString(R.string.com_auth0_scheme))
            .withScope("openid profile email read:current_user update:current_user_metadata")
            .withAudience("https://${getString(R.string.com_auth0_domain)}/api/v2/")
            .useCode()
            .build(this)

        startActivity(lock.newIntent(this))
    }

    private val nativeCallback = object : AuthenticationCallback() {
        override fun onAuthentication(credentials: Credentials) {
            cachedCredentials = credentials
            SnackBar.show(applicationContext, binding.root, "Success: ${credentials.accessToken}")
            updateUI()
            showUserProfile()
            // Authenticated
        }

        override fun onError(error: AuthenticationException) {
            println(error)
            SnackBar.show(applicationContext, binding.root, "Failure: ${error.getCode()}")
        }
    }

    private fun logout() {
        WebAuthProvider.logout(account)
            .withScheme(getString(R.string.com_auth0_scheme))
            .start(this, object : Callback<Void?, AuthenticationException> {
                override fun onSuccess(result: Void?) {
                    // The user has been logged out!
                    cachedCredentials = null
                    cachedUserProfile = null
                    updateUI()
                }

                override fun onFailure(error: AuthenticationException) {
                    updateUI()
                    SnackBar.show(applicationContext, binding.root, "Failure: ${error.getCode()}")
                }
            })
    }

    private fun showUserProfile() {
        val client = AuthenticationAPIClient(account)

        // Use the access token to call userInfo endpoint.
        // In this sample, we can assume cachedCredentials has been initialized by this point.
        client.userInfo(cachedCredentials!!.accessToken)
            .start(object : Callback<UserProfile, AuthenticationException> {
                override fun onFailure(error: AuthenticationException) {
                    SnackBar.show(applicationContext, binding.root, "Failure: ${error.getCode()}")
                }

                override fun onSuccess(result: UserProfile) {
                    cachedUserProfile = result
                    updateUI()
                }
            })
    }

    private fun getUserMetadata() {
        // Create the user API client
        val usersClient = UsersAPIClient(account, cachedCredentials!!.accessToken)

        // Get the full user profile
        usersClient.getProfile(cachedUserProfile!!.getId()!!)
            .start(object : Callback<UserProfile, ManagementException> {
                override fun onFailure(error: ManagementException) {
                    SnackBar.show(applicationContext, binding.root, "Failure: ${error.getCode()}")
                }

                override fun onSuccess(result: UserProfile) {
                    cachedUserProfile = result
                    updateUI()

                    val country = result.getUserMetadata()["country"] as String?
                    binding.inputEditMetadata.setText(country)
                }
            })
    }

    private fun patchUserMetadata() {
        val usersClient = UsersAPIClient(account, cachedCredentials!!.accessToken)
        val metadata = mapOf("country" to binding.inputEditMetadata.text.toString())

        usersClient
            .updateMetadata(cachedUserProfile!!.getId()!!, metadata)
            .start(object : Callback<UserProfile, ManagementException> {
                override fun onFailure(error: ManagementException) {
                    SnackBar.show(applicationContext, binding.root, "Failure: ${error.getCode()}")
                }

                override fun onSuccess(result: UserProfile) {
                    cachedUserProfile = result
                    updateUI()
                    SnackBar.show(applicationContext, binding.root, "Successful")
                }
            })
    }

}