package com.oxoghost.hexapass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import com.oxoghost.hexapass.data.keepass.KeePassVaultRepository
import com.oxoghost.hexapass.ui.MainViewModel
import com.oxoghost.hexapass.ui.MainViewModelFactory
import com.oxoghost.hexapass.ui.VaultState

class MainActivity : AppCompatActivity() {

    lateinit var viewModelFactory: MainViewModelFactory
        private set

    private lateinit var viewModel: MainViewModel

    private val handler = Handler(Looper.getMainLooper())
    private val lockRunnable = Runnable {
        viewModel.lockVault()
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (Intent.ACTION_SCREEN_OFF == intent?.action) {
                // Lock immediately when screen goes off
                viewModel.lockVault()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent screenshots and hide content in recent apps switcher
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_main)

        val repository = KeePassVaultRepository(contentResolver, cacheDir)
        viewModelFactory = MainViewModelFactory(repository)
        viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]

        setupLockObservers()
    }

    private fun setupLockObservers() {
        // Handle Screen Off
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        // Handle App Lifecycle (Backgrounding)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // When the app goes to background, we start/continue the inactivity timer.
                // If you want it to lock INSTANTLY on background, call viewModel.lockVault() here.
                // For now, we follow the inactivity timeout set in ViewModel.
            }

            override fun onStart(owner: LifecycleOwner) {
                // If the timer expired while backgrounded, the vault is already locked.
                // This ensures the UI is correct when returning.
                checkVaultState()
            }
        })

        // React to vault locking by navigating to the entry list (which shows the unlock UI)
        viewModel.vaultState.observe(this) { state ->
            if (state == VaultState.LOCKED) {
                checkVaultState()
            }
        }
    }

    private fun checkVaultState() {
        if (viewModel.vaultState.value == VaultState.LOCKED) {
            val navController = findNavController(R.id.nav_host_fragment)
            if (navController.currentDestination?.id != R.id.entryListFragment) {
                navController.popBackStack(R.id.entryListFragment, false)
            }
            handler.removeCallbacks(lockRunnable)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (viewModel.isUnlocked()) {
            resetInactivityTimer()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun resetInactivityTimer() {
        handler.removeCallbacks(lockRunnable)
        handler.postDelayed(lockRunnable, viewModel.autoLockTimeoutMs)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        handler.removeCallbacks(lockRunnable)
    }

    override fun onSupportNavigateUp(): Boolean {
        return findNavController(R.id.nav_host_fragment).navigateUp()
    }
}
