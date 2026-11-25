package com.yuyu.dev.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

/** Owns and recovers the privileged Shizuku UserService connection. */
object ShizukuManager {
    private const val REQUEST_CODE = 1001
    private const val SERVICE_TIMEOUT_MS = 15_000L

    sealed class State {
        data object NotRequired : State()
        data object Unavailable : State()
        data object NoPermission : State()
        data object Idle : State()
        data object Binding : State()
        data object Bound : State()
    }

    @Volatile private var _state: State = State.Unavailable
    val state: State get() = _state

    @Volatile var service: IUnzipService? = null
        private set

    @Volatile private var appContext: Context? = null
    @Volatile private var bindRequested = false
    private var pending = CompletableDeferred<IUnzipService>()
    private var listenersRegistered = false

    private val userServiceArgs: (Context) -> Shizuku.UserServiceArgs = { context ->
        Shizuku.UserServiceArgs(ComponentName(context, UserService::class.java))
            .processNameSuffix("forkthubx_mlbb")
            .tag("forkthubx-mlbb-unzip")
            .version(2)
            .daemon(false)
    }

    private fun resetPending() {
        if (pending.isCompleted) pending = CompletableDeferred()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val remote = binder?.let { IUnzipService.Stub.asInterface(it) }
            if (remote == null || binder?.isBinderAlive != true) {
                service = null
                bindRequested = false
                _state = State.Idle
                return
            }
            service = remote
            bindRequested = false
            _state = State.Bound
            if (!pending.isCompleted) pending.complete(remote)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bindRequested = false
            _state = if (Shizuku.pingBinder()) State.Idle else State.Unavailable
            pending = CompletableDeferred()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        appContext?.let { init(it) }
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        service = null
        bindRequested = false
        _state = State.Unavailable
        pending = CompletableDeferred()
    }

    @Synchronized
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (!listenersRegistered) {
            listenersRegistered = true
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            _state = State.NotRequired
            return
        }
        if (!Shizuku.pingBinder()) {
            _state = State.Unavailable
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            bind(app)
        } else {
            _state = State.NoPermission
        }
    }

    fun requestPermission(context: Context) {
        if (!Shizuku.pingBinder()) {
            _state = State.Unavailable
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            bind(context.applicationContext)
            return
        }
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                Shizuku.removeRequestPermissionResultListener(this)
                if (requestCode != REQUEST_CODE) return
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    bind(context.applicationContext)
                } else {
                    _state = State.NoPermission
                }
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
            .onFailure {
                Shizuku.removeRequestPermissionResultListener(listener)
                _state = State.Unavailable
            }
    }

    @Synchronized
    fun bind(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            _state = State.Idle
            return
        }
        if (!Shizuku.pingBinder()) {
            _state = State.Unavailable
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            _state = State.NoPermission
            return
        }
        if (service?.asBinder()?.isBinderAlive == true) {
            _state = State.Bound
            return
        }
        if (bindRequested) return
        resetPending()
        bindRequested = true
        _state = State.Binding
        runCatching { Shizuku.bindUserService(userServiceArgs(app), connection) }
            .onFailure {
                bindRequested = false
                _state = State.Idle
                if (!pending.isCompleted) pending.completeExceptionally(it)
            }
    }

    suspend fun awaitService(): IUnzipService {
        service?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        val context = appContext ?: throw IllegalStateException("Shizuku has not been initialized")
        if (!Shizuku.pingBinder()) throw IllegalStateException("Shizuku is not running")
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Shizuku permission has not been granted")
        }
        bind(context)
        return withTimeout(SERVICE_TIMEOUT_MS) { pending.await() }
    }
}
