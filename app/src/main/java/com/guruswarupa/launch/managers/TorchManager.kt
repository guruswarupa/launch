package com.guruswarupa.launch.managers

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.widget.Toast





class TorchManager(private val context: Context) {
    
    private val cameraManager: CameraManager? = 
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    
    private var isTorchOn = false
    private var cameraId: String? = null
    
    init {
        cameraManager?.let { manager ->
            try {
                
                for (id in manager.cameraIdList) {
                    val characteristics = manager.getCameraCharacteristics(id)
                    val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    if (flashAvailable) {
                        cameraId = id
                        break
                    }
                }
            } catch (_: Exception) {
                
            }
        }
    }
    
    



    fun toggleTorch(): Boolean {
        if (cameraManager == null || cameraId == null) {
            Toast.makeText(context, "Flashlight not available on this device", Toast.LENGTH_SHORT).show()
            return false
        }
        
        return try {
            isTorchOn = !isTorchOn
            cameraManager.setTorchMode(cameraId!!, isTorchOn)
            true
        } catch (_: CameraAccessException) {
            Toast.makeText(context, "Unable to access camera", Toast.LENGTH_SHORT).show()
            isTorchOn = !isTorchOn 
            false
        } catch (e: Exception) {
            Toast.makeText(context, "Error toggling torch: ${e.message}", Toast.LENGTH_SHORT).show()
            isTorchOn = !isTorchOn 
            false
        }
    }
    
    


    fun turnOffTorch() {
        if (isTorchOn && cameraManager != null && cameraId != null) {
            try {
                cameraManager.setTorchMode(cameraId!!, false)
                isTorchOn = false
            } catch (_: Exception) {
                
            }
        }
    }
}
