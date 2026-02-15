package com.guruswarupa.launch

@Suppress("unused")
object NumberBaseConverter {
    
    fun decimalToBinary(decimal: Long): String {
        if (decimal == 0L) return "0"
        return decimal.toString(2)
    }
    
    fun binaryToDecimal(binary: String): Long {
        return binary.toLongOrNull(2) ?: 0L
    }
    
    fun decimalToOctal(decimal: Long): String {
        if (decimal == 0L) return "0"
        return decimal.toString(8)
    }
    
    fun octalToDecimal(octal: String): Long {
        return octal.toLongOrNull(8) ?: 0L
    }
    
    fun decimalToHex(decimal: Long): String {
        if (decimal == 0L) return "0"
        return decimal.toString(16).uppercase()
    }
    
    fun hexToDecimal(hex: String): Long {
        return hex.toLongOrNull(16) ?: 0L
    }
    
    fun convertBase(value: String, fromBase: Int, toBase: Int): String {
        try {
            val decimal = value.toLongOrNull(fromBase) ?: return "Error"
            return if (toBase == 10) {
                decimal.toString()
            } else {
                decimal.toString(toBase).uppercase()
            }
        } catch (_: Exception) {
            return "Error"
        }
    }
}
