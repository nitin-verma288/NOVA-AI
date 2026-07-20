import React, { useState } from 'react';
import { useChat } from '../context/ChatContext';
import { useAuth } from '../context/AuthContext';
import { 
  MessageSquare, 
  Brain, 
  FileText, 
  Code, 
  Search, 
  Plus, 
  Trash2, 
  Pin, 
  Settings, 
  LogOut, 
  Terminal, 
  PanelLeftClose 
} from 'lucide-react';

const Sidebar = ({ activeTab, setActiveTab, onOpenSettings, sidebarCollapsed, setSidebarCollapsed }) => {
  const { chats, currentChat, createChat, deleteChat, togglePinChat, selectChat } = useChat();
  const { user, logout } = useAuth();
  const [historySearch, setHistorySearch] = useState('');

  const handleNewChat = () => {
    createChat();
    setActiveTab('chat');
  };

  const navItems = [
    { id: 'chat', label: 'Chat Assistant', icon: MessageSquare },
    { id: 'memory', label: 'Memory Board', icon: Brain },
    { id: 'files', label: 'Files & RAG', icon: FileText },
    { id: 'code', label: 'Code Workspace', icon: Code },
    { id: 'search', label: 'Local Search', icon: Search }
  ];

  // Filter chats by history search query
  const filteredChats = chats.filter(c => 
    c.title.toLowerCase().includes(historySearch.toLowerCase())
  );

  if (sidebarCollapsed) {
    return null;
  }

  return (
    <div className="w-[260px] bg-[#0c0c0e] border-r border-zinc-800 flex flex-col h-full overflow-hidden shrink-0 transition-all duration-300">
      {/* Brand header */}
      <div className="flex items-center justify-between p-4 border-b border-zinc-800">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 border border-zinc-800 bg-zinc-900 flex items-center justify-center rounded-lg shadow-sm">
            <Terminal className="w-4.5 h-4.5 text-zinc-300" />
          </div>
          <div>
            <h1 className="text-xs font-bold text-white tracking-wider uppercase">NOVA AI</h1>
            <span className="text-[9px] text-zinc-500 font-mono">v1.0.0 // OFFLINE</span>
          </div>
        </div>
        <button
          onClick={() => setSidebarCollapsed(true)}
          className="p-1.5 rounded-lg text-zinc-400 hover:text-white hover:bg-zinc-950 transition-colors"
          title="Collapse Sidebar"
        >
          <PanelLeftClose className="w-4 h-4" />
        </button>
      </div>

      {/* New conversation button */}
      <div className="p-3">
        <button
          onClick={handleNewChat}
          className="stripe-btn-secondary w-full flex items-center justify-center gap-2"
        >
          <Plus className="w-4 h-4" />
          New Conversation
        </button>
      </div>

      {/* Navigation tabs list */}
      <div className="px-2 py-1 space-y-0.5 shrink-0">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = activeTab === item.id;
          return (
            <button
              key={item.id}
              onClick={() => setActiveTab(item.id)}
              className={`w-full py-1.5 px-3 rounded-lg flex items-center gap-2.5 text-xs font-medium transition-all duration-150 ${
                isActive
                  ? 'bg-zinc-800 text-white'
                  : 'text-zinc-400 hover:text-white hover:bg-zinc-900/50'
              }`}
            >
              <Icon className="w-3.5 h-3.5 shrink-0" />
              <span>{item.label}</span>
            </button>
          );
        })}
      </div>

      <div className="border-t border-zinc-800 my-2 shrink-0"></div>

      {/* Chat History Search & Listing */}
      <div className="flex-1 overflow-y-auto px-2 space-y-2 min-h-0 flex flex-col">
        <div className="relative px-1">
          <Search className="absolute left-3 top-2 w-3.5 h-3.5 text-zinc-500" />
          <input 
            type="text"
            value={historySearch}
            onChange={(e) => setHistorySearch(e.target.value)}
            placeholder="Search conversations..."
            className="w-full pl-8 pr-3 py-1.5 bg-[#09090b] border border-zinc-800 text-zinc-300 text-xs rounded-lg placeholder-zinc-500 focus:outline-none focus:border-zinc-700 transition"
          />
        </div>

        <div className="flex-grow overflow-y-auto space-y-0.5 mt-1.5">
          {filteredChats.length === 0 ? (
            <span className="block text-[10px] text-zinc-500 italic px-2.5 py-4">
              {historySearch ? 'No matches found' : 'No history'}
            </span>
          ) : (
            filteredChats.map((c) => {
              const isSelected = currentChat && currentChat.id === c.id;
              return (
                <div
                  key={c.id}
                  onClick={() => {
                    selectChat(c.id);
                    setActiveTab('chat');
                  }}
                  className={`group py-1.5 px-2.5 rounded-lg flex items-center justify-between gap-2 cursor-pointer transition-all duration-150 ${
                    isSelected
                      ? 'bg-zinc-900 text-white'
                      : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900/40'
                  }`}
                >
                  <div className="flex items-center gap-2 min-w-0">
                    <MessageSquare className="w-3.5 h-3.5 shrink-0 text-zinc-500" />
                    <span className="text-xs truncate font-normal pr-1">{c.title}</span>
                  </div>

                  <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity duration-150">
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        togglePinChat(c.id);
                      }}
                      className={`p-1 rounded hover:bg-zinc-800 ${c.isPinned ? 'text-zinc-300' : 'text-zinc-600 hover:text-zinc-300'}`}
                      title={c.isPinned ? "Unpin chat" : "Pin chat"}
                    >
                      <Pin className="w-3 h-3" />
                    </button>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        deleteChat(c.id);
                      }}
                      className="p-1 rounded hover:bg-zinc-800 text-zinc-600 hover:text-red-400"
                      title="Delete chat"
                    >
                      <Trash2 className="w-3 h-3" />
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>

      {/* User profile footer */}
      <div className="p-3 bg-[#09090b] border-t border-zinc-800 flex items-center justify-between shrink-0">
        <div className="flex items-center gap-2.5 min-w-0">
          <div className="w-8 h-8 rounded-lg bg-zinc-800 border border-zinc-700 flex items-center justify-center font-semibold text-zinc-200 uppercase text-xs">
            {user?.username?.substring(0, 2) || 'US'}
          </div>
          <div className="min-w-0">
            <span className="block text-xs font-semibold text-white truncate">{user?.username || 'SYSTEM USER'}</span>
            <span className="block text-[9px] text-zinc-500 truncate">{user?.email || 'offline@nova.local'}</span>
          </div>
        </div>

        <div className="flex items-center gap-1">
          <button
            onClick={onOpenSettings}
            className="p-1.5 rounded-lg text-zinc-400 hover:text-white hover:bg-zinc-900 transition-colors"
            title="Open Configurations"
          >
            <Settings className="w-4 h-4" />
          </button>
          <button
            onClick={logout}
            className="p-1.5 rounded-lg text-zinc-400 hover:text-red-400 hover:bg-zinc-900 transition-colors"
            title="Log Out Session"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
};

export default Sidebar;
