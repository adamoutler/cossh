package com.adamoutler.ssh.sync

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.MainActivity
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.lang.reflect.Proxy
import java.util.concurrent.Executor
import kotlin.coroutines.resume

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], shadows = [DriveSyncAuthorizeCoverageTest.ShadowIdentity::class])
class DriveSyncAuthorizeCoverageTest {

    @Implements(Identity::class)
    class ShadowIdentity {
        companion object {
            var mockAuthorizationClient: AuthorizationClient? = null

            @Implementation
            @JvmStatic
            fun getAuthorizationClient(@Suppress("UNUSED_PARAMETER") activity: Activity): AuthorizationClient {
                return mockAuthorizationClient ?: throw IllegalStateException("mockAuthorizationClient not configured")
            }

            @Implementation
            @JvmStatic
            fun getAuthorizationClient(@Suppress("UNUSED_PARAMETER") context: Context): AuthorizationClient {
                return mockAuthorizationClient ?: throw IllegalStateException("mockAuthorizationClient not configured")
            }
        }
    }

    private lateinit var context: Context
    private lateinit var driveSyncManager: DriveSyncManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        driveSyncManager = DriveSyncManager(context)
        DriveSyncManager.authorizationContinuation = null
        ShadowIdentity.mockAuthorizationClient = null
    }

    @After
    fun tearDown() {
        DriveSyncManager.authorizationContinuation = null
        ShadowIdentity.mockAuthorizationClient = null
    }

    private suspend fun callAuthorizeScope(manager: DriveSyncManager, activity: Activity) {
        suspendCancellableCoroutine<Unit> { cont ->
            val method = DriveSyncManager::class.java.declaredMethods.first { it.name == "authorizeScope" }
            method.isAccessible = true
            val result = method.invoke(manager, activity, cont)
            if (result != kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) {
                cont.resume(Unit)
            }
        }
    }

    private fun getOauthToken(manager: DriveSyncManager): String? {
        val field = DriveSyncManager::class.java.getDeclaredField("oauthToken")
        field.isAccessible = true
        return field.get(manager) as String?
    }

    open class ImmediateTask<T>(
        private val result: T?,
        private val exception: Exception?
    ) : Task<T>() {
        override fun isComplete(): Boolean = true
        override fun isSuccessful(): Boolean = exception == null
        override fun isCanceled(): Boolean = false
        override fun getResult(): T = result ?: throw IllegalStateException("No result")
        override fun <X : Throwable?> getResult(p0: Class<X>): T = result ?: throw IllegalStateException("No result")
        override fun getException(): Exception? = exception

        @Suppress("UNCHECKED_CAST")
        override fun addOnSuccessListener(listener: OnSuccessListener<in T>): Task<T> {
            if (exception == null && result != null) {
                listener.onSuccess(result)
            }
            return this
        }

        override fun addOnSuccessListener(executor: Executor, listener: OnSuccessListener<in T>): Task<T> =
            addOnSuccessListener(listener)

        override fun addOnSuccessListener(activity: Activity, listener: OnSuccessListener<in T>): Task<T> =
            addOnSuccessListener(listener)

        override fun addOnFailureListener(listener: OnFailureListener): Task<T> {
            if (exception != null) {
                listener.onFailure(exception)
            }
            return this
        }

        override fun addOnFailureListener(executor: Executor, listener: OnFailureListener): Task<T> =
            addOnFailureListener(listener)

        override fun addOnFailureListener(activity: Activity, listener: OnFailureListener): Task<T> =
            addOnFailureListener(listener)
    }

    @Suppress("DEPRECATION")
    private fun createMockClient(taskProvider: (AuthorizationRequest) -> Task<AuthorizationResult>): AuthorizationClient {
        return Proxy.newProxyInstance(
            AuthorizationClient::class.java.classLoader,
            arrayOf(AuthorizationClient::class.java)
        ) { _, method, args ->
            when (method.name) {
                "authorize" -> taskProvider(args[0] as AuthorizationRequest)
                "getAuthorizationResultFromIntent" -> {
                    AuthorizationResult(null, "token_from_intent", null, emptyList(), null, null)
                }
                else -> null
            }
        } as AuthorizationClient
    }

    @Test
    @Suppress("DEPRECATION")
    fun testAuthorizeScope_SuccessWithoutResolution() = runBlocking {
        val result = AuthorizationResult("auth_code", "direct_access_token", null, emptyList(), null, null)
        val mockClient = createMockClient { ImmediateTask(result, null) }
        ShadowIdentity.mockAuthorizationClient = mockClient

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        callAuthorizeScope(driveSyncManager, activity)

        assertEquals("direct_access_token", getOauthToken(driveSyncManager))
    }

    @Test
    @Suppress("DEPRECATION")
    fun testAuthorizeScope_SuccessWithResolution_StandardActivity() = runBlocking {
        val pendingIntent = PendingIntent.getActivity(
            context, 1, Intent(), PendingIntent.FLAG_IMMUTABLE
        )
        val result = AuthorizationResult(null, null, null, emptyList(), null, pendingIntent)
        val mockClient = createMockClient { ImmediateTask(result, null) }
        ShadowIdentity.mockAuthorizationClient = mockClient

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        var resumed = false
        val job = launch {
            callAuthorizeScope(driveSyncManager, activity)
            resumed = true
        }
        kotlinx.coroutines.yield()

        assertNotNull(DriveSyncManager.authorizationContinuation)
        DriveSyncManager.handleAuthorizationResult(1001, Activity.RESULT_OK, Intent())

        job.join()
        assertTrue(resumed)
        assertEquals("token_from_intent", getOauthToken(driveSyncManager))
    }

    @Test
    @Suppress("DEPRECATION")
    fun testAuthorizeScope_SuccessWithResolution_MainActivity() = runBlocking {
        val pendingIntent = PendingIntent.getActivity(
            context, 1, Intent(), PendingIntent.FLAG_IMMUTABLE
        )
        val result = AuthorizationResult(null, null, null, emptyList(), null, pendingIntent)
        val mockClient = createMockClient { ImmediateTask(result, null) }
        ShadowIdentity.mockAuthorizationClient = mockClient

        val mainActivity = Robolectric.buildActivity(MainActivity::class.java).create().get()

        var resumed = false
        val job = launch {
            callAuthorizeScope(driveSyncManager, mainActivity)
            resumed = true
        }
        kotlinx.coroutines.yield()

        assertNotNull(DriveSyncManager.authorizationContinuation)
        DriveSyncManager.handleAuthorizationResult(1001, Activity.RESULT_OK, Intent())

        job.join()
        assertTrue(resumed)
        assertEquals("token_from_intent", getOauthToken(driveSyncManager))
    }

    @Test
    fun testAuthorizeScope_Failure_ThrowsException() = runBlocking {
        val exception = ApiException(Status.RESULT_INTERNAL_ERROR)
        val mockClient = createMockClient { ImmediateTask<AuthorizationResult>(null, exception) }
        ShadowIdentity.mockAuthorizationClient = mockClient

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        var caught: Throwable? = null
        try {
            callAuthorizeScope(driveSyncManager, activity)
        } catch (t: Throwable) {
            caught = t
        }

        assertNotNull(caught)
        assertNull(getOauthToken(driveSyncManager))
    }
}
