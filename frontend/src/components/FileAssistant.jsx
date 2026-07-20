import React, { useState, useEffect } from 'react';
import api from '../services/api';
import { motion, AnimatePresence } from 'framer-motion';
import { FileText, Upload, Trash2, HelpCircle, Eye, ArrowLeft, RefreshCw, Layers } from 'lucide-react';

const FileAssistant = () => {
  const [documents, setDocuments] = useState([]);
  const [selectedDoc, setSelectedDoc] = useState(null);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [dragActive, setDragActive] = useState(false);
  
  // Q&A State
  const [question, setQuestion] = useState('');
  const [answer, setAnswer] = useState('');
  const [querying, setQuerying] = useState(false);
  const [docContent, setDocContent] = useState('');
  const [activeTab, setActiveTab] = useState('summary'); // summary or fullText

  const fetchDocuments = async () => {
    setLoading(true);
    try {
      const res = await api.get('/file/all');
      setDocuments(res.data);
    } catch (e) {
      console.error('Failed to fetch documents', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDocuments();
  }, []);

  const handleDrag = (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === "dragenter" || e.type === "dragover") {
      setDragActive(true);
    } else if (e.type === "dragleave") {
      setDragActive(false);
    }
  };

  const handleDrop = async (e) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);

    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      uploadFile(e.dataTransfer.files[0]);
    }
  };

  const handleFileInput = (e) => {
    if (e.target.files && e.target.files[0]) {
      uploadFile(e.target.files[0]);
    }
  };

  const uploadFile = async (file) => {
    setUploading(true);
    const formData = new FormData();
    formData.append('file', file);

    try {
      const response = await api.post('/file/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      });
      setDocuments((prev) => [response.data, ...prev]);
      setSelectedDoc(response.data);
      setAnswer('');
      setQuestion('');
    } catch (e) {
      alert(e.response?.data?.message || 'Failed to upload/parse file. Verify its extension and content.');
    } finally {
      setUploading(false);
    }
  };

  const handleDelete = async (id, e) => {
    e.stopPropagation();
    if (!confirm('Are you sure you want to delete this document?')) return;

    try {
      await api.delete(`/file/${id}`);
      setDocuments((prev) => prev.filter((d) => d.id !== id));
      if (selectedDoc && selectedDoc.id === id) {
        setSelectedDoc(null);
      }
    } catch (e) {
      console.error('Failed to delete document', e);
    }
  };

  const handleQuery = async (e) => {
    e.preventDefault();
    if (!question.trim() || !selectedDoc) return;

    setQuerying(true);
    setAnswer('');
    try {
      const response = await api.post(`/file/query/${selectedDoc.id}`, {
        question: question
      });
      setAnswer(response.data);
    } catch (e) {
      setAnswer('Error: Could not retrieve answer from local assistant.');
    } finally {
      setQuerying(false);
    }
  };

  const loadFullContent = async () => {
    if (!selectedDoc) return;
    try {
      const res = await api.get(`/file/${selectedDoc.id}/content`);
      setDocContent(res.data);
    } catch (e) {
      console.error('Failed to load text content', e);
    }
  };

  useEffect(() => {
    if (selectedDoc && activeTab === 'fullText' && !docContent) {
      loadFullContent();
    }
  }, [selectedDoc, activeTab]);

  const selectDoc = (doc) => {
    setSelectedDoc(doc);
    setAnswer('');
    setQuestion('');
    setDocContent('');
    setActiveTab('summary');
  };

  return (
    <div className="flex-1 overflow-hidden flex flex-col h-full bg-[#09090b] p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3 border-b border-zinc-800 pb-5 shrink-0">
        <div className="w-10 h-10 border border-zinc-800 bg-zinc-900 flex items-center justify-center rounded-xl">
          <FileText className="w-5 h-5 text-zinc-300" />
        </div>
        <div>
          <h2 className="text-base font-bold text-white tracking-tight">Document Knowledge Workspace</h2>
          <p className="text-xs text-zinc-400">Upload PDFs, Word files, Spreadsheets, CSV, or Text and query them offline</p>
        </div>
      </div>

      <div className="flex-grow overflow-hidden">
        <AnimatePresence mode="wait">
          {!selectedDoc ? (
            /* Document List View */
            <motion.div 
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="grid grid-cols-1 lg:grid-cols-3 gap-6 h-full overflow-hidden"
            >
              {/* Left Column: Drag & Drop Zone */}
              <div className="lg:col-span-1 flex flex-col h-full justify-between gap-5">
                <div 
                  onDragEnter={handleDrag}
                  onDragOver={handleDrag}
                  onDragLeave={handleDrag}
                  onDrop={handleDrop}
                  className={`flex-grow border border-dashed rounded-2xl flex flex-col items-center justify-center p-6 text-center transition duration-200 relative ${
                    dragActive 
                      ? 'border-white bg-zinc-900/50' 
                      : 'border-zinc-850 hover:border-zinc-700 bg-zinc-900/10'
                  }`}
                >
                  <input 
                    type="file" 
                    id="file-upload-input"
                    accept=".pdf,.docx,.xls,.xlsx,.csv,.txt"
                    onChange={handleFileInput}
                    className="hidden"
                  />
                  
                  {uploading ? (
                    <div className="flex flex-col items-center gap-3 select-none">
                      <RefreshCw className="w-8 h-8 text-zinc-400 animate-spin" />
                      <p className="text-xs font-semibold text-white uppercase tracking-wider">Parsing files...</p>
                      <p className="text-[10px] text-zinc-500 italic">Running local text extraction models</p>
                    </div>
                  ) : (
                    <label htmlFor="file-upload-input" className="cursor-pointer flex flex-col items-center gap-3 select-none">
                      <div className="w-10 h-10 bg-zinc-900 border border-zinc-800 flex items-center justify-center rounded-xl">
                        <Upload className="w-5 h-5 text-zinc-400" />
                      </div>
                      <p className="text-xs font-semibold text-white">Drag & drop files here</p>
                      <p className="text-[10px] text-zinc-500">or click to browse local files</p>
                      <span className="mt-4 px-2.5 py-1 rounded bg-zinc-900 border border-zinc-850 text-[9px] text-zinc-400 font-mono">PDF, DOCX, XLSX, CSV, TXT</span>
                    </label>
                  )}
                </div>
              </div>

              {/* Right Column: Existing Documents Grid */}
              <div className="lg:col-span-2 flex flex-col space-y-4 overflow-hidden h-full">
                <h3 className="text-xs font-bold text-white uppercase tracking-wider">Indexed Knowledge Base</h3>
                
                <div className="flex-grow overflow-y-auto space-y-2.5 pr-1">
                  {loading && documents.length === 0 ? (
                    <div className="py-20 text-center text-zinc-500 text-xs tracking-wider animate-pulse">Scanning folders...</div>
                  ) : documents.length === 0 ? (
                    <div className="py-16 text-center border border-dashed border-zinc-800 rounded-2xl text-zinc-500 text-xs">
                      No documents parsed yet. Upload files to initialize indexing context.
                    </div>
                  ) : (
                    documents.map((doc) => (
                      <div 
                        key={doc.id}
                        onClick={() => selectDoc(doc)}
                        className="premium-card p-4 flex items-center justify-between gap-4 cursor-pointer group"
                      >
                        <div className="flex items-center gap-3.5 min-w-0">
                          <div className="w-9 h-9 bg-zinc-800 border border-zinc-700 flex items-center justify-center rounded-xl shrink-0">
                            <FileText className="w-4.5 h-4.5 text-zinc-350" />
                          </div>
                          <div className="min-w-0">
                            <h4 className="text-xs font-semibold text-white truncate">{doc.fileName}</h4>
                            <p className="text-[9px] text-zinc-500 mt-0.5 uppercase tracking-wide">
                              {doc.fileType} • {(doc.fileSize / 1024).toFixed(1)} KB • {new Date(doc.createdAt).toLocaleDateString()}
                            </p>
                          </div>
                        </div>

                        <div className="flex items-center gap-1.5 opacity-0 group-hover:opacity-100 transition duration-150">
                          <button className="p-1.5 rounded-lg bg-zinc-800 text-zinc-400 hover:text-white transition">
                            <Eye className="w-3.5 h-3.5" />
                          </button>
                          <button 
                            onClick={(e) => handleDelete(doc.id, e)}
                            className="p-1.5 rounded-lg bg-zinc-850 text-zinc-500 hover:bg-red-950/40 hover:text-red-400 transition"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>
            </motion.div>
          ) : (
            /* Document Detailed / Q&A View */
            <motion.div 
              initial={{ opacity: 0, x: 10 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -10 }}
              className="grid grid-cols-1 lg:grid-cols-12 gap-6 h-full overflow-hidden"
            >
              {/* Back key + Document Details Panel */}
              <div className="lg:col-span-5 flex flex-col space-y-4 overflow-hidden h-full">
                <button 
                  onClick={() => setSelectedDoc(null)}
                  className="stripe-btn-secondary self-start py-1.5 px-3 text-xs"
                >
                  <ArrowLeft className="w-3.5 h-3.5" />
                  Return to Database
                </button>

                <div className="flex-grow premium-card p-5 flex flex-col space-y-5 overflow-hidden">
                  <div className="flex items-start gap-3">
                    <div className="w-9 h-9 bg-zinc-800 border border-zinc-700 flex items-center justify-center rounded-xl shrink-0">
                      <FileText className="w-4.5 h-4.5 text-zinc-350" />
                    </div>
                    <div className="min-w-0">
                      <h3 className="text-xs font-bold text-white leading-tight truncate">{selectedDoc.fileName}</h3>
                      <p className="text-[9px] text-zinc-500 mt-1 uppercase">
                        {selectedDoc.fileType} • {(selectedDoc.fileSize / 1024).toFixed(1)} KB
                      </p>
                    </div>
                  </div>

                  {/* Tabs: Summary / Full Content */}
                  <div className="flex border-b border-zinc-800 select-none">
                    <button 
                      onClick={() => setActiveTab('summary')}
                      className={`flex-grow pb-2.5 text-xs font-semibold border-b-2 transition-colors duration-150 ${
                        activeTab === 'summary' ? 'border-zinc-300 text-white' : 'border-transparent text-zinc-500 hover:text-zinc-300'
                      }`}
                    >
                      Summary
                    </button>
                    <button 
                      onClick={() => setActiveTab('fullText')}
                      className={`flex-grow pb-2.5 text-xs font-semibold border-b-2 transition-colors duration-150 ${
                        activeTab === 'fullText' ? 'border-zinc-300 text-white' : 'border-transparent text-zinc-500 hover:text-zinc-300'
                      }`}
                    >
                      Extracted Content
                    </button>
                  </div>

                  {/* Tab Contents */}
                  <div className="flex-grow overflow-y-auto text-xs leading-relaxed text-zinc-300 pr-1">
                    {activeTab === 'summary' ? (
                      <div className="space-y-4">
                        <h4 className="font-semibold text-white uppercase tracking-wider text-[9px]">AI-Generated Brief</h4>
                        <div className="whitespace-pre-line bg-zinc-900/50 p-4 rounded-xl border border-zinc-800">
                          {selectedDoc.summary}
                        </div>
                      </div>
                    ) : (
                      <div className="space-y-4">
                        <h4 className="font-semibold text-white uppercase tracking-wider text-[9px]">Raw Extracted Text</h4>
                        <pre className="whitespace-pre-wrap font-sans bg-zinc-900/50 p-4 rounded-xl border border-zinc-800 max-h-[38vh] overflow-y-auto">
                          {docContent || 'Loading document text from database...'}
                        </pre>
                      </div>
                    )}
                  </div>
                </div>
              </div>

              {/* RAG Q&A Console */}
              <div className="lg:col-span-7 premium-card p-5 flex flex-col space-y-4 h-full overflow-hidden">
                <h3 className="text-xs font-bold text-white uppercase tracking-wider flex items-center gap-2 border-b border-zinc-800 pb-3">
                  <HelpCircle className="w-3.5 h-3.5 text-zinc-400" />
                  Ask Question about this Document
                </h3>

                {/* Chat screen */}
                <div className="flex-grow overflow-y-auto bg-zinc-900/30 p-4 rounded-xl border border-zinc-850 space-y-4">
                  {answer ? (
                    <div className="space-y-2">
                      <div className="text-[9px] uppercase font-semibold text-zinc-400 flex items-center gap-1.5">
                        <Layers className="w-3 h-3 text-zinc-500" />
                        Nova RAG Engine Response
                      </div>
                      <p className="text-xs text-zinc-200 leading-relaxed whitespace-pre-wrap">{answer}</p>
                    </div>
                  ) : (
                    <div className="h-full flex flex-col items-center justify-center text-center text-zinc-650 p-6 select-none">
                      <HelpCircle className="w-8 h-8 mb-2 text-zinc-700" />
                      <p className="text-xs text-zinc-500 leading-relaxed">Type a query below to retrieve and analyze document contents offline using RAG embeddings.</p>
                    </div>
                  )}
                </div>

                {/* Query box */}
                <form onSubmit={handleQuery} className="flex gap-2 shrink-0">
                  <input 
                    type="text" 
                    required
                    value={question}
                    onChange={(e) => setQuestion(e.target.value)}
                    placeholder="e.g. Summarize the budget section in detail..."
                    className="clerk-input text-xs"
                  />
                  <button 
                    type="submit" 
                    disabled={querying || !question.trim()}
                    className="stripe-btn-primary py-2 px-4 text-xs font-semibold shrink-0"
                  >
                    {querying ? 'Retrieving...' : 'Run Query'}
                  </button>
                </form>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
};

export default FileAssistant;
