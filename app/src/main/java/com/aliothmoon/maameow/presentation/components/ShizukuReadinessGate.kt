package com.aliothmoon.maameow.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.manager.ShizukuInstallHelper
import com.aliothmoon.maameow.manager.ShizukuReadinessProvider
import kotlinx.coroutines.launch
import org.koin.compose.koinInject


@Composable
fun ShizukuReadinessGate(
    permissionManager: PermissionManager = koinInject(),
    appSettingsManager: AppSettingsManager = koinInject(),
    readinessProvider: ShizukuReadinessProvider = koinInject(),
) {
    val readiness by readinessProvider.state.collectAsStateWithLifecycle()
    val launchPackage by appSettingsManager.shizukuLaunchPackage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRequesting by remember { mutableStateOf(false) }

    ShizukuReadinessDialog(
        readiness = readiness,
        onInstall = { ShizukuInstallHelper.installShizuku(context) },
        onOpenApp = { ShizukuInstallHelper.openShizuku(context, launchPackage) },
        onRequestAuth = {
            scope.launch {
                isRequesting = true
                permissionManager.requestRemoteAccess()
                isRequesting = false
            }
        },
        onDismiss = {
            scope.launch { appSettingsManager.setSkipShizukuCheck(true) }
        },
        onSwitchToRoot = {
            scope.launch { permissionManager.setStartupBackend(RemoteBackend.ROOT) }
        },
        isRequesting = isRequesting,
    )
}
