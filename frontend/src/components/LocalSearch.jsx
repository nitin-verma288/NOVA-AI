import React, { useState } from 'react';
import api from '../services/api';
import { motion, AnimatePresence } from 'framer-motion';
import { Search, Folder, File, FileText, Calendar, Database, Layers } from 'lucide-react';

const LocalSearch = () => {
  const [query, setQuery] = useState('');
  const [rootPath, setRootPath] = useState('');
  const [results, setResults] = useState([]);
  const [searching, setSearching] = useState(false);

  const handleSearch = async (e) => {
    e.preventDefault();
    if (!query.trim()) return;

    setSearching(true);
    try {
      const response = await api.post('/search/local', {
        query: query,
        rootPath: rootPath.trim() === '' ? null : rootPath
      });
      setResults(response.data);
    } catch (e) {
      console.error('Failed to run local search', e);
    } finally {
      setSearching(false);
    }
  };

  const getFileIcon = (type) => {
    if (type === 'folder') return <Folder className="w-4 h-4 text-zinc-400" />;
    if (type === 'pdf') return <FileText className="w-4 h-4 text-zinc-400" />;
    return <File className="w-4 h-4 text-zinc-400" />;
  };

  return (
    <div className="flex-1 overflow-hidden flex flex-col h-full bg-[#09090b] p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3 border-b border-zinc-800 pb-5 shrink-0">
        <div className="w-10 h-10 border border-zinc-800 bg-zinc-900 flex items-center justify-center rounded-xl">
          <Search className="w-5 h-5 text-zinc-300" />
        </div>
        <div>
          <h2 className="text-base font-bold text-white tracking-tight">Local File System Search</h2>
          <p className="text-xs text-zinc-400">Index directories recursively, matching filenames or code contents on the fly</p>
        </div>
      </div>

      {/* Input controls form */}
      <form onSubmit={handleSearch} className="premium-card p-5 grid grid-cols-1 md:grid-cols-12 gap-4 shrink-0">
        <div className="md:col-span-6">
          <label className="block text-[10px] font-semibold uppercase tracking-wider text-zinc-400 mb-1.5">Search Query</label>
          <input 
            type="text" 
            required
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="e.g. ChatController or secret token"
            className="clerk-input text-xs"
          />
        </div>

        <div className="md:col-span-4">
          <label className="block text-[10px] font-semibold uppercase tracking-wider text-zinc-400 mb-1.5">Root Folder Path (Optional)</label>
          <input 
            type="text" 
            value={rootPath}
            onChange={(e) => setRootPath(e.target.value)}
            placeholder="Defaults to active workspace"
            className="clerk-input text-xs"
          />
        </div>

        <div className="md:col-span-2 flex items-end">
          <button 
            type="submit" 
            disabled={searching || !query.trim()}
            className="stripe-btn-primary w-full py-2"
          >
            {searching ? 'Searching...' : 'Run Search'}
          </button>
        </div>
      </form>

      {/* Results grid */}
      <div className="flex-grow overflow-hidden flex flex-col space-y-3">
        <span className="text-[10px] font-semibold text-zinc-500 uppercase tracking-wider">
          Found {results.length} Matches
        </span>

        <div className="flex-grow overflow-y-auto space-y-3 pr-1">
          {searching && results.length === 0 ? (
            <div className="py-20 text-center text-zinc-500 text-xs tracking-wider animate-pulse">Running crawler...</div>
          ) : results.length === 0 ? (
            <div className="py-16 text-center border border-dashed border-zinc-800 rounded-2xl text-zinc-500 text-xs">
              No results found. Adjust search query or check the target root path.
            </div>
          ) : (
            <AnimatePresence>
              {results.map((res, index) => (
                <motion.div
                  key={index}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.15, delay: Math.min(index * 0.015, 0.2) }}
                  className="premium-card p-4 flex flex-col gap-3 group"
                >
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="w-8 h-8 bg-zinc-800 border border-zinc-700 flex items-center justify-center rounded-lg shrink-0">
                        {getFileIcon(res.type)}
                      </div>
                      <div className="min-w-0">
                        <h4 className="text-xs font-semibold text-white leading-tight flex items-center gap-2">
                          <span className="truncate">{res.name}</span>
                          <span className="py-0.5 px-1.5 rounded bg-zinc-800 text-[8px] text-zinc-400 font-mono uppercase tracking-wider border border-zinc-750 shrink-0">
                            {res.type}
                          </span>
                        </h4>
                        <p className="text-[10px] text-zinc-500 font-mono mt-1 break-all select-all">
                          {res.path}
                        </p>
                      </div>
                    </div>

                    <div className="flex flex-col items-end shrink-0 text-[10px] text-zinc-550 font-mono select-none">
                      <span className="flex items-center gap-1.5">
                        <Database className="w-3.5 h-3.5 text-zinc-650" />
                        {(res.size / 1024).toFixed(1)} KB
                      </span>
                      <span className="flex items-center gap-1.5 mt-1">
                        <Calendar className="w-3.5 h-3.5 text-zinc-650" />
                        {res.lastModified}
                      </span>
                    </div>
                  </div>

                  {res.matchSnippet && (
                    <div className="bg-zinc-900/40 border border-zinc-850 p-3 rounded-xl flex items-start gap-2.5">
                      <Layers className="w-4 h-4 mt-0.5 text-zinc-500 shrink-0" />
                      <pre className="text-xs font-mono text-zinc-350 whitespace-pre-wrap leading-relaxed truncate-3-lines flex-grow">
                        {res.matchSnippet}
                      </pre>
                    </div>
                  )}
                </motion.div>
              ))}
            </AnimatePresence>
          )}
        </div>
      </div>
    </div>
  );
};

export default LocalSearch;
