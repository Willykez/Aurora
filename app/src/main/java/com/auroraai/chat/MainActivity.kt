package com.auroraai.chat

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.auroraai.chat.data.AppTab
import com.auroraai.chat.data.SettingsStore
import com.auroraai.chat.data.ThemeMode
import com.auroraai.chat.ui.components.HistorySidebarContent
import com.auroraai.chat.ui.components.ProviderFormDialog
import com.auroraai.chat.ui.components.ProviderPickerSheet
import com.auroraai.chat.ui.screens.ChatScreen
import com.auroraai.chat.ui.screens.SettingsScreen
import com.auroraai.chat.ui.theme.AuroraTheme
import com.auroraai.chat.viewmodel.AppViewModel
import kotlinx.coroutines.launch

/** Two back-presses within this window on the Chat screen exits the app. */
private const val EXIT_CONFIRM_WINDOW_MS = 2000L

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // True edge-to-edge: content (and TopAppBar's own background) draws under the system
        // bars instead of the window auto-resizing around a separate black status-bar strip.
        // This is also what makes Modifier.imePadding() below do real work.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val state by viewModel.state.collectAsState()
            val useDarkTheme = when (state.settings?.themeMode ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val view = LocalView.current
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDarkTheme
                controller.isAppearanceLightNavigationBars = !useDarkTheme
            }
            AuroraTheme(themeMode = state.settings?.themeMode ?: ThemeMode.SYSTEM) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AuroraApp(viewModel)
                }
            }
        }
    }
}

@Composable
private fun AuroraApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { message -> if (message.isNotBlank()) snackbarHostState.showSnackbar(message) }
    }

    // Keep the drawer and the ViewModel's notion of "sidebar open" in sync in both directions —
    // a swipe-to-close or scrim tap changes drawerState directly without going through us.
    LaunchedEffect(state.showHistorySidebar) {
        if (state.showHistorySidebar) drawerState.open() else drawerState.close()
    }
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Closed && state.showHistorySidebar) viewModel.dismissHistorySidebar()
    }

    // Back priority, most-specific first: close the drawer, then leave Settings back to Chat,
    // then require a confirming second press to actually exit from Chat.
    if (drawerState.isOpen) {
        BackHandler { scope.launch { drawerState.close() } }
    } else if (state.currentTab != AppTab.CHAT) {
        BackHandler { viewModel.selectTab(AppTab.CHAT) }
    } else {
        var lastBackPressAt by remember { mutableLongStateOf(0L) }
        val activity = context as? Activity
        BackHandler {
            val now = System.currentTimeMillis()
            if (now - lastBackPressAt < EXIT_CONFIRM_WINDOW_MS) {
                activity?.finish()
            } else {
                lastBackPressAt = now
                scope.launch { snackbarHostState.showSnackbar("Press back again to exit") }
            }
        }
    }

    val activeProfile = state.activeProviderProfile
    val providerStatusLabel = activeProfile?.let { "${it.name} · ${it.model}" } ?: "No provider configured"
    val isProviderReady = activeProfile != null && (activeProfile.apiKey.isNotBlank() || SettingsStore.isKeylessLocal(activeProfile.baseUrl))

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            HistorySidebarContent(
                sessions = state.sessionSummaries,
                providerStatusLabel = providerStatusLabel,
                isProviderReady = isProviderReady,
                onNewChat = { viewModel.newChat(); viewModel.selectTab(AppTab.CHAT); scope.launch { drawerState.close() } },
                onOpenSession = { id -> viewModel.loadSession(id); scope.launch { drawerState.close() } },
                onDeleteSession = viewModel::deleteSession,
                onOpenSettings = { viewModel.selectTab(AppTab.SETTINGS); scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.imePadding(), // keeps the chat input bar (and message list) above the keyboard
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(if (state.currentTab == AppTab.SETTINGS) "Settings" else "Aurora") },
                    navigationIcon = {
                        if (state.currentTab == AppTab.SETTINGS) {
                            IconButton(onClick = { viewModel.selectTab(AppTab.CHAT) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Chat history")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background
                    ),
                    actions = {
                        if (state.currentTab == AppTab.CHAT) {
                            IconButton(onClick = { viewModel.newChat() }) {
                                Icon(Icons.Default.Add, contentDescription = "New chat")
                            }
                        }
                    }
                )
            }
        ) { padding ->
            when (state.currentTab) {
                AppTab.CHAT -> ChatScreen(
                    state = state, onSend = viewModel::sendMessage, onStop = viewModel::stopAgent,
                    onOpenProviderPicker = viewModel::openProviderPicker,
                    modifier = Modifier.padding(padding).fillMaxSize()
                )
                AppTab.SETTINGS -> SettingsScreen(
                    settings = state.settings,
                    providerProfiles = state.providerProfiles,
                    activeProviderProfileId = state.activeProviderProfile?.id,
                    onSaveGeneration = viewModel::saveGenerationParams,
                    onSetThemeMode = viewModel::setThemeMode,
                    onSelectProvider = viewModel::selectProviderProfile,
                    onAddProvider = viewModel::requestAddProvider,
                    onEditProvider = viewModel::requestEditProvider,
                    onDeleteProvider = viewModel::deleteProviderProfile,
                    modifier = Modifier.padding(padding).fillMaxSize()
                )
            }
        }
    }

    if (state.showProviderPicker) {
        ProviderPickerSheet(
            profiles = state.providerProfiles,
            activeProfileId = state.activeProviderProfile?.id,
            onSelect = { profile -> viewModel.selectProviderProfile(profile) },
            onAddRequested = viewModel::requestAddProvider,
            onEditRequested = viewModel::requestEditProvider,
            onDelete = viewModel::deleteProviderProfile,
            onDismiss = viewModel::dismissProviderPicker
        )
    }

    if (state.showProviderForm) {
        ProviderFormDialog(
            editing = state.providerFormEditing,
            onDismiss = viewModel::dismissProviderForm,
            onSave = { name, baseUrl, apiKey, model -> viewModel.saveProviderProfile(name, baseUrl, apiKey, model) }
        )
    }
}
