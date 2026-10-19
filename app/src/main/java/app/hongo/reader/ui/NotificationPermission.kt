package app.hongo.reader.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Wraps an action that starts background work with a progress notification (dictionary
 * downloads and imports). From Android 13 apps need a permission to show notifications at all;
 * without it the work runs unseen. The question comes when the reader starts such work, and the
 * action runs whatever the answer: the notification is a convenience, not a requirement.
 */
@Composable
fun rememberWithNotifications(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<() -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val action = pending[0]
        pending[0] = null
        action?.invoke()
    }
    return remember(launcher) {
        { action ->
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                action()
            } else {
                // After two refusals Android answers at once without asking.
                pending[0] = action
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
