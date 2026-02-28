package com.guruswarupa.launch.widgets

/**
 * Coordinates the lifecycle of multiple widgets by delegating onResume, onPause and onDestroy calls
 */
class WidgetLifecycleCoordinator {
    private data class WidgetWrapper(
        val isInitialized: () -> Boolean,
        val onResume: () -> Unit,
        val onPause: () -> Unit,
        val onDestroy: () -> Unit
    )

    private val widgets = mutableListOf<WidgetWrapper>()

    /**
     * Register a widget for lifecycle management
     */
    fun register(
        isInitializedCheck: () -> Boolean,
        onResumeAction: () -> Unit,
        onPauseAction: () -> Unit,
        onDestroyAction: () -> Unit = {}
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
            },
            onDestroy = {
                if (isInitializedCheck()) {
                    onDestroyAction()
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

    /**
     * Call onDestroy on all registered widgets that are initialized
     */
    fun onDestroy() {
        widgets.forEach { widget ->
            widget.onDestroy()
        }
    }
}
