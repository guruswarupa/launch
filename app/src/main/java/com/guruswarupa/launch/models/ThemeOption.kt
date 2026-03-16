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
                "https://i.pinimg.com/1200x/0e/dd/70/0edd7082a53fb8ad4cfe4d361904a0a1.jpg", 
                "#94A3B8",
                "Landscape"
            ),
            ThemeOption(
                "forest", 
                "Evergreen Forest", 
                "https://i.pinimg.com/1200x/f4/7d/d5/f47dd53023bf5efbdf2968ae145e30f8.jpg", 
                "#10B981",
                "Landscape"
            ),
            ThemeOption(
                "midnight_peak",
                "Midnight Peak",
                "https://i.pinimg.com/1200x/35/4b/76/354b76938cdf21f8b6e09944e46c3186.jpg",
                "#6366F1",
                "Landscape"
            ),
            ThemeOption(
                "desert", 
                "Golden Sands", 
                "https://i.pinimg.com/1200x/1a/77/dc/1a77dcc2b9b830d835d5ce97670fd893.jpg", 
                "#F59E0B",
                "Landscape"
            ),
            ThemeOption(
                "aurora",
                "Aurora Borealis",
                "https://i.pinimg.com/1200x/24/6d/30/246d300272e182a154a400f15618777e.jpg",
                "#2DD4BF",
                "Landscape"
            ),
            ThemeOption(
                "ocean", 
                "Pacific Blue", 
                "https://i.pinimg.com/736x/2c/90/d7/2c90d74fed30e3563155d3158a23d856.jpg", 
                "#0EA5E9",
                "Landscape"
            ),
            ThemeOption(
                "autumn",
                "Autumn Ember",
                "https://i.pinimg.com/736x/db/6f/31/db6f310af1b34a66af83037c45651827.jpg",
                "#EA580C",
                "Landscape"
            ),
            
            // City
            ThemeOption(
                "cyber",
                "Cyber City",
                "https://i.pinimg.com/1200x/cf/7f/a9/cf7fa9350a8d2a21ee70069059fa641c.jpg",
                "#F472B6",
                "City"
            ),
            ThemeOption(
                "tokyo_night",
                "Tokyo Neon",
                "https://i.pinimg.com/736x/03/0a/74/030a74346c9324c13e7939b7c6873f70.jpg",
                "#818CF8",
                "City"
            ),
            ThemeOption(
                "ny_skyline",
                "Gotham Spirit",
                "https://i.pinimg.com/1200x/a5/9a/2d/a59a2d7341995e943ea455b68a3b8cdc.jpg",
                "#FCD34D",
                "City"
            ),
              ThemeOption(
                "blr_blooms",
                "Bengaluru Blooms",
                "https://i.pinimg.com/736x/47/c1/ab/47c1abb68c056f4ba1d96d3a87512322.jpg",
                "#FCD34D",
                "City"
            ),

            // Abstract
            ThemeOption(
                "lavender",
                "Lavender Mist",
                "https://i.pinimg.com/736x/9a/02/0c/9a020c30f610d2930852e9530b153c7a.jpg",
                "#A855F7",
                "Abstract"
            ),
            ThemeOption(
                "solar",
                "Solar Flare",
                "https://i.pinimg.com/736x/c4/9a/d4/c49ad42724b3b8095c3289af5513d45f.jpg",
                "#FB923C",
                "Abstract"
            ),
            ThemeOption(
                "crimson",
                "Crimson Dusk",
                "https://i.pinimg.com/1200x/3d/6f/89/3d6f89f30253f975787b90ed52a6ff74.jpg",
                "#F87171",
                "Abstract"
            ),

            // Minimal
            ThemeOption(
                "nordic",
                "Nordic Ice",
                "https://i.pinimg.com/736x/8e/ca/6f/8eca6fe01ef8e6d6f43b1f4cbd628196.jpg",
                "#22D3EE",
                "Minimal"
            ),
            ThemeOption(
                "volcanic",
                "Volcanic Ash",
                "https://i.pinimg.com/1200x/a5/b0/1a/a5b01a17de8e3a8e795f0cf6416f842d.jpg",
                "#9CA3AF",
                "Minimal"
            ),
            ThemeOption(
                "minimalist", 
                "Minimal Slate", 
                "https://i.pinimg.com/1200x/b1/05/65/b105650530a4584e120758f032026a78.jpg", 
                "#64748B",
                "Minimal"
            )
        )
    }
}
