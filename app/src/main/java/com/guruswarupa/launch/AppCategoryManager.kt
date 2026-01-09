package com.guruswarupa.launch

import android.content.pm.PackageManager

class AppCategoryManager(private val packageManager: PackageManager) {
    
    enum class AppCategory {
        SOCIAL_MEDIA,
        ENTERTAINMENT,
        PRODUCTIVITY,
        OTHER
    }
    
    // Social media app package names
    private val socialMediaPackages = setOf(
        "com.whatsapp",
        "com.facebook.katana",
        "com.facebook.orca",
        "com.instagram.android",
        "com.twitter.android",
        "com.twitter.app",
        "com.snapchat.android",
        "com.linkedin.android",
        "com.reddit.frontpage",
        "com.pinterest",
        "com.tumblr",
        "com.viber.voip",
        "com.telegram.messenger",
        "com.discord",
        "com.skype.raider",
        "com.tencent.mm", // WeChat
        "com.tencent.mobileqq", // QQ
        "com.kakao.talk",
        "com.linecorp.LINE",
        "com.zhiliaoapp.musically", // TikTok
        "com.zhiliaoapp.musically.go", // TikTok Lite
        "com.ss.android.ugc.trill", // TikTok alternative
        "com.musical.ly"
    )
    
    private val socialMediaKeywords = setOf(
        "whatsapp", "facebook", "messenger", "instagram", "twitter", "snapchat",
        "linkedin", "reddit", "pinterest", "tumblr", "viber", "telegram", "discord",
        "skype", "wechat", "qq", "kakao", "line", "tiktok", "musically", "social",
        "chat", "messaging"
    )
    
    // Entertainment app package names
    private val entertainmentPackages = setOf(
        "com.google.android.youtube",
        "app.revanced.android.youtube",
        "com.netflix.mediaclient",
        "com.amazon.avod.thirdpartyclient",
        "com.hulu.plus",
        "com.disney.disneyplus",
        "com.hbo.hbonow",
        "com.spotify.music",
        "com.soundcloud.android",
        "com.pandora.android",
        "com.google.android.apps.playgames",
        "com.epicgames.fortnite",
        "com.activision.callofduty.shooter",
        "com.king.candycrushsaga",
        "com.supercell.clashofclans",
        "com.supercell.clashroyale",
        "com.rovio.angrybirdstransformers",
        "com.ea.gp.fifamobile",
        "com.mojang.minecraftpe",
        "com.roblox.client",
        "com.tencent.ig", // PUBG Mobile
        "com.garena.game.codm", // Call of Duty Mobile
        "com.nianticlabs.pokemongo",
        "com.twitch.tv.app.android",
        "com.plexapp.android",
        "com.vudu.rentalstore.android",
        "com.cbs.app",
        "com.foxnews.android",
        "com.cnn.mobile.android.phone",
        "com.bbc.iplayer.android",
        "com.paramountplus.app",
        "com.crunchyroll.crunchyroid",
        "com.funimation.funimationnow",
        "com.viki.android",
        "com.viu.phone",
        "com.iflix.mobile",
        "com.hotstar.mobile",
        "com.startv.hotstar",
        "com.airtelxstream.in",
        "com.zee5.media",
        "com.sonyliv",
        "com.voot.android"
    )
    
    private val entertainmentKeywords = setOf(
        "youtube", "netflix", "hulu", "disney", "hbo", "spotify", "pandora",
        "game", "gaming", "play", "video", "movie", "tv", "stream", "music",
        "entertainment", "twitch", "plex", "vudu", "cbs", "fox", "cnn", "bbc",
        "paramount", "crunchyroll", "funimation", "viki", "viu", "iflix", "hotstar",
        "zee5", "sonyliv", "voot", "mx player", "player", "streaming", "watch",
        "show", "series", "episode", "season", "film", "cinema", "theater"
    )
    
    // Productivity app package names
    private val productivityPackages = setOf(
        "com.google.android.apps.docs.editors.docs",
        "com.google.android.apps.docs.editors.sheets",
        "com.google.android.apps.docs.editors.slides",
        "com.microsoft.office.excel",
        "com.microsoft.office.word",
        "com.microsoft.office.powerpoint",
        "com.microsoft.office.onenote",
        "com.microsoft.office.outlook",
        "com.microsoft.office.officehub",
        "com.adobe.reader",
        "com.adobe.acrobat.reader",
        "com.dropbox.android",
        "com.google.android.apps.photos",
        "com.google.android.apps.drive",
        "com.google.android.gm",
        "com.google.android.calendar",
        "com.google.android.keep",
        "com.google.android.apps.tasks",
        "com.evernote",
        "com.todoist",
        "com.wunderlist",
        "com.microsoft.todos",
        "com.asana.app",
        "com.trello",
        "com.slack",
        "com.microsoft.teams",
        "com.zoom.videomeetings",
        "com.cisco.webex.meetings",
        "com.logmein.gotomeeting",
        "com.notion.id",
        "com.airtable.android",
        "com.workflowy",
        "com.anydo",
        "com.ticktick.task",
        "com.habitica",
        "com.habitrpg.habitica",
        "com.google.android.apps.meetings",
        "com.google.android.apps.meet"
    )
    
    private val productivityKeywords = setOf(
        "office", "word", "excel", "powerpoint", "docs", "sheets", "slides",
        "productivity", "document", "spreadsheet", "presentation", "pdf", "reader",
        "editor", "writer", "note", "notes", "task", "todo", "reminder", "calendar",
        "email", "mail", "gmail", "outlook", "drive", "dropbox", "cloud", "storage",
        "meeting", "zoom", "teams", "slack", "collaboration", "project", "management",
        "work", "business", "professional", "workflow", "organize", "plan", "schedule"
    )
    
    /**
     * Categorizes an app based on its package name and app name
     */
    fun getAppCategory(packageName: String, appName: String? = null): AppCategory {
        val lowerPackageName = packageName.lowercase()
        val lowerAppName = (appName ?: "").lowercase()
        
        // Check package name first (most reliable)
        if (socialMediaPackages.any { lowerPackageName.startsWith(it) }) {
            return AppCategory.SOCIAL_MEDIA
        }
        
        if (entertainmentPackages.any { lowerPackageName.startsWith(it) }) {
            return AppCategory.ENTERTAINMENT
        }
        
        if (productivityPackages.any { lowerPackageName.startsWith(it) }) {
            return AppCategory.PRODUCTIVITY
        }
        
        // Check app name keywords
        val combinedText = "$lowerPackageName $lowerAppName"
        
        if (socialMediaKeywords.any { combinedText.contains(it) }) {
            return AppCategory.SOCIAL_MEDIA
        }
        
        if (entertainmentKeywords.any { combinedText.contains(it) }) {
            return AppCategory.ENTERTAINMENT
        }
        
        if (productivityKeywords.any { combinedText.contains(it) }) {
            return AppCategory.PRODUCTIVITY
        }
        
        return AppCategory.OTHER
    }
    
    /**
     * Checks if an app should show the timer dialog (social media or entertainment only)
     */
    fun shouldShowTimer(packageName: String, appName: String? = null): Boolean {
        val category = getAppCategory(packageName, appName)
        return category == AppCategory.SOCIAL_MEDIA || category == AppCategory.ENTERTAINMENT
    }
}
