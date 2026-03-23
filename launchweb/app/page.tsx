'use client';

import { useState, useEffect, useRef } from 'react';
import { documentationContent } from './docs-content';

// Documentation navigation structure
const docsNavigation = [
  {
    category: 'Getting Started',
    items: [
      { id: 'introduction', label: 'Introduction', icon: '📱' },
      { id: 'installation', label: 'Installation & Setup', icon: '⚙️' },
      { id: 'permissions', label: 'Required Permissions', icon: '🔐' },
    ]
  },
  {
    category: 'Core Features',
    items: [
      { id: 'search-bar', label: 'Unified Search Bar', icon: '⌁' },
      { id: 'focus-mode', label: 'Focus Mode & Pomodoro', icon: '⏱' },
      { id: 'productivity-widgets', label: 'Productivity Widgets', icon: '⚙' },
      { id: 'app-lock', label: 'App Lock & Timers', icon: '🔐' },
      { id: 'hidden-apps', label: 'Hidden Apps & Workspaces', icon: '🗂' },
      { id: 'control-center', label: 'Control Center', icon: '🧭' },
      { id: 'app-management', label: 'Smart App Management', icon: '📊' },
      { id: 'voice-commands', label: 'Voice Commands', icon: '🎤' },
      { id: 'finance-tracker', label: 'Finance Tracker', icon: '💰' },
      { id: 'sensors', label: 'Advanced Sensors', icon: '🧲' },
      { id: 'torch', label: 'Shake to Torch', icon: '🔦' },
      { id: 'apk-sharing', label: 'APK Sharing', icon: '📦' },
      { id: 'gestures', label: 'Gesture Controls', icon: '🤏' },
      { id: 'customization', label: 'Deep Customization', icon: '🎨' },
      { id: 'privacy-dashboard', label: 'Privacy Dashboard', icon: '🛡' },
      { id: 'web-apps', label: 'Web Apps Support', icon: '🌐' },
      { id: 'speed-test', label: 'Network Speed Test', icon: '⚡' },
      { id: 'github-widget', label: 'GitHub Contributions', icon: '📈' },
    ]
  },
  {
    category: 'Reference',
    items: [
      { id: 'gestures-guide', label: 'Gestures Guide', icon: '👆' },
      { id: 'settings', label: 'Settings & Configuration', icon: '⚙️' },
      { id: 'privacy-security', label: 'Privacy & Security', icon: '🔒' },
      { id: 'faq', label: 'FAQ & Troubleshooting', icon: '❓' },
    ]
  },
];

// Simple markdown-like parser for formatting
function formatContent(text: string) {
  return text.split('\n').map((line, i) => {
    // Bold text
    const parts = line.split(/(\*\*.*?\*\*)/g);
    const formattedParts = parts.map((part, j) => {
      if (part.startsWith('**') && part.endsWith('**')) {
        return <strong key={j} className="font-semibold text-white">{part.slice(2, -2)}</strong>;
      }
      return part;
    });

    // Handle bullet points - remove the • marker since CSS adds it
    if (line.startsWith('• ')) {
      const contentWithoutMarker = line.slice(2);
      const contentParts = contentWithoutMarker.split(/(\*\*.*?\*\*)/g);
      const formattedContentParts = contentParts.map((part, j) => {
        if (part.startsWith('**') && part.endsWith('**')) {
          return <strong key={j} className="font-semibold text-white">{part.slice(2, -2)}</strong>;
        }
        return part;
      });
      return <li key={i} className="ml-4 list-disc text-slate-300">{formattedContentParts}</li>;
    }
    
    // Handle numbered lists - remove the number marker since CSS adds it
    if (/^\d+\./.test(line)) {
      const contentWithoutMarker = line.replace(/^\d+\.\s*/, '');
      const contentParts = contentWithoutMarker.split(/(\*\*.*?\*\*)/g);
      const formattedContentParts = contentParts.map((part, j) => {
        if (part.startsWith('**') && part.endsWith('**')) {
          return <strong key={j} className="font-semibold text-white">{part.slice(2, -2)}</strong>;
        }
        return part;
      });
      return <li key={i} className="ml-4 list-decimal text-slate-300">{formattedContentParts}</li>;
    }
    
    // Handle headers
    if (line.startsWith('### ')) {
      return <h4 key={i} className="text-lg font-semibold text-white mt-4 mb-2">{formattedParts}</h4>;
    }
    
    // Regular paragraph
    return <p key={i} className="mb-2 text-slate-300">{formattedParts}</p>;
  });
}

export default function Home() {
  const [activeSection, setActiveSection] = useState('introduction');
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [isHomePage, setIsHomePage] = useState(true);
  const [touchStartX, setTouchStartX] = useState<number | null>(null);

  const currentPage = documentationContent[activeSection];

  // Handle touch gestures for mobile menu - only from left edge
  const handleTouchStart = (e: React.TouchEvent) => {
    e.stopPropagation();
    const touchX = e.targetTouches[0].clientX;
    
    // Only allow swipe from left 20px edge of screen
    if (touchX > 20) {
      setTouchStartX(null);
      return;
    }
    
    setTouchStartX(touchX);
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    e.stopPropagation();
    if (touchStartX === null) return;
    
    const touchEndX = e.changedTouches[0].clientX;
    const distance = touchEndX - touchStartX;
    
    // Swipe right to open menu (from left edge only)
    if (distance > 30 && !mobileMenuOpen && !isHomePage) {
      setMobileMenuOpen(true);
    }
    
    // Swipe left to close menu
    if (distance < -30 && mobileMenuOpen) {
      setMobileMenuOpen(false);
    }
    
    setTouchStartX(null);
  };

  return (
    <div 
      className="min-h-screen bg-[#121212] text-white"
      onTouchStart={handleTouchStart}
      onTouchEnd={handleTouchEnd}
    >
      {/* Header */}
      <header className="fixed top-0 left-0 right-0 z-50 border-b border-white/10 bg-[#121212]/95 backdrop-blur supports-[backdrop-filter]:bg-[#121212]/80">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-3 sm:py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 sm:gap-3">
              <img src="/icon.png" alt="" className="w-8 h-8 sm:w-10 sm:h-10 rounded-xl" />
              <div>
                <h1 className="text-base sm:text-xl font-semibold">Launch Documentation</h1>
                <p className="text-[10px] sm:text-xs text-slate-400">User Guide v1.0</p>
              </div>
            </div>
            
            <div className="flex items-center gap-2">
              {!isHomePage && (
                <button 
                  onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
                  className="lg:hidden p-2 rounded-lg hover:bg-white/10"
                >
                  <span className="text-2xl">{mobileMenuOpen ? '✕' : '☰'}</span>
                </button>
              )}
            </div>
            
            <nav className="hidden lg:flex items-center gap-6">
              <button onClick={() => setIsHomePage(true)} className={`text-sm hover:text-white transition ${isHomePage ? 'text-white font-medium' : 'text-slate-300'}`}>Home</button>
              <button onClick={() => setIsHomePage(false)} className={`text-sm hover:text-white transition ${!isHomePage ? 'text-white font-medium' : 'text-slate-300'}`}>Documentation</button>
              <a href="https://play.google.com/store/apps/details?id=com.guruswarupa.launch" target="_blank" rel="noopener noreferrer" className="text-sm text-slate-300 hover:text-white transition">Download</a>
              <a href="https://github.com/guruswarupa/launch" target="_blank" rel="noopener noreferrer" className="text-sm text-slate-300 hover:text-white transition">GitHub</a>
            </nav>
          </div>
        </div>
      </header>

      {isHomePage ? (
        /* Home Page */
        <div className="max-w-7xl mx-auto px-4 sm:px-6 pt-24 sm:pt-32 pb-8 sm:pb-12">
          <div className="flex flex-col lg:grid lg:grid-cols-2 gap-8 sm:gap-12">
            {/* Left Column - Hero Text */}
            <div className="space-y-6 sm:space-y-8 animate-fade-in w-full">
              <h1 className="flex items-center gap-3 sm:gap-6 text-2xl sm:text-3xl font-semibold leading-tight text-white bg-gradient-to-r from-white via-slate-200 to-slate-400 bg-clip-text text-transparent">
                <img src="/icon.png" alt="" className="w-16 h-16 sm:w-24 sm:h-24 rounded-xl sm:rounded-2xl shadow-2xl shadow-sky-500/20 flex-shrink-0" />
                <span className="leading-tight">Launch - Productive Launcher</span>
              </h1>
              <p className="text-base sm:text-lg text-slate-300 leading-relaxed max-w-xl">
                A clean, efficient, and minimalist Android launcher built for focus and productivity.
              </p>
              <div className="flex flex-col sm:flex-row flex-wrap gap-3 sm:gap-4">
                <a
                  href="https://play.google.com/store/apps/details?id=com.guruswarupa.launch"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center justify-center rounded-full bg-gradient-to-r from-sky-500 to-indigo-600 px-6 py-3 sm:px-8 sm:py-4 text-xs sm:text-sm font-semibold uppercase tracking-wide text-white shadow-xl shadow-sky-500/30 transition-all duration-300 hover:scale-110 hover:shadow-2xl hover:shadow-sky-500/40 w-full sm:w-auto"
                >
                  Download Now
                  <span className="ml-2">→</span>
                </a>
                <a
                  href="https://github.com/guruswarupa/launch"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center justify-center rounded-full border border-white/40 px-6 py-3 sm:px-8 sm:py-4 text-xs sm:text-sm font-semibold uppercase tracking-wide text-white transition-all duration-300 hover:bg-white/15 hover:border-white/60 hover:scale-110 w-full sm:w-auto"
                >
                  View on GitHub
                </a>
              </div>
            </div>

            {/* Right Column - Phone Mockup */}
            <div className="relative mx-auto w-full flex justify-center lg:justify-start">
              <div className="scale-90 sm:scale-100">
                <PhoneMockup />
              </div>
            </div>
          </div>
        </div>
      ) : (
        /* Documentation Page */
        <div className="max-w-7xl mx-auto flex h-screen overflow-hidden pt-[73px]">
          {/* Swipe indicator - visible hint on left edge */}
          {!mobileMenuOpen && (
            <div
              className="fixed left-2 top-1/2 -translate-y-1/2 z-30 lg:hidden pointer-events-none flex items-center"
              style={{ touchAction: 'none' }}
            >
              <span className="text-white/70 text-2xl leading-none">›</span>
            </div>
          )}
          
          {/* Sidebar Navigation */}
          <aside className={`fixed inset-0 left-0 z-40 w-72 bg-[#121212] border-r border-white/10 transform transition-transform duration-300 lg:translate-x-0 lg:static lg:w-auto lg:h-screen overflow-hidden ${mobileMenuOpen ? 'translate-x-0' : '-translate-x-full'} pt-[73px] lg:pt-0`}>
            <div className="h-full overflow-y-auto p-6 space-y-8 scrollbar-hide" style={{ overflowX: 'hidden' }}>
              {docsNavigation.map((section) => (
                <div key={section.category}>
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-3">{section.category}</h3>
                  <ul className="space-y-2">
                    {section.items.map((item) => (
                      <li key={item.id}>
                        <button
                          onClick={() => {
                            setActiveSection(item.id);
                            setMobileMenuOpen(false);
                          }}
                          className={`w-full text-left px-3 py-2 rounded-lg text-sm transition-all ${
                            activeSection === item.id
                              ? 'bg-white/10 text-white font-medium'
                              : 'text-slate-400 hover:text-white hover:bg-white/5'
                          }`}
                        >
                          <span className="mr-2">{item.icon}</span>
                          {item.label}
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          </aside>

          {/* Tap catcher when menu is open */}
          {mobileMenuOpen && (
            <div
              className="fixed inset-0 z-30 lg:hidden bg-black/20 backdrop-blur-sm"
              onClick={() => setMobileMenuOpen(false)}
            />
          )}

          {/* Main Content */}
          <main className="flex-1 overflow-y-auto scrollbar-hide" style={{ overflowX: 'hidden', overflowY: 'auto' }}>
            <div className="max-w-4xl px-4 sm:px-6 lg:px-12 py-4 sm:py-6 lg:py-12 pb-20">
              {currentPage ? (
              <article className="prose prose-invert max-w-none">
                <h1 className="text-2xl sm:text-3xl lg:text-4xl font-bold text-white mb-6 sm:mb-8">{currentPage.title}</h1>
                
                {currentPage.sections.map((section, index) => (
                  <section key={index} className="mb-6 sm:mb-8">
                    <h2 className="text-xl sm:text-2xl font-semibold text-white mb-3 sm:mb-4">{section.heading}</h2>
                    <div className="text-slate-300 leading-relaxed">
                      {formatContent(section.content)}
                    </div>
                  </section>
                ))}

                {/* Quick navigation */}
                <div className="mt-12 p-6 rounded-2xl bg-gradient-to-r from-sky-500/25 via-sky-500/15 to-transparent border border-white/10">
                  <h3 className="font-semibold text-white mb-3">Need Help?</h3>
                  <p className="text-sm text-slate-300 mb-4">
                    Can't find what you're looking for? Check out our FAQ or reach out to support.
                  </p>
                  <div className="flex gap-3">
                    <button 
                      onClick={() => setActiveSection('faq')}
                      className="px-4 py-2 rounded-full bg-white/10 hover:bg-white/20 transition text-sm font-medium"
                    >
                      View FAQ
                    </button>
                    <a 
                      href="https://github.com/guruswarupa/launch/issues"
                      target="_blank"
                      rel="noopener noreferrer"
                      className="px-4 py-2 rounded-full border border-white/30 hover:bg-white/10 transition text-sm font-medium"
                    >
                      Report Issue
                    </a>
                  </div>
                </div>
              </article>
            ) : (
              <div className="text-center py-20">
                <div className="text-6xl mb-6">🚀</div>
                <h2 className="text-3xl font-bold text-white mb-4">Coming Soon</h2>
                <p className="text-slate-400 text-lg">
                  This documentation section is being written. Check back soon!
                </p>
                <button 
                  onClick={() => setActiveSection('introduction')}
                  className="mt-6 px-6 py-3 rounded-full bg-gradient-to-r from-sky-500 to-indigo-600 text-white font-semibold hover:scale-105 transition"
                >
                  Back to Introduction
                </button>
              </div>
            )}
          </div>
        </main>
        </div>
      )}

      {/* Mobile menu */}
      {mobileMenuOpen && isHomePage && (
        <div className="fixed inset-y-0 left-0 right-0 z-40 bg-[#121212] border-b border-white/10 lg:hidden pt-[73px]">
          <nav className="flex flex-col p-6 space-y-4">
            <button 
              onClick={() => {
                setIsHomePage(true);
                setMobileMenuOpen(false);
              }} 
              className={`text-left text-base font-medium ${isHomePage ? 'text-white' : 'text-slate-300 hover:text-white'}`}
            >
              Home
            </button>
            <button 
              onClick={() => {
                setIsHomePage(false);
                setMobileMenuOpen(false);
              }} 
              className={`text-left text-base font-medium ${!isHomePage ? 'text-white' : 'text-slate-300 hover:text-white'}`}
            >
              Documentation
            </button>
            <a 
              href="https://play.google.com/store/apps/details?id=com.guruswarupa.launch" 
              target="_blank" 
              rel="noopener noreferrer" 
              className="text-base text-slate-300 hover:text-white"
            >
              Download
            </a>
            <a 
              href="https://github.com/guruswarupa/launch" 
              target="_blank" 
              rel="noopener noreferrer" 
              className="text-base text-slate-300 hover:text-white"
            >
              GitHub
            </a>
          </nav>
        </div>
      )}
    </div>
  );
}

// Phone mockup component showing launcher interface
function PhoneMockup() {
  const [activePage, setActivePage] = useState(2);
  const [isAnimating, setIsAnimating] = useState(false);
  const [touchStart, setTouchStart] = useState<number | null>(null);
  const [touchEnd, setTouchEnd] = useState<number | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // Handle touch/swipe gestures
  const handleTouchStart = (e: React.TouchEvent) => {
    setTouchEnd(null);
    setTouchStart(e.targetTouches[0].clientX);
  };

  const handleTouchMove = (e: React.TouchEvent) => {
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
        className="relative w-[220px] h-[460px] sm:w-[240px] sm:h-[500px] bg-gradient-to-br from-slate-700 via-slate-800 to-slate-900 rounded-[2rem] border-[4px] border-slate-600 shadow-2xl overflow-hidden transition-all duration-500 hover:scale-105 hover:shadow-sky-500/30 select-none touch-none cursor-grab active:cursor-grabbing"
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
          className="absolute inset-0 bg-black overflow-hidden touch-none pt-5 select-none"
          onTouchStart={handleTouchStart}
          onTouchMove={handleTouchMove}
          onTouchEnd={handleTouchEnd}
          onDragStart={(e) => e.preventDefault()}
        >
          {/* Screen content with swipe animation */}
          <div className="relative w-full h-full select-none pointer-events-none">
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
              <img src="/leftpage.jpeg" alt="Widgets Drawer" className="w-full h-full object-cover select-none pointer-events-none" draggable={false} />
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
              <img src="/centerpage.jpeg" alt="Home Screen" className="w-full h-full object-cover select-none pointer-events-none" draggable={false} />
            </div>

            {/* Page 3: Right - App Drawer */}
            <div 
              className={`transition-all duration-500 absolute inset-0 ${
                activePage === 3 
                  ? 'opacity-100 scale-100 translate-x-0' 
                  : activePage < 3 
                    ? 'opacity-0 scale-95 translate-x-full' 
                    : 'opacity-0 scale-95 -translate-x-full'
              }`}
            >
              <img src="/rightpage.jpeg" alt="App Drawer" className="w-full h-full object-cover select-none pointer-events-none" draggable={false} />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
