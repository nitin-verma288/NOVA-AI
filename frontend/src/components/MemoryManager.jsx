import React, { useState, useEffect } from 'react';
import api from '../services/api';
import { motion, AnimatePresence } from 'framer-motion';
import { Brain, Search, Trash2, Plus, Download, Upload, Tag } from 'lucide-react';

const CATEGORIES = ['ALL', 'Bio', 'Preference', 'Fact', 'Constraint', 'Work', 'Other'];

const MemoryManager = () => {
  const [memories, setMemories] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  const [loading, setLoading] = useState(false);
  const [newFact, setNewFact] = useState('');
  const [newCategory, setNewCategory] = useState('Fact');
  const [importStatus, setImportStatus] = useState('');

  const fetchMemories = async () => {
    setLoading(true);
    try {
      let res;
      if (searchQuery.trim().length > 0) {
        res = await api.get(`/memory/search?query=${encodeURIComponent(searchQuery)}`);
      } else if (selectedCategory !== 'ALL') {
        res = await api.get(`/memory/category/${selectedCategory}`);
      } else {
        res = await api.get('/memory/all');
      }
      setMemories(res.data);
    } catch (e) {
      console.error('Failed to load memories', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const timer = setTimeout(() => {
      fetchMemories();
    }, 250);
    return () => clearTimeout(timer);
  }, [searchQuery, selectedCategory]);

  const handleAdd = async (e) => {
    e.preventDefault();
    if (!newFact.trim()) return;

    try {
      const response = await api.post('/memory/add', {
        fact: newFact,
        category: newCategory
      });
      setMemories((prev) => [response.data, ...prev]);
      setNewFact('');
    } catch (e) {
      console.error('Failed to create memory', e);
    }
  };

  const handleDelete = async (id) => {
    try {
      await api.delete(`/memory/delete/${id}`);
      setMemories((prev) => prev.filter((m) => m.id !== id));
    } catch (e) {
      console.error('Failed to remove memory', e);
    }
  };

  const handleExport = async () => {
    try {
      const response = await api.get('/memory/export', { responseType: 'blob' });
      const blob = new Blob([response.data], { type: 'application/json' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `nova_memory_profile_${new Date().toISOString().slice(0,10)}.json`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch (e) {
      console.error('Failed to export memories', e);
    }
  };

  const handleImport = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = async (event) => {
      try {
        const jsonContent = event.target?.result;
        if (typeof jsonContent === 'string') {
          await api.post('/memory/import', jsonContent);
          setImportStatus('SUCCESS');
          fetchMemories();
          setTimeout(() => setImportStatus(''), 3000);
        }
      } catch (err) {
        setImportStatus('ERROR');
        setTimeout(() => setImportStatus(''), 3000);
      }
    };
    reader.readAsText(file);
  };

  return (
    <div className="flex-1 overflow-y-auto lg:overflow-hidden flex flex-col h-full bg-[#09090b] p-3 sm:p-4 md:p-6 space-y-4 sm:space-y-6 w-full min-w-0">
      {/* Header section */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 sm:gap-4 border-b border-zinc-800 pb-4 sm:pb-5 shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 sm:w-10 sm:h-10 border border-zinc-800 bg-zinc-900 flex items-center justify-center rounded-xl shrink-0">
            <Brain className="w-4 h-4 sm:w-5 sm:h-5 text-zinc-300" />
          </div>
          <div>
            <h2 className="text-sm sm:text-base font-bold text-white tracking-tight">Autonomous Profile Memories</h2>
            <p className="text-[11px] sm:text-xs text-zinc-400">Context variables collected proactively to enrich LLM response generation</p>
          </div>
        </div>

        {/* Export / Import operations */}
        <div className="flex items-center gap-2 self-start sm:self-auto">
          <button 
            onClick={handleExport}
            className="stripe-btn-secondary py-1.5 px-2.5 sm:px-3 text-xs"
          >
            <Download className="w-3.5 h-3.5 text-zinc-400" />
            <span className="hidden xs:inline">Export</span> Backup
          </button>
          
          <label className="stripe-btn-secondary py-1.5 px-2.5 sm:px-3 text-xs cursor-pointer select-none">
            <Upload className="w-3.5 h-3.5 text-zinc-400" />
            {importStatus === 'SUCCESS' ? 'Restored' : importStatus === 'ERROR' ? 'Failed' : 'Restore'}
            <input type="file" accept=".json" onChange={handleImport} className="hidden" />
          </label>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto lg:overflow-hidden grid grid-cols-1 lg:grid-cols-3 gap-4 sm:gap-6 min-w-0">
        {/* Left Side: Create memory fact manually */}
        <div className="lg:col-span-1 premium-card p-4 sm:p-6 h-fit space-y-4 sm:space-y-5">
          <h3 className="text-xs font-bold text-white uppercase tracking-wider flex items-center gap-2 border-b border-zinc-800 pb-3">
            <Plus className="w-3.5 h-3.5 text-zinc-400" />
            Inject Fact Manually
          </h3>
          
          <form onSubmit={handleAdd} className="space-y-4">
            <div>
              <label className="block text-[10px] font-semibold uppercase tracking-wider text-zinc-400 mb-1.5 sm:mb-2">Category type</label>
              <select 
                value={newCategory}
                onChange={(e) => setNewCategory(e.target.value)}
                className="clerk-input py-2 bg-[#09090b]"
              >
                {CATEGORIES.slice(1).map((cat) => (
                  <option key={cat} value={cat}>{cat}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-[10px] font-semibold uppercase tracking-wider text-zinc-400 mb-1.5 sm:mb-2">Statement Fact</label>
              <textarea 
                required
                rows="3"
                value={newFact}
                onChange={(e) => setNewFact(e.target.value)}
                placeholder="e.g. User prefers Python for microservices and React for frontend development."
                className="clerk-input resize-none"
              />
            </div>

            <button 
              type="submit" 
              className="stripe-btn-primary w-full"
            >
              Commit Fact
            </button>
          </form>
        </div>

        {/* Right Side: List & Search memories */}
        <div className="lg:col-span-2 flex flex-col space-y-4 overflow-hidden h-full min-w-0">
          {/* Search bar & Category select badges */}
          <div className="flex flex-col gap-3 shrink-0">
            <div className="relative">
              <Search className="absolute left-3 top-2.5 sm:top-3 w-4 h-4 text-zinc-500" />
              <input 
                type="text" 
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Filter memories dynamically..."
                className="w-full pl-9 pr-4 py-2 bg-[#18181b] border border-zinc-850 text-zinc-100 text-xs rounded-xl focus:outline-none focus:border-zinc-700 transition"
              />
            </div>

            {/* Badges */}
            <div className="flex items-center gap-1.5 overflow-x-auto py-1 no-scrollbar select-none">
              {CATEGORIES.map((cat) => (
                <button
                  key={cat}
                  onClick={() => setSelectedCategory(cat)}
                  className={`py-1 px-2.5 sm:px-3 rounded-full text-[11px] sm:text-xs font-medium transition duration-150 border shrink-0 ${
                    selectedCategory === cat 
                      ? 'bg-white border-white text-zinc-950 font-semibold' 
                      : 'bg-zinc-900 border-zinc-850 text-zinc-400 hover:text-white hover:bg-zinc-800'
                  }`}
                >
                  {cat}
                </button>
              ))}
            </div>
          </div>

          {/* List display */}
          <div className="flex-grow overflow-y-auto space-y-3 pr-1 min-h-[250px]">
            {loading && memories.length === 0 ? (
              <div className="py-16 text-center text-zinc-500 text-xs tracking-wider animate-pulse">Syncing memories...</div>
            ) : memories.length === 0 ? (
              <div className="py-12 sm:py-16 text-center border border-dashed border-zinc-800 rounded-2xl text-zinc-500 text-xs leading-normal px-4">
                No indexed facts match this category.
              </div>
            ) : (
              <AnimatePresence>
                {memories.map((m) => (
                  <motion.div
                    key={m.id}
                    initial={{ opacity: 0, y: 8 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, scale: 0.98 }}
                    transition={{ duration: 0.15 }}
                    className="premium-card p-3.5 sm:p-4 flex items-start justify-between gap-3 sm:gap-4 group"
                  >
                    <div className="space-y-1.5 sm:space-y-2 flex-grow min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="py-0.5 px-2 rounded bg-zinc-800 border border-zinc-750 text-[9px] font-semibold text-zinc-300 uppercase tracking-wider flex items-center gap-1">
                          <Tag className="w-2.5 h-2.5" />
                          {m.category}
                        </span>
                        <span className="text-[10px] text-zinc-500">
                          {new Date(m.createdAt).toLocaleDateString()}
                        </span>
                      </div>
                      <p className="text-zinc-300 text-xs leading-relaxed break-words">{m.fact}</p>
                    </div>

                    <button
                      onClick={() => handleDelete(m.id)}
                      className="p-1.5 rounded-lg bg-zinc-850 text-zinc-500 opacity-100 sm:opacity-0 group-hover:opacity-100 hover:bg-red-950/40 hover:text-red-400 transition shrink-0"
                      title="Delete fact"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </motion.div>
                ))}
              </AnimatePresence>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default MemoryManager;
