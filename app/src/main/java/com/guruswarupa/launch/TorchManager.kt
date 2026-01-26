package com.guruswarupa.launch

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.widget.Toast

/**
 * Manages torch/flashlight functionality.
 * Handles toggling the device flashlight on and off.
 */
class TorchManager(private val context: Context) {
    
    private val cameraManager: CameraManager? = 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        } else {
            null
        }
    
    private var isTorchOn = false
    private var cameraId: String? = null
    
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && cameraManager != null) {
            try {
                // Find a camera with flash capability
                for (id in cameraManager.cameraIdList) {
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    if (flashAvailable) {
                        cameraId = id
                        break
                    }
                }
                // Fallback to first camera if no flash-capable camera found
                if (cameraId == null && cameraManager.cameraIdList.isNotEmpty()) {
                    cameraId = cameraManager.cameraIdList[0]
                }
            } catch (e: Exception) {
                // Camera not available
            }
        }
    }
    
    /**
     * Toggles the torch/flashlight on or off
     * @return true if torch was successfully toggled, false otherwise
     */
    fun toggleTorch(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(context, "Torch not supported on this device", Toast.LENGTH_SHORT).show()
            return false
        }
        
        if (cameraManager == null || cameraId == null) {
            Toast.makeText(context, "Camera not available", Toast.LENGTH_SHORT).show()
            return false
        }
        
        return try {
            isTorchOn = !isTorchOn
            cameraManager.setTorchMode(cameraId!!, isTorchOn)
            true
        } catch (e: CameraAccessException) {
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
     * Returns the current torch state
     */
    fun isTorchOn(): Boolean = isTorchOn
    
    /**
     * Turns off the torch if it's on
     */
    fun turnOffTorch() {
        if (isTorchOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && cameraManager != null && cameraId != null) {
            try {
                cameraManager.setTorchMode(cameraId!!, false)
                isTorchOn = false
            } catch (e: Exception) {
                // Ignore errors when turning off
            }
        }
    }
}
