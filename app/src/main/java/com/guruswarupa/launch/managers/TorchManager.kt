package com.guruswarupa.launch.managers

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.widget.Toast

/**
 * Manages torch/flashlight functionality.
 * Handles toggling the device flashlight on and off.
 */
class TorchManager(private val context: Context) {
    
    private val cameraManager: CameraManager? = 
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    
    private var isTorchOn = false
    private var cameraId: String? = null
    
    init {
        cameraManager?.let { manager ->
            try {
                // Find a camera with flash capability
                for (id in manager.cameraIdList) {
                    val characteristics = manager.getCameraCharacteristics(id)
                    val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    if (flashAvailable) {
                        cameraId = id
                        break
                    }
                }
                // Fallback to first camera if no flash-capable camera found
                if (cameraId == null && manager.cameraIdList.isNotEmpty()) {
                    cameraId = manager.cameraIdList[0]
                }
            } catch (_: Exception) {
                // Camera not available
            }
        }
    }
    
    /**
     * Toggles the torch/flashlight on or off
     * @return true if torch was successfully toggled, false otherwise
     */
    fun toggleTorch(): Boolean {
        if (cameraManager == null || cameraId == null) {
            Toast.makeText(context, "Camera not available", Toast.LENGTH_SHORT).show()
            return false
        }
        
        return try {
            isTorchOn = !isTorchOn
            cameraManager.setTorchMode(cameraId!!, isTorchOn)
            true
        } catch (_: CameraAccessException) {
            Toast.makeText(context, "Unable to access camera", Toast.LENGTH_SHORT).show()
            isTorchOn = !isTorchOn // Revert state
            false
        } catch (e: Exception) {
            Toast.makeText(context, "Error toggling torch: ${e.message}", Toast.LENGTH_SHORT).show()
            isTorchOn = !isTorchOn // Revert state
            false
        }
    }
    
    /**
     * Turns off the torch if it's on
     */
    fun turnOffTorch() {
        if (isTorchOn && cameraManager != null && cameraId != null) {
            try {
                cameraManager.setTorchMode(cameraId!!, false)
                isTorchOn = false
            } catch (_: Exception) {
                // Ignore errors when turning off
            }
        }
    }
}
