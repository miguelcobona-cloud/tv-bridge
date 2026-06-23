package com.tvbridge.sender

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.app.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvbridge.sender.ui.SenderApp
import com.tvbridge.sender.service.ProjectionForegroundService
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val viewModel: SenderViewModel
        get() = androidx.lifecycle.ViewModelProvider(this)[SenderViewModel::class.java]

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            viewModel.onProjectionPermissionGranted(data)
        } else {
            viewModel.onProjectionPermissionDenied()
        }
    }

    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.applyServerFromLink(result.contents)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchQrScanner()
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingScreenCapture) {
            pendingScreenCapture = false
            launchScreenCaptureIntent()
        } else if (!granted) {
            pendingScreenCapture = false
            viewModel.onAudioPermissionDenied()
        }
    }

    private var pendingScreenCapture = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        handleDeepLink(intent)

        setContent {
            val vm: SenderViewModel = viewModel()

            LaunchedEffect(vm) {
                vm.projectionRequests.collectLatest { request ->
                    requestScreenCapture(request.shareSystemAudio)
                }
            }

            SenderApp(
                viewModel = vm,
                onScanQr = ::onScanQrClicked,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        viewModel.applyServerFromLink(data.toString())
    }

    private fun onScanQrClicked() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> launchQrScanner()

            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchQrScanner() {
        val options = ScanOptions()
            .setPrompt(getString(R.string.qr_scan_title))
            .setBeepEnabled(false)
            .setOrientationLocked(false)
        qrScannerLauncher.launch(options)
    }

    private fun requestScreenCapture(shareSystemAudio: Boolean) {
        if (!shareSystemAudio) {
            launchScreenCaptureIntent()
            return
        }

        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> launchScreenCaptureIntent()

            else -> showAudioPermissionRationale()
        }
    }

    private fun showAudioPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle(R.string.audio_permission_title)
            .setMessage(R.string.audio_permission_rationale)
            .setPositiveButton(R.string.audio_permission_continue) { _, _ ->
                pendingScreenCapture = true
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                viewModel.onAudioPermissionDenied()
            }
            .show()
    }

    private fun launchScreenCaptureIntent() {
        ProjectionForegroundService.start(this)
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
