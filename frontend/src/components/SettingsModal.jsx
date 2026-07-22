import React, { useState, useEffect } from 'react';
import api from '../services/api';
import { useTheme } from '../context/ThemeContext';
import { X, Sliders, Moon, Sun, Type, Brain } from 'lucide-react';

const SettingsModal = ({ isOpen, onClose }) => {
  const { theme, toggleTheme } = useTheme();
  const [settings, setSettings] = useState({
    modelName: 'gemma3:4b',
    temperature: 0.7,
    topP: 0.9,
    maxTokens: 2048,
    fontSize: 14,
    memoryEnabled: true
  });
  
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!isOpen) return;

    const loadSettings = async () => {
      setLoading(true);
      try {
        const settingsRes = await api.get('/user/settings');
        setSettings(settingsRes.data);
      } catch (e) {
        console.error('Failed to load settings', e);
      } finally {
        setLoading(false);
      }
    };
    loadSettings();
  }, [isOpen]);

  const handleSave = async () => {
    setSaving(true);
    try {
      const response = await api.put('/user/settings', settings);
      setSettings(response.data);
      onClose();
    } catch (e) {
      console.error('Failed to update settings', e);
    } finally {
      setSaving(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 p-3 sm:p-4 font-sans select-none animate-fade-in">
      <div className="w-full max-w-[480px] rounded-2xl bg-[#18181b] border border-zinc-800 overflow-hidden flex flex-col max-h-[90vh] max-h-[90dvh] shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between p-4 sm:p-5 border-b border-zinc-800">
          <div className="flex items-center gap-2.5">
            <Sliders className="w-4 h-4 sm:w-4.5 sm:h-4.5 text-zinc-350" />
            <h3 className="text-xs sm:text-sm font-bold text-white uppercase tracking-wider">Interface Configuration</h3>
          </div>
          <button 
            onClick={onClose} 
            className="p-1.5 rounded-lg text-zinc-400 hover:text-white hover:bg-zinc-900 transition"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Content */}
        <div className="p-4 sm:p-6 overflow-y-auto space-y-4 sm:space-y-5 flex-grow text-xs text-zinc-300">
          {loading ? (
            <div className="py-20 text-center text-zinc-400 font-medium uppercase tracking-wider animate-pulse">Syncing configuration variables...</div>
          ) : (
            <>
              {/* LLM Model Options */}
              <div className="space-y-2">
                <label className="block text-[10px] font-semibold uppercase tracking-wider text-zinc-400">
                  Active Local LLM Model
                </label>
                <select 
                  value={settings.modelName}
                  onChange={(e) => setSettings({ ...settings, modelName: e.target.value })}
                  className="clerk-input py-2 bg-[#09090b]"
                >
                  <option value="gemma3:4b">Gemma 3 (4B - Default)</option>
                  <option value="llama3.2">Llama 3.2 (3B - Fast & Smart)</option>
                  <option value="gemma">Gemma (7B - Great Coding)</option>
                  <option value="mistral">Mistral (7B - Highly Versatile)</option>
                </select>
                <p className="text-[10px] text-zinc-500 italic">Verify that the target model is loaded locally in your Ollama directory.</p>
              </div>

              {/* Temperature & Max Tokens */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 border-t border-zinc-800 pt-4">
                <div className="space-y-2.5">
                  <label className="block text-[10px] font-semibold uppercase tracking-wider text-zinc-400">
                    Temperature: {settings.temperature}
                  </label>
                  <input 
                    type="range" 
                    min="0" 
                    max="1.2" 
                    step="0.1"
                    value={settings.temperature}
                    onChange={(e) => setSettings({ ...settings, temperature: parseFloat(e.target.value) })}
                    className="w-full h-1 bg-zinc-800 rounded-lg appearance-none cursor-pointer accent-zinc-200"
                  />
                  <p className="text-[10px] text-zinc-500">Lower = focused/precise, Higher = creative/random.</p>
                </div>

                <div className="space-y-2">
                  <label className="block text-[10px] font-semibold uppercase tracking-wider text-zinc-400">
                    Max Tokens
                  </label>
                  <input 
                    type="number" 
                    value={settings.maxTokens}
                    onChange={(e) => setSettings({ ...settings, maxTokens: parseInt(e.target.value) || 1024 })}
                    className="clerk-input py-2"
                  />
                </div>
              </div>

              {/* Theme & Font Size */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 border-t border-zinc-800 pt-4">
                <div className="space-y-2">
                  <label className="block text-[10px] font-semibold uppercase tracking-wider text-zinc-405">
                    Interface Theme
                  </label>
                  <button 
                    onClick={toggleTheme}
                    className="stripe-btn-secondary w-full py-2 flex items-center justify-center gap-1.5"
                  >
                    {theme === 'dark' ? (
                      <>
                        <Sun className="w-3.5 h-3.5" />
                        <span>Light Mode</span>
                      </>
                    ) : (
                      <>
                        <Moon className="w-3.5 h-3.5" />
                        <span>Dark Mode</span>
                      </>
                    )}
                  </button>
                </div>

                <div className="space-y-2">
                  <label className="block text-[10px] font-semibold uppercase tracking-wider text-zinc-405">
                    Font Size (px)
                  </label>
                  <input 
                    type="number" 
                    value={settings.fontSize}
                    onChange={(e) => setSettings({ ...settings, fontSize: parseInt(e.target.value) || 14 })}
                    className="clerk-input py-2"
                  />
                </div>
              </div>

              {/* Memory System Switcher */}
              <div className="flex items-center justify-between border-t border-zinc-800 pt-4">
                <div className="space-y-0.5">
                  <label className="block text-[10px] font-semibold uppercase tracking-wider text-zinc-400">
                    Autonomous Profile Memory
                  </label>
                  <p className="text-[10px] text-zinc-500">Enables background biographical indexing from conversation exchanges.</p>
                </div>
                <label className="relative inline-flex items-center cursor-pointer select-none">
                  <input 
                    type="checkbox" 
                    checked={settings.memoryEnabled}
                    onChange={(e) => setSettings({ ...settings, memoryEnabled: e.target.checked })}
                    className="sr-only peer"
                  />
                  <div className="w-9 h-5 bg-zinc-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full after:content-[''] after:absolute after:top-[2px] after:start-[2px] after:bg-zinc-400 after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-white peer-checked:after:bg-zinc-950"></div>
                </label>
              </div>
            </>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-2 p-5 border-t border-zinc-800 bg-[#121214]">
          <button 
            onClick={onClose}
            className="stripe-btn-secondary text-xs py-1.5 px-3"
          >
            Discard
          </button>
          <button 
            onClick={handleSave}
            disabled={saving || loading}
            className="stripe-btn-primary text-xs py-1.5 px-4 font-semibold"
          >
            {saving ? 'Saving...' : 'Save settings'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default SettingsModal;
