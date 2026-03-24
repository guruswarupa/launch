package com.guruswarupa.launch

import android.content.pm.ResolveInfo
import com.guruswarupa.launch.core.BroadcastReceiverManager
import com.guruswarupa.launch.core.CacheManager
import com.guruswarupa.launch.core.LifecycleManager
import com.guruswarupa.launch.core.PermissionManager
import com.guruswarupa.launch.core.ShareManager
import com.guruswarupa.launch.core.SystemBarManager
import com.guruswarupa.launch.handlers.NavigationManager
import com.guruswarupa.launch.handlers.VoiceSearchManager
import com.guruswarupa.launch.handlers.ActivityInitializer
import com.guruswarupa.launch.handlers.ActivityResultHandler
import com.guruswarupa.launch.handlers.MainActivityResultRegistry
import com.guruswarupa.launch.handlers.SettingsChangeCoordinator
import com.guruswarupa.launch.managers.AppDockManager
import com.guruswarupa.launch.managers.AppListLoader
import com.guruswarupa.launch.managers.AppListManager
import com.guruswarupa.launch.managers.AppListUIUpdater
import com.guruswarupa.launch.managers.AppLockManager
import com.guruswarupa.launch.managers.AppSearchManager
import com.guruswarupa.launch.managers.AppTimerManager
import com.guruswarupa.launch.managers.AppUsageStatsManager
import com.guruswarupa.launch.managers.ContactActionHandler
import com.guruswarupa.launch.managers.ContactManager
import com.guruswarupa.launch.managers.DonationPromptManager
import com.guruswarupa.launch.managers.DrawerManager
import com.guruswarupa.launch.managers.FavoriteAppManager
import com.guruswarupa.launch.managers.FocusModeApplier
import com.guruswarupa.launch.managers.GestureHandler
import com.guruswarupa.launch.managers.HiddenAppManager
import com.guruswarupa.launch.managers.ReviewPromptManager
import com.guruswarupa.launch.managers.RssFeedManager
import com.guruswarupa.launch.managers.ScreenPagerManager
import com.guruswarupa.launch.managers.SearchTypeMenuManager
import com.guruswarupa.launch.managers.ServiceManager
import com.guruswarupa.launch.managers.UsageStatsCacheManager
import com.guruswarupa.launch.managers.UsageStatsDisplayManager
import com.guruswarupa.launch.managers.UsageStatsRefreshManager
import com.guruswarupa.launch.managers.WallpaperManagerHelper
import com.guruswarupa.launch.managers.WebAppManager
import com.guruswarupa.launch.managers.WidgetConfigurationManager
import com.guruswarupa.launch.managers.WidgetManager
import com.guruswarupa.launch.utils.FinanceWidgetManager
import com.guruswarupa.launch.utils.TimeDateManager
import com.guruswarupa.launch.utils.TodoAlarmManager
import com.guruswarupa.launch.utils.TodoManager
import com.guruswarupa.launch.utils.WeatherManager
import com.guruswarupa.launch.widgets.WidgetLifecycleCoordinator
import com.guruswarupa.launch.widgets.WidgetSetupManager
import com.guruswarupa.launch.widgets.WidgetThemeManager
import com.guruswarupa.launch.widgets.WidgetVisibilityManager

class CoreManagers {
    var activityInitializer: ActivityInitializer? = null
    var shareManager: ShareManager? = null
    var appLockManager: AppLockManager? = null
    var appTimerManager: AppTimerManager? = null
    var favoriteAppManager: FavoriteAppManager? = null
    var hiddenAppManager: HiddenAppManager? = null
    var webAppManager: WebAppManager? = null
    var appLauncher: AppLauncher? = null
    var permissionManager: PermissionManager? = null
    var systemBarManager: SystemBarManager? = null
    var broadcastReceiverManager: BroadcastReceiverManager? = null
    var lifecycleManager: LifecycleManager? = null
}

class DataManagers {
    var appList: MutableList<ResolveInfo>? = null
    var fullAppList: MutableList<ResolveInfo> = mutableListOf()
    var cacheManager: CacheManager? = null
    var appListManager: AppListManager? = null
    var appListLoader: AppListLoader? = null
    var contactManager: ContactManager? = null
    var usageStatsCacheManager: UsageStatsCacheManager? = null
    var appSearchManager: AppSearchManager? = null
    var usageStatsManager: AppUsageStatsManager? = null
    var weatherManager: WeatherManager? = null
    var timeDateManager: TimeDateManager? = null
    var todoManager: TodoManager? = null
    var todoAlarmManager: TodoAlarmManager? = null
    var financeWidgetManager: FinanceWidgetManager? = null
    var usageStatsDisplayManager: UsageStatsDisplayManager? = null
    var rssFeedManager: RssFeedManager? = null
    var serviceManager: ServiceManager? = null
}

class WidgetManagers {
    var widgetThemeManager: WidgetThemeManager? = null
    var widgetSetupManager: WidgetSetupManager? = null
    var widgetLifecycleCoordinator: WidgetLifecycleCoordinator? = null
    var widgetManager: WidgetManager? = null
    var widgetConfigurationManager: WidgetConfigurationManager? = null
    var widgetVisibilityManager: WidgetVisibilityManager? = null
}

class UIManagers {
    var adapter: AppAdapter? = null
    var searchTypeMenuManager: SearchTypeMenuManager? = null
    var gestureHandler: GestureHandler? = null
    var wallpaperManagerHelper: WallpaperManagerHelper? = null
    var appDockManager: AppDockManager? = null
    var resultRegistry: MainActivityResultRegistry? = null
    var voiceSearchManager: VoiceSearchManager? = null
    var usageStatsRefreshManager: UsageStatsRefreshManager? = null
    var activityResultHandler: ActivityResultHandler? = null
    var navigationManager: NavigationManager? = null
    var focusModeApplier: FocusModeApplier? = null
    var appListUIUpdater: AppListUIUpdater? = null
    var drawerManager: DrawerManager? = null
    var screenPagerManager: ScreenPagerManager? = null
    var contactActionHandler: ContactActionHandler? = null
    var settingsChangeCoordinator: SettingsChangeCoordinator? = null
    var donationPromptManager: DonationPromptManager? = null
    var reviewPromptManager: ReviewPromptManager? = null
}
