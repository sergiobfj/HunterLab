package dev.hunterlab.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext

class PermissionRequests(
    val locationAvailable: Boolean,
    val notificationsAvailable: Boolean,
    val bluetooth: () -> Unit,
    val location: () -> Unit,
    val notifications: () -> Unit,
    val denied: Set<String>,
    val openSystemSettings: () -> Unit,
)

/** Só solicita permissões já declaradas pela versão instalada, após um toque. */
@Composable
fun rememberPermissionRequests(onChanged: () -> Unit): PermissionRequests {
    val context = LocalContext.current
    val declared = remember(context) {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
    }
    val denied = remember { mutableStateOf<Set<String>>(emptySet()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        denied.value = result.filterValues { !it }.keys
        onChanged()
    }
    val location = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val bluetooth = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    val notifications = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
    return PermissionRequests(
        locationAvailable = location.all { it in declared },
        notificationsAvailable = notifications.isNotEmpty() && notifications.all { it in declared },
        bluetooth = { launcher.launch(bluetooth) },
        location = { if (location.all { it in declared }) launcher.launch(location) },
        notifications = { if (notifications.isNotEmpty() && notifications.all { it in declared }) launcher.launch(notifications) },
        denied = denied.value,
        openSystemSettings = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
    )
}
