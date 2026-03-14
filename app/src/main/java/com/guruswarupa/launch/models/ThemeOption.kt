package com.guruswarupa.launch.models

data class ThemeOption(
    val id: String,
    val name: String,
    val wallpaperUrl: String,
    val primaryColor: String,
    val category: String,
    val isDark: Boolean = true
) {
    companion object {
        val PREDEFINED_THEMES = listOf(
            // Landscape
            ThemeOption(
                "stardust", 
                "Deep Stardust", 
                "https://images.unsplash.com/photo-1475274047050-1d0c0975c63e?q=80&w=2400&auto=format&fit=crop", 
                "#94A3B8",
                "Landscape"
            ),
            ThemeOption(
                "forest", 
                "Evergreen Forest", 
                "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?q=80&w=2400&auto=format&fit=crop", 
                "#10B981",
                "Landscape"
            ),
            ThemeOption(
                "midnight_peak",
                "Midnight Peak",
                "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?q=80&w=2400&auto=format&fit=crop",
                "#6366F1",
                "Landscape"
            ),
            ThemeOption(
                "desert", 
                "Golden Sands", 
                "https://images.unsplash.com/photo-1473580044384-7ba9967e16a0?q=80&w=2400&auto=format&fit=crop", 
                "#F59E0B",
                "Landscape"
            ),
            ThemeOption(
                "aurora",
                "Aurora Borealis",
                "https://images.unsplash.com/photo-1531366930477-4fbd595da335?q=80&w=2400&auto=format&fit=crop",
                "#2DD4BF",
                "Landscape"
            ),
            ThemeOption(
                "ocean", 
                "Pacific Blue", 
                "https://images.unsplash.com/photo-1505118380757-91f5f45d8de4?q=80&w=2400&auto=format&fit=crop", 
                "#0EA5E9",
                "Landscape"
            ),
            ThemeOption(
                "autumn",
                "Autumn Ember",
                "https://images.unsplash.com/photo-1506126613408-eca07ce68773?q=80&w=2400&auto=format&fit=crop",
                "#EA580C",
                "Landscape"
            ),
            
            // City
            ThemeOption(
                "cyber",
                "Cyber City",
                "https://images.unsplash.com/photo-1514565131-fce0801e5785?q=80&w=2400&auto=format&fit=crop",
                "#F472B6",
                "City"
            ),
            ThemeOption(
                "tokyo_night",
                "Tokyo Neon",
                "https://images.unsplash.com/photo-1540959733332-eab4deabeeaf?q=80&w=2400&auto=format&fit=crop",
                "#818CF8",
                "City"
            ),
            ThemeOption(
                "ny_skyline",
                "Gotham Spirit",
                "https://images.unsplash.com/photo-1496442226666-8d4d0e62e6e9?q=80&w=2400&auto=format&fit=crop",
                "#FCD34D",
                "City"
            ),

            // Abstract & Gradient
            ThemeOption(
                "lavender",
                "Lavender Mist",
                "https://images.unsplash.com/photo-1496101021205-f3763f68d6f5?q=80&w=2400&auto=format&fit=crop",
                "#A855F7",
                "Abstract"
            ),
            ThemeOption(
                "solar",
                "Solar Flare",
                "https://images.unsplash.com/photo-1541119638723-c51cbe2262aa?q=80&w=2400&auto=format&fit=crop",
                "#FB923C",
                "Abstract"
            ),
            ThemeOption(
                "crimson",
                "Crimson Dusk",
                "https://images.unsplash.com/photo-1419242902214-272b3f66ee7a?q=80&w=2400&auto=format&fit=crop",
                "#F87171",
                "Abstract"
            ),
            ThemeOption(
                "nordic",
                "Nordic Ice",
                "https://images.unsplash.com/photo-1516616370751-86d6bd8b03f0?q=80&w=2400&auto=format&fit=crop",
                "#22D3EE",
                "Minimal"
            ),
            ThemeOption(
                "volcanic",
                "Volcanic Ash",
                "https://images.unsplash.com/photo-1467348733814-393e0bc59639?q=80&w=2400&auto=format&fit=crop",
                "#9CA3AF",
                "Minimal"
            ),
            ThemeOption(
                "minimalist", 
                "Minimal Slate", 
                "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?q=80&w=2400&auto=format&fit=crop", 
                "#64748B",
                "Minimal"
            )
        )
    }
}
