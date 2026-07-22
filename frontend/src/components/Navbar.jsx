import React, { useEffect, useState } from 'react';
import { useTheme } from '../context/ThemeContext';
import { Sun, Moon, Database, PanelLeft, Menu } from 'lucide-react';
import api from '../services/api';

const Navbar = ({ activeTab, sidebarCollapsed, setSidebarCollapsed, mobileDrawerOpen, setMobileDrawerOpen }) => {
  const { theme, toggleTheme } = useTheme();
  const [ollamaStatus, setOllamaStatus] = useState('CHECKING');

  const checkOllamaStatus = async () => {
    try {
      await api.get('/chat/history');
      setOllamaStatus('ONLINE');
    } catch (e) {
      setOllamaStatus('OFFLINE');
    }
  };

  useEffect(() => {
    checkOllamaStatus();
    const interval = setInterval(checkOllamaStatus, 15000);
    return () => clearInterval(interval);
  }, []);

  const getViewTitle = () => {
    switch (activeTab) {
      case 'chat': return 'Conversation';
      case 'memory': return 'Memory Board';
      case 'files': return 'Files & RAG Ingestion';
      case 'code': return 'Code Workspace';
      case 'search': return 'Local Search';
      default: return 'Nova AI Terminal';
    }
  };

  return (
    <div className="h-14 border-b border-zinc-800 bg-[#0c0c0e] px-3 sm:px-4 flex items-center justify-between shrink-0 font-sans select-none w-full min-w-0">
      {/* Left section: Hamburger (Mobile) / Collapse Trigger (Desktop) & Active Tab */}
      <div className="flex items-center gap-2 sm:gap-3 min-w-0">
        {/* Mobile Hamburger Menu Button */}
        <button
          onClick={() => setMobileDrawerOpen(true)}
          className="p-1.5 rounded-lg text-zinc-400 hover:text-white hover:bg-zinc-900 transition-colors md:hidden shrink-0"
          title="Open Menu"
        >
          <Menu className="w-5 h-5" />
        </button>

        {/* Desktop Expand Sidebar Button */}
        {sidebarCollapsed && (
          <button
            onClick={() => setSidebarCollapsed(false)}
            className="hidden md:flex p-1.5 rounded-lg text-zinc-400 hover:text-white hover:bg-zinc-900 transition-colors shrink-0"
            title="Expand Sidebar"
          >
            <PanelLeft className="w-4 h-4" />
          </button>
        )}

        <h2 className="text-xs sm:text-sm font-semibold text-zinc-200 truncate">
          {getViewTitle()}
        </h2>
      </div>

      {/* Right section: Connectivity Indicators & Theme toggler */}
      <div className="flex items-center gap-2 sm:gap-4 shrink-0">
        <div className="flex items-center gap-2 sm:gap-3.5 text-[10px] sm:text-[11px] font-medium text-zinc-400 border-r border-zinc-800 pr-2 sm:pr-4">
          {/* SQLite database status - hidden on very small screens */}
          <span className="hidden sm:flex items-center gap-1.5 text-zinc-400">
            <Database className="w-3.5 h-3.5 text-zinc-500" />
            SQLite
          </span>

          {/* Ollama status */}
          <span className="flex items-center gap-1.5">
            <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${
              ollamaStatus === 'ONLINE' 
                ? 'bg-emerald-500' 
                : ollamaStatus === 'OFFLINE' 
                  ? 'bg-red-500' 
                  : 'bg-amber-500 animate-pulse'
            }`}></span>
            <span className="hidden xs:inline">LLM:</span> {ollamaStatus}
          </span>
        </div>

        {/* Theme control toggler */}
        <button 
          onClick={toggleTheme}
          className="p-1.5 rounded-lg text-zinc-400 hover:text-white hover:bg-zinc-900 transition-all shrink-0"
          title="Toggle Theme"
        >
          {theme === 'dark' ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
        </button>
      </div>
    </div>
  );
};

export default Navbar;
