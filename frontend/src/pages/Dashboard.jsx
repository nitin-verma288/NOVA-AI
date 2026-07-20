import React, { useState } from 'react';
import Sidebar from '../components/Sidebar';
import Navbar from '../components/Navbar';
import ChatArea from '../components/ChatArea';
import MemoryManager from '../components/MemoryManager';
import FileAssistant from '../components/FileAssistant';
import CodeAssistant from '../components/CodeAssistant';
import LocalSearch from '../components/LocalSearch';
import SettingsModal from '../components/SettingsModal';

const Dashboard = () => {
  const [activeTab, setActiveTab] = useState('chat');
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  const renderContent = () => {
    switch (activeTab) {
      case 'chat':
        return <ChatArea sidebarCollapsed={sidebarCollapsed} />;
      case 'memory':
        return <MemoryManager />;
      case 'files':
        return <FileAssistant />;
      case 'code':
        return <CodeAssistant />;
      case 'search':
        return <LocalSearch />;
      default:
        return <ChatArea sidebarCollapsed={sidebarCollapsed} />;
    }
  };

  return (
    <div className="h-screen w-screen flex bg-[#09090b] text-zinc-100 font-sans overflow-hidden">
      {/* Main layout container */}
      <div className="flex-1 flex overflow-hidden relative">
        <Sidebar 
          activeTab={activeTab} 
          setActiveTab={setActiveTab} 
          onOpenSettings={() => setSettingsOpen(true)} 
          sidebarCollapsed={sidebarCollapsed}
          setSidebarCollapsed={setSidebarCollapsed}
        />
        
        <div className="flex-1 flex flex-col overflow-hidden h-full">
          <Navbar 
            activeTab={activeTab} 
            sidebarCollapsed={sidebarCollapsed}
            setSidebarCollapsed={setSidebarCollapsed}
          />
          <div className="flex-1 overflow-hidden bg-[#09090b]">
            {renderContent()}
          </div>
        </div>
      </div>

      {/* Configuration Settings Modal overlay */}
      <SettingsModal 
        isOpen={settingsOpen} 
        onClose={() => setSettingsOpen(false)} 
      />
    </div>
  );
};

export default Dashboard;
