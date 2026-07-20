import React, { useEffect, useState } from 'react';
import { useTheme } from '../context/ThemeContext';
import { Sun, Moon, Database, HelpCircle, PanelLeft } from 'lucide-react';
import api from '../services/api';

const Navbar = ({ activeTab, sidebarCollapsed, setSidebarCollapsed }) => {
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
    <div className="h-14 border-b border-zinc-800 bg-[#0c0c0e] px-4 flex items-center justify-between shrink-0 font-sans select-none">
      {/* Left section: Collapse Trigger & Active Tab */}
      <div className="flex items-center gap-3">
        {sidebarCollapsed && (
          <button
            onClick={() => setSidebarCollapsed(false)}
            className="p-1.5 rounded-lg text-zinc-400 hover:text-white hover:bg-zinc-900 transition-colors"
            title="Expand Sidebar"
          >
            <PanelLeft className="w-4 h-4" />
          </button>
        )}
        <h2 className="text-sm font-semibold text-zinc-200">
          {getViewTitle()}
        </h2>
      </div>

      {/* Right section: Connectivity Indicators & Theme toggler */}
      <div className="flex items-center gap-4">
        <div className="flex items-center gap-3.5 text-[11px] font-medium text-zinc-400 border-r border-zinc-800 pr-4">
          {/* SQLite database status */}
          <span className="flex items-center gap-1.5 text-zinc-400">
            <Database className="w-3.5 h-3.5 text-zinc-500" />
            SQLite
          </span>

          {/* Ollama status */}
          <span className="flex items-center gap-1.5">
            <span className={`w-1.5 h-1.5 rounded-full ${
              ollamaStatus === 'ONLINE' 
                ? 'bg-emerald-500' 
                : ollamaStatus === 'OFFLINE' 
                  ? 'bg-red-500' 
                  : 'bg-amber-500 animate-pulse'
            }`}></span>
            LLM: {ollamaStatus}
          </span>
        </div>

        {/* Theme control toggler */}
        <button 
          onClick={toggleTheme}
          className="p-1.5 rounded-lg text-zinc-400 hover:text-white hover:bg-zinc-900 transition-all"
          title="Toggle Theme"
        >
          {theme === 'dark' ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
        </button>
      </div>
    </div>
  );
};

export default Navbar;
