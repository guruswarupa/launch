package com.guruswarupa.launch.widgets

/**
 * Coordinates the lifecycle of multiple widgets by delegating onResume and onPause calls
 */
class WidgetLifecycleCoordinator {
    private data class WidgetWrapper(
        val isInitialized: () -> Boolean,
        val onResume: () -> Unit,
        val onPause: () -> Unit
    )

    private val widgets = mutableListOf<WidgetWrapper>()

    /**
     * Register a widget for lifecycle management
     */
    fun register(
        isInitializedCheck: () -> Boolean,
        onResumeAction: () -> Unit,
        onPauseAction: () -> Unit
    ) {
        val wrapper = WidgetWrapper(
            isInitialized = isInitializedCheck,
            onResume = {
                if (isInitializedCheck()) {
                    onResumeAction()
                }
            },
            onPause = {
                if (isInitializedCheck()) {
                    onPauseAction()
                }
            }
        )
        widgets.add(wrapper)
    }

    /**
     * Call onResume on all registered widgets that are initialized
     */
    fun onResume() {
        widgets.forEach { widget ->
            widget.onResume()
        }
    }

    /**
     * Call onPause on all registered widgets that are initialized
     */
    fun onPause() {
        widgets.forEach { widget ->
            widget.onPause()
        }
    }
}