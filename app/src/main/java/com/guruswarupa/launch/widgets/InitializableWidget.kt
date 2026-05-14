package com.guruswarupa.launch.widgets

/**
 * Interface for widgets that support lazy initialization.
 * Implementing this interface allows the widget to be set up through WidgetSetupManager's generic helpers.
 */
interface InitializableWidget {
    fun initialize()
}
