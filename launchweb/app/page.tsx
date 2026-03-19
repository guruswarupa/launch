'use client';

import { useState, useEffect, useRef } from 'react';

const features = [
  {
    title: "Unified search bar",
    detail:
      "Query apps, contacts, settings, files, Maps, Play Store, YouTube, or the browser and get instant auto-complete alongside quick math results.",
    icon: "⌁",
    accent: "from-slate-500/70 via-slate-600/70 to-slate-700/70",
  },
  {
    title: "Focus mode & Pomodoro",
    detail:
      "Start 15m–4h focus sessions with optional Do Not Disturb, lock the drawer, and let the Pomodoro timer cycle work/break sessions automatically.",
    icon: "⏱",
    accent: "from-purple-500/70 via-fuchsia-500/70 to-pink-500/70",
  },
  {
    title: "Productivity widgets",
    detail:
      "All-in-one productivity hub: calculator (basic, scientific, converters) with history, countdown timers synced to calendar events, todo list with due times/priority/recurring tasks, notification center with swipe-to-dismiss, weather powered by Open-Meteo, and persistent preferences.",
    icon: "⚙",
    accent: "from-amber-500/70 via-amber-400/70 to-amber-300/70",
  },
  {
    title: "App lock & timers",
    detail:
      "Protect apps with a PIN or biometrics, throttle usage with timers and daily limits, and see icons dim automatically once a cap is reached.",
    icon: "🔐",
    accent: "from-indigo-500/70 via-blue-500/70 to-cyan-500/70",
  },
  {
    title: "Hidden apps & workspaces",
    detail:
      "Hide stash apps, keep favorites at the top, and activate workspace filters so each screen only surfaces the apps you want right now.",
    icon: "🗂",
    accent: "from-emerald-500/70 via-teal-500/70 to-sky-500/70",
  },
  {
    title: "Control Center shortcuts",
    detail:
      "Quickly reorder toggles for Wi‑Fi, DND, audio profiles, QR scanner, screenshot, and more so the lock screen control center mirrors your workflow.",
    icon: "🧭",
    accent: "from-rose-500/70 via-red-500/70 to-orange-500/70",
  },
  {
    title: "Smart app management",
    detail:
      "Apps sorted by usage frequency with rarely used apps grouped alphabetically. Weekly usage graph, interactive pie charts, and one-tap uninstall via long press.",
    icon: "📊",
    accent: "from-green-500/70 via-emerald-500/70 to-teal-500/70",
  },
  {
    title: "Voice commands",
    detail:
      "Hands-free control with voice commands. Call contacts, send messages, open apps, search the web, or uninstall apps—just speak naturally.",
    icon: "🎤",
    accent: "from-violet-500/70 via-purple-500/70 to-fuchsia-500/70",
  },
  {
    title: "Finance tracker",
    detail:
      "Track income and expenses with notes, view recent transaction history, and monitor monthly savings. Lightweight and offline-first.",
    icon: "💰",
    accent: "from-yellow-500/70 via-amber-500/70 to-orange-500/70",
  },
  {
    title: "Advanced sensors",
    detail:
      "Built-in compass, atmospheric pressure monitor, temperature sensor, noise decibel analyzer, and workout tracker for fitness metrics.",
    icon: "🧲",
    accent: "from-teal-500/70 via-cyan-500/70 to-blue-500/70",
  },
  {
    title: "Shake to toggle torch",
    detail:
      "Shake device twice to toggle flashlight instantly. Works in background, battery efficient, and active only when screen is on.",
    icon: "🔦",
    accent: "from-orange-500/70 via-amber-500/70 to-yellow-500/70",
  },
  {
    title: "APK sharing",
    detail:
      "Share installed APKs via Bluetooth, email, or messaging apps. Access from app context menu or dock for quick sharing.",
    icon: "📦",
    accent: "from-pink-500/70 via-rose-500/70 to-red-500/70",
  },
  {
    title: "Gesture controls",
    detail:
      "Tap widgets for quick actions, long press icons for uninstall/share/rename, swipe notifications to dismiss, and customizable gesture shortcuts.",
    icon: "🤏",
    accent: "from-indigo-500/70 via-purple-500/70 to-pink-500/70",
  },
  {
    title: "Deep customization",
    detail:
      "Grid or list layout, adjustable columns, custom themes, wallpapers, font styles/colors, icon sizes, background translucency, and 24-hour clock format.",
    icon: "🎨",
    accent: "from-pink-500/70 via-purple-500/70 to-indigo-500/70",
  },
  {
    title: "Privacy dashboard",
    detail:
      "Complete privacy hub showing which apps have sensitive permissions. Track camera, microphone, location, contacts, and storage access with one-tap permission management.",
    icon: "🛡",
    accent: "from-cyan-500/70 via-blue-500/70 to-indigo-500/70",
  },
  {
    title: "Web apps support",
    detail:
      "Add any website as a PWA-style app. Browse with built-in WebView, custom address bar, and full-screen mode. Access your favorite sites like native apps.",
    icon: "🌐",
    accent: "from-blue-500/70 via-sky-500/70 to-cyan-500/70",
  },
  {
    title: "Network speed test",
    detail:
      "Built-in speed test widget measuring download/upload speeds, ping, and jitter. Track mobile and WiFi data usage with real-time network statistics.",
    icon: "⚡",
    accent: "from-green-500/70 via-lime-500/70 to-emerald-500/70",
  },
  {
    title: "GitHub contributions",
    detail:
      "Visual GitHub contribution graph showing your commit activity. Track total contributions, current streak, and longest streak with yearly overview.",
    icon: "📈",
    accent: "from-gray-500/70 via-slate-500/70 to-zinc-500/70",
  },
];

const stats = [
  { label: "Active Users", value: "1.5K+", icon: "👥" },
  { label: "Minutes Saved Daily", value: "42", icon: "⏱️" },
  { label: "Countries", value: "72+", icon: "🌍" },
];

const testimonials = [
  {
    quote:
      "Launch keeps my commute routine tidy without hunting for apps every morning. It just feels lighter.",
    name: "Nora, product designer",
    avatar: "👩‍🎨",
  },
  {
    quote:
      "I needed power gestures and silence controls in one place. Launch delivers both without overloading the UI.",
    name: "Mario, indie developer",
    avatar: "👨‍💻",
  },
  {
    quote:
      "The focus mode and app timers have completely changed how I use my phone. I'm so much more productive now!",
    name: "Alex, student",
    avatar: "🧑‍🎓",
  },
];

// Phone mockup component showing launcher interface
function PhoneMockup() {
  const [activePage, setActivePage] = useState(2);
  const [isAnimating, setIsAnimating] = useState(false);
  const [touchStart, setTouchStart] = useState<number | null>(null);
  const [touchEnd, setTouchEnd] = useState<number | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // Handle touch/swipe gestures
  const handleTouchStart = (e: React.TouchEvent) => {
    e.preventDefault();
    setTouchEnd(null);
    setTouchStart(e.targetTouches[0].clientX);
  };

  const handleTouchMove = (e: React.TouchEvent) => {
    e.preventDefault();
    setTouchEnd(e.targetTouches[0].clientX);
  };

  const handleTouchEnd = () => {
    if (!touchStart || !touchEnd) return;
    
    const distance = touchStart - touchEnd;
    const isLeftSwipe = distance > 50;
    const isRightSwipe = distance < -50;
    
    if (isLeftSwipe) {
      setActivePage(prev => prev >= 3 ? 1 : prev + 1);
      setIsAnimating(true);
      setTimeout(() => setIsAnimating(false), 500);
    }
    
    if (isRightSwipe) {
      setActivePage(prev => prev <= 1 ? 3 : prev - 1);
      setIsAnimating(true);
      setTimeout(() => setIsAnimating(false), 500);
    }
    
    setTouchStart(null);
    setTouchEnd(null);
  };

  // Handle mouse drag for desktop
  const [mouseDownX, setMouseDownX] = useState(0);
  
  const handleMouseDown = (e: React.MouseEvent) => {
    setMouseDownX(e.clientX);
  };
  
  const handleMouseUp = (e: React.MouseEvent) => {
    if (!mouseDownX) return;
    
    const distance = mouseDownX - e.clientX;
    const isLeftSwipe = distance > 50;
    const isRightSwipe = distance < -50;
    
    if (isLeftSwipe) {
      setActivePage(prev => prev >= 3 ? 1 : prev + 1);
      setIsAnimating(true);
      setTimeout(() => setIsAnimating(false), 500);
    }
    
    if (isRightSwipe) {
      setActivePage(prev => prev <= 1 ? 3 : prev - 1);
      setIsAnimating(true);
      setTimeout(() => setIsAnimating(false), 500);
    }
    
    setMouseDownX(0);
  };

  // Handle keyboard navigation
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'ArrowLeft') {
        setActivePage(prev => prev >= 3 ? 1 : prev + 1);
        setIsAnimating(true);
        setTimeout(() => setIsAnimating(false), 500);
      } else if (e.key === 'ArrowRight') {
        setActivePage(prev => prev <= 1 ? 3 : prev - 1);
        setIsAnimating(true);
        setTimeout(() => setIsAnimating(false), 500);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  return (
    <div className="relative mx-auto">
      {/* Realistic Phone Frame - Samsung S23 Ultra style */}
      <div 
        ref={containerRef}
        className="relative w-[260px] h-[540px] sm:w-[280px] sm:h-[580px] bg-gradient-to-br from-slate-700 via-slate-800 to-slate-900 rounded-[2rem] border-[4px] border-slate-600 shadow-2xl overflow-hidden transition-all duration-500 hover:scale-105 hover:shadow-sky-500/30 select-none touch-none cursor-grab active:cursor-grabbing"
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
        onMouseDown={handleMouseDown}
        onMouseUp={handleMouseUp}
        onMouseLeave={() => setMouseDownX(0)}
        role="region"
        aria-label="Interactive phone mockup - Swipe or use arrow keys to navigate"
        tabIndex={0}
      >
        {/* Screen notch/camera - centered punch hole */}
        <div className="absolute top-1 left-1/2 transform -translate-x-1/2 z-30">
          <div className="w-3 h-3 bg-slate-950 rounded-full border-2 border-slate-600 shadow-lg flex items-center justify-center">
            <div className="w-1.5 h-1.5 bg-slate-800 rounded-full"></div>
          </div>
        </div>
        
        {/* Power button */}
        <div className="absolute right-[-5px] top-20 w-[4px] h-16 bg-gradient-to-b from-slate-600 to-slate-700 rounded-r-sm"></div>
        
        {/* Volume buttons */}
        <div className="absolute left-[-5px] top-16 w-[4px] h-12 bg-gradient-to-b from-slate-600 to-slate-700 rounded-l-sm"></div>
        <div className="absolute left-[-5px] top-32 w-[4px] h-12 bg-gradient-to-b from-slate-600 to-slate-700 rounded-l-sm"></div>
        
        {/* Screen */}
        <div 
          className="absolute inset-0 bg-black overflow-hidden touch-none pt-5"
          onTouchStart={handleTouchStart}
          onTouchMove={handleTouchMove}
          onTouchEnd={handleTouchEnd}
        >
          {/* Screen content with swipe animation */}
          <div className="relative w-full h-full">
            {/* Swipe overlay hint - invisible but captures touch */}
            <div className="absolute inset-0 z-10"></div>
            
            {/* Page 1: Left - Widgets Drawer */}
            <div 
              className={`transition-all duration-500 absolute inset-0 ${
                activePage === 1 
                  ? 'opacity-100 scale-100 translate-x-0' 
                  : activePage > 1 
                    ? 'opacity-0 scale-95 -translate-x-full' 
                    : 'opacity-0 scale-95 translate-x-full'
              }`}
            >
              <img src="/leftpage.jpeg" alt="Widgets Drawer" className="w-full h-full object-cover" />
            </div>

            {/* Page 2: Center - Home with Workspace & Focus Mode */}
            <div 
              className={`transition-all duration-500 absolute inset-0 ${
                activePage === 2 
                  ? 'opacity-100 scale-100 translate-x-0' 
                  : activePage > 2 
                    ? 'opacity-0 scale-95 -translate-x-full' 
                    : 'opacity-0 scale-95 translate-x-full'
              }`}
            >
              <img src="/centerpage.jpeg" alt="Home Screen" className="w-full h-full object-cover" />
            </div>

            {/* Page 3: Right - Wallpaper Picker */}
            <div 
              className={`transition-all duration-500 absolute inset-0 ${
                activePage === 3 
                  ? 'opacity-100 scale-100 translate-x-0' 
                  : 'opacity-0 scale-95 translate-x-full'
              }`}
            >
              <img src="/rightpage.jpeg" alt="Wallpaper Picker" className="w-full h-full object-cover" />
            </div>
          </div>

          {/* Touch-sensitive overlay for better swipe detection */}
          <div 
            className="absolute inset-0 z-20 touch-none"
            onTouchStart={handleTouchStart}
            onTouchMove={handleTouchMove}
            onTouchEnd={handleTouchEnd}
          ></div>

          {/* Gesture indicator */}
          <div className="absolute bottom-2 left-1/2 transform -translate-x-1/2 w-20 h-1 bg-white/50 rounded-full"></div>
        </div>
      </div>

      {/* Ambient glow behind phone */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[320px] h-[600px] sm:w-[340px] sm:h-[640px] bg-gradient-to-br from-sky-500/15 to-indigo-600/15 blur-3xl -z-10 rounded-[2.5rem] animate-pulse"></div>
    </div>
  );
}

export default function Home() {
  return (
    <div className="min-h-screen bg-[#121212] text-white overflow-x-hidden">
      {/* Gradient background overlays */}
      <div className="fixed inset-0 pointer-events-none">
        <div className="absolute top-0 left-1/4 w-96 h-96 bg-sky-500/10 rounded-full blur-3xl"></div>
        <div className="absolute bottom-0 right-1/4 w-96 h-96 bg-indigo-500/10 rounded-full blur-3xl"></div>
      </div>

      <div className="relative isolate overflow-hidden px-6 py-8 sm:py-12 lg:px-8">
        <div className="relative mx-auto flex max-w-7xl flex-col gap-16">
          {/* Hero Section */}
          <section className="grid gap-8 lg:grid-cols-2 lg:items-center min-h-[85vh] sm:min-h-[90vh]">
            <div className="space-y-6 sm:space-y-8 order-1 animate-fade-in">
              <h1 className="flex items-center gap-3 sm:gap-6 text-2xl font-semibold leading-tight text-white sm:text-3xl lg:text-4xl bg-gradient-to-r from-white via-slate-200 to-slate-400 bg-clip-text text-transparent">
                <img src="/icon.png" alt="" className="w-16 h-16 sm:w-20 sm:h-20 lg:w-24 lg:h-24 rounded-xl sm:rounded-2xl shadow-2xl shadow-sky-500/20 flex-shrink-0" />
                <span className="leading-tight">Launch - Productive Launcher</span>
              </h1>
              <p className="text-base sm:text-lg text-slate-300 lg:max-w-xl leading-relaxed">
                A clean, efficient, and minimalist Android launcher built for focus and productivity.
              </p>
              <div className="flex flex-wrap gap-3 sm:gap-4">
                <a
                  href="https://play.google.com/store/apps/details?id=com.guruswarupa.launch"
                  target="_blank"
                  rel="noopener noreferrer"
                  aria-label="Download Launch on Google Play"
                >
                  <img
                    src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"
                    alt="Get it on Google Play"
                    className="h-12 sm:h-14 lg:h-16"
                  />
                </a>
                <a
                  className="inline-flex items-center justify-center rounded-full border border-white/30 px-6 py-3 sm:px-8 sm:py-4 text-xs sm:text-sm font-semibold uppercase tracking-wide text-white transition-all duration-300 hover:bg-white/10 hover:border-white/50 hover:scale-105"
                  href="#features"
                >
                  See features
                </a>
              </div>
              
              {/* Trust indicators */}
              <div className="pt-6 sm:pt-8 border-t border-white/10">
                <p className="text-xs text-slate-400 mb-3">Trusted by users worldwide</p>
                <a
                  className="group inline-flex items-center gap-2 sm:gap-3 rounded-full border border-white/30 bg-white/5 px-3 py-2 sm:px-4 sm:py-2 text-xs sm:text-sm font-semibold text-white transition hover:border-white/60 hover:bg-white/10"
                  href="https://play.google.com/store/apps/details?id=com.guruswarupa.launch"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <span className="text-amber-300">★</span>
                  <span className="text-sm font-semibold">4.9</span>
                  <span className="text-xs uppercase tracking-[0.4em] text-slate-400 hidden sm:inline">Google Play</span>
                </a>
                <div className="flex flex-wrap gap-2 sm:gap-4 mt-3">
                  <span className="text-xs text-slate-500">🔓 100% Open Source</span>
                  <span className="text-xs text-slate-500">🚫 No Ads</span>
                  <span className="text-xs text-slate-500">🔐 Privacy First</span>
                </div>
              </div>
            </div>
            
            {/* Phone Mockup */}
            <div className="order-2 flex justify-center mb-8 lg:mb-0">
              <PhoneMockup />
            </div>
          </section>

          {/* Features Grid */}
          <section id="features" className="grid gap-6 sm:gap-8 lg:grid-cols-2 xl:grid-cols-3">
            {features.map((feature, index) => (
              <div
                key={feature.title}
                className="group relative rounded-3xl border border-white/10 bg-gradient-to-br from-white/[0.08] via-white/[0.02] to-transparent p-8 shadow-xl shadow-black/50 backdrop-blur-xl transition-all duration-500 hover:border-white/25 hover:bg-white/[0.12] hover:scale-[1.02] hover:shadow-2xl hover:shadow-sky-500/10 overflow-hidden"
                style={{ animationDelay: `${index * 80}ms` }}
              >
                {/* Animated background glow */}
                <div className={`absolute inset-0 bg-gradient-to-br ${feature.accent} opacity-0 group-hover:opacity-10 transition-opacity duration-700 blur-2xl`}></div>
                
                {/* Subtle shimmer effect */}
                <div className="absolute inset-0 bg-gradient-to-r from-transparent via-white/[0.06] to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-700 animate-shimmer"></div>
                
                {/* Icon container with enhanced styling */}
                <div
                  className={`relative mb-6 h-16 w-16 sm:h-20 sm:w-20 rounded-2xl sm:rounded-3xl bg-gradient-to-br ${feature.accent} flex items-center justify-center text-3xl sm:text-4xl shadow-2xl shadow-black/50 transition-all duration-500 group-hover:scale-110 group-hover:rotate-6 group-hover:shadow-sky-500/30 ring-1 ring-white/20 group-hover:ring-white/40`}
                >
                  <span className="drop-shadow-lg">{feature.icon}</span>
                </div>
                
                {/* Content */}
                <h3 className="relative text-xl sm:text-2xl font-bold text-white mb-3 tracking-tight group-hover:text-sky-200 transition-colors duration-300">{feature.title}</h3>
                <p className="relative text-sm sm:text-base text-slate-300 leading-relaxed group-hover:text-slate-200 transition-colors duration-300">{feature.detail}</p>
                
                {/* Bottom accent line */}
                <div className="absolute bottom-0 left-8 right-8 h-px bg-gradient-to-r from-transparent via-white/20 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-500"></div>
              </div>
            ))}
          </section>

          {/* Stats Section */}
          <section className="grid gap-8 sm:gap-12 rounded-2xl sm:rounded-3xl border border-white/10 bg-gradient-to-br from-nord0/60 via-nord1/40 to-nord0/60 p-6 sm:p-8 lg:p-10 backdrop-blur-sm shadow-2xl">
            <div className="space-y-4 sm:space-y-6">
              <div className="space-y-2">
                <h2 className="text-2xl sm:text-3xl lg:text-4xl font-semibold text-white">Maintain momentum</h2>
                <div className="w-16 h-1 sm:w-20 sm:h-1 lg:w-24 lg:h-1 bg-gradient-to-r from-sky-500 to-indigo-600 rounded-full"></div>
              </div>
              <p className="text-sm sm:text-base text-slate-300 leading-relaxed max-w-2xl">
                Launch learns when you most need silence and keeps your gestures consistent across devices. Focus sessions, commute quick-access, and contextual folders stay in sync so your setup is ready wherever you unlock.
              </p>
              <div className="grid gap-4 sm:gap-6 grid-cols-1 sm:grid-cols-3 pt-4 sm:pt-6">
                {stats.map((item) => (
                  <div key={item.label} className="group space-y-3 text-left p-4 rounded-xl sm:rounded-2xl bg-white/5 border border-white/5 transition-all duration-300 hover:bg-white/10 hover:border-white/20 hover:scale-105">
                    <div className="text-2xl sm:text-3xl mb-2">{item.icon}</div>
                    <p className="text-3xl sm:text-4xl font-bold bg-gradient-to-r from-white to-slate-300 bg-clip-text text-transparent">{item.value}</p>
                    <p className="text-xs uppercase tracking-wide text-slate-400 font-semibold">{item.label}</p>
                  </div>
                ))}
              </div>
            </div>
            <div className="space-y-4 sm:space-y-6 rounded-xl sm:rounded-2xl border border-white/10 bg-white/[0.07] p-6 sm:p-8 text-xs sm:text-sm text-slate-300 backdrop-blur-sm">
              <p className="text-xs uppercase tracking-[0.3em] text-slate-400 font-semibold">Testimonials</p>
              {testimonials.map((item, idx) => (
                <figure key={item.name} className="space-y-3 sm:space-y-4 group">
                  <blockquote className="text-sm sm:text-base leading-relaxed text-white italic">"{item.quote}"</blockquote>
                  <figcaption className="flex items-center gap-2 sm:gap-3">
                    <span className="text-xl sm:text-2xl">{item.avatar}</span>
                    <span className="text-xs font-semibold uppercase tracking-[0.3em] text-slate-400">
                      {item.name}
                    </span>
                  </figcaption>
                </figure>
              ))}
            </div>
          </section>

          {/* CTA Section */}
          <section className="flex flex-col gap-6 sm:gap-8 rounded-2xl sm:rounded-3xl border border-white/10 bg-gradient-to-r from-sky-500/25 via-sky-500/15 to-transparent p-8 sm:p-10 lg:p-12 text-center backdrop-blur-sm shadow-2xl relative overflow-hidden">
            <div className="absolute inset-0 bg-gradient-to-r from-transparent via-white/[0.03] to-transparent animate-shimmer"></div>
            <div className="relative z-10">
              <p className="text-xs sm:text-sm uppercase tracking-[0.4em] text-slate-300 font-semibold">Launch early access</p>
              <h2 className="text-2xl sm:text-3xl lg:text-4xl font-semibold text-white mt-2">Switch to a launcher that keeps you moving.</h2>
              <p className="text-sm sm:text-base text-slate-200 max-w-2xl mx-auto mt-4 leading-relaxed">
                Join the waiting list for early betas, deep automation recipes, and the next wave of responsive widgets we are shipping.
              </p>
              <div className="flex flex-wrap justify-center gap-4 sm:gap-6 mt-6 sm:mt-8">
                <a
                  className="group inline-flex items-center justify-center rounded-full bg-gradient-to-r from-sky-500 to-indigo-600 px-6 py-3 sm:px-8 sm:py-4 sm:py-5 text-xs sm:text-sm font-semibold uppercase tracking-wide text-white shadow-xl shadow-sky-500/30 transition-all duration-300 hover:scale-110 hover:shadow-2xl hover:shadow-sky-500/40"
                  href="#"
                >
                  Join beta
                  <span className="ml-2 transition-transform group-hover:translate-x-1">→</span>
                </a>
                <a
                  className="inline-flex items-center justify-center rounded-full border border-white/40 px-6 py-3 sm:px-8 sm:py-4 sm:py-5 text-xs sm:text-sm font-semibold uppercase tracking-wide text-white transition-all duration-300 hover:bg-white/15 hover:border-white/60 hover:scale-110"
                  href="#"
                >
                  Message the team
                </a>
              </div>
            </div>
          </section>

          {/* Privacy & Permissions Section */}
          <section className="rounded-2xl sm:rounded-3xl border border-white/10 bg-gradient-to-br from-nord0/60 via-nord1/40 to-nord0/60 p-6 sm:p-8 lg:p-10 backdrop-blur-sm shadow-2xl">
            <div className="text-center mb-6 sm:mb-8">
              <h2 className="text-2xl sm:text-3xl lg:text-4xl font-semibold text-white mb-2">🔐 Privacy & Permissions</h2>
              <p className="text-base sm:text-lg text-slate-300">100% Open Source. No ads. No tracking.</p>
            </div>
            <div className="grid gap-4 sm:gap-6 md:grid-cols-2 lg:grid-cols-3">
              <div className="p-4 sm:p-6 rounded-xl sm:rounded-2xl bg-white/5 border border-white/5 hover:bg-white/10 transition-all">
                <div className="text-2xl sm:text-3xl mb-2 sm:mb-3">👥</div>
                <h3 className="text-base sm:text-lg font-semibold text-white mb-2">Contacts</h3>
                <p className="text-sm text-slate-300">Contact search & quick actions</p>
              </div>
              <div className="p-4 sm:p-6 rounded-xl sm:rounded-2xl bg-white/5 border border-white/5 hover:bg-white/10 transition-all">
                <div className="text-2xl sm:text-3xl mb-2 sm:mb-3">📞</div>
                <h3 className="text-base sm:text-lg font-semibold text-white mb-2">Phone</h3>
                <p className="text-sm text-slate-300">Calling support</p>
              </div>
              <div className="p-4 sm:p-6 rounded-xl sm:rounded-2xl bg-white/5 border border-white/5 hover:bg-white/10 transition-all">
                <div className="text-2xl sm:text-3xl mb-2 sm:mb-3">💬</div>
                <h3 className="text-base sm:text-lg font-semibold text-white mb-2">SMS</h3>
                <p className="text-sm text-slate-300">Messaging support</p>
              </div>
              <div className="p-4 sm:p-6 rounded-xl sm:rounded-2xl bg-white/5 border border-white/5 hover:bg-white/10 transition-all">
                <div className="text-2xl sm:text-3xl mb-2 sm:mb-3">💾</div>
                <h3 className="text-base sm:text-lg font-semibold text-white mb-2">Storage</h3>
                <p className="text-sm text-slate-300">Notes, wallpapers, backups</p>
              </div>
              <div className="p-4 sm:p-6 rounded-xl sm:rounded-2xl bg-white/5 border border-white/5 hover:bg-white/10 transition-all">
                <div className="text-2xl sm:text-3xl mb-2 sm:mb-3">📊</div>
                <h3 className="text-base sm:text-lg font-semibold text-white mb-2">Usage Stats</h3>
                <p className="text-sm text-slate-300">App usage tracking & limits (optional)</p>
              </div>
              <div className="p-4 sm:p-6 rounded-xl sm:rounded-2xl bg-white/5 border border-white/5 hover:bg-white/10 transition-all">
                <div className="text-2xl sm:text-3xl mb-2 sm:mb-3">🔔</div>
                <h3 className="text-base sm:text-lg font-semibold text-white mb-2">Notifications</h3>
                <p className="text-sm text-slate-300">Notifications widget (optional)</p>
              </div>
            </div>
            <div className="mt-6 sm:mt-8 text-center">
              <p className="text-xs sm:text-sm text-slate-400">All advanced permissions are optional. Core launcher functionality works independently.</p>
            </div>
          </section>

          {/* Gestures Guide Section */}
          <section className="rounded-2xl sm:rounded-3xl border border-white/10 bg-gradient-to-br from-nord0/60 via-nord1/40 to-nord0/60 p-6 sm:p-8 lg:p-10 backdrop-blur-sm shadow-2xl">
            <div className="text-center mb-6 sm:mb-8">
              <h2 className="text-2xl sm:text-3xl lg:text-4xl font-semibold text-white mb-2">🤏 Gestures Guide</h2>
              <p className="text-base sm:text-lg text-slate-300">Quick actions at your fingertips</p>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left">
                <thead>
                  <tr className="border-b border-white/10">
                    <th className="pb-3 sm:pb-4 text-xs sm:text-sm font-semibold uppercase tracking-wide text-slate-300">Gesture</th>
                    <th className="pb-3 sm:pb-4 text-xs sm:text-sm font-semibold uppercase tracking-wide text-slate-300">Result</th>
                  </tr>
                </thead>
                <tbody className="text-xs sm:text-sm text-slate-300">
                  <tr className="border-b border-white/5">
                    <td className="py-3 sm:py-4 font-semibold text-white">Tap Time Widget</td>
                    <td className="py-3 sm:py-4">Opens Clock app</td>
                  </tr>
                  <tr className="border-b border-white/5">
                    <td className="py-3 sm:py-4 font-semibold text-white">Tap Date Widget</td>
                    <td className="py-3 sm:py-4">Opens Calendar app</td>
                  </tr>
                  <tr className="border-b border-white/5">
                    <td className="py-3 sm:py-4 font-semibold text-white">Long Press Search Bar</td>
                    <td className="py-3 sm:py-4">Opens Google in browser</td>
                  </tr>
                  <tr className="border-b border-white/5">
                    <td className="py-3 sm:py-4 font-semibold text-white">Long Press App Icon</td>
                    <td className="py-3 sm:py-4">Opens app context menu (Uninstall, Share, etc.)</td>
                  </tr>
                  <tr className="border-b border-white/5">
                    <td className="py-3 sm:py-4 font-semibold text-white">Long Press Dock App</td>
                    <td className="py-3 sm:py-4">Remove or rename app</td>
                  </tr>
                  <tr className="border-b border-white/5">
                    <td className="py-3 sm:py-4 font-semibold text-white">Type in Search Bar</td>
                    <td className="py-3 sm:py-4">Instant calculator</td>
                  </tr>
                  <tr className="border-b border-white/5">
                    <td className="py-3 sm:py-4 font-semibold text-white">Long Press Focus Icon</td>
                    <td className="py-3 sm:py-4">Enter Focus Mode setup</td>
                  </tr>
                  <tr className="border-b border-white/5">
                    <td className="py-3 sm:py-4 font-semibold text-white">Shake Device (2x)</td>
                    <td className="py-3 sm:py-4">Toggle torch/flashlight</td>
                  </tr>
                  <tr>
                    <td className="py-3 sm:py-4 font-semibold text-white">Tap Weekly Usage Day</td>
                    <td className="py-3 sm:py-4">View detailed daily usage breakdown</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}
