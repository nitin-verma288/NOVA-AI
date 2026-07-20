import React, { useState, useEffect, useRef } from 'react';
import { useChat } from '../context/ChatContext';
import { motion, AnimatePresence } from 'framer-motion';
import { 
  Send, 
  StopCircle, 
  RotateCcw, 
  Copy, 
  Check, 
  Terminal, 
  Edit, 
  Paperclip, 
  Mic, 
  CheckCheck,
  ArrowDown
} from 'lucide-react';

const escapeHtml = (text) => {
  if (!text) return '';
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
};

const highlightCode = (code, language) => {
  if (!code) return '';
  let escaped = escapeHtml(code);

  // Escape regexes to prevent highlight errors inside tags
  // Keywords
  const keywords = /\b(const|let|var|function|return|if|else|for|while|do|switch|case|break|continue|import|export|from|class|extends|new|this|typeof|instanceof|try|catch|finally|throw|async|await|public|private|protected|void|int|double|float|String|boolean|interface|struct|fn|mut|impl|pub|use|namespace|using|std|package|private)\b/g;
  escaped = escaped.replace(keywords, '<span class="code-keyword">$1</span>');

  // Strings
  escaped = escaped.replace(/(["'])(?:\\.|[^\\])*?\1/g, '<span class="code-string">$0</span>');

  // Numbers
  escaped = escaped.replace(/\b(\d+(?:\.\d+)?)\b/g, '<span class="code-number">$1</span>');

  // Comments
  escaped = escaped.replace(/(\/\/.*|\/\*[\s\S]*?\*\/|&lt;!--[\s\S]*?--&gt;)/g, '<span class="code-comment">$1</span>');

  return escaped;
};

const parseInlineStyles = (line) => {
  let escaped = escapeHtml(line);
  // Bold: **text**
  escaped = escaped.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
  // Italic: *text*
  escaped = escaped.replace(/\*(.*?)\*/g, '<em>$1</em>');
  // Inline code: `code`
  escaped = escaped.replace(/`(.*?)`/g, '<code>$1</code>');
  return <span dangerouslySetInnerHTML={{ __html: escaped }} />;
};

const renderMarkdown = (text, onCopyCode) => {
  if (!text) return null;

  // Split text by code blocks
  const parts = text.split(/(```[\s\S]*?```)/g);

  return parts.map((part, idx) => {
    if (part.startsWith('```')) {
      const match = part.match(/```(\w*)\n([\s\S]*?)```/);
      const language = match ? match[1] : 'code';
      const content = match ? match[2].trim() : part.replace(/```/g, '').trim();

      return (
        <div key={idx} className="my-4 rounded-xl border border-zinc-800 bg-[#18181b] overflow-hidden font-mono text-xs">
          <div className="flex items-center justify-between px-4 py-2.5 bg-[#121214] border-b border-zinc-800 text-[11px] text-zinc-400 select-none">
            <span className="uppercase font-semibold flex items-center gap-1.5">
              <Terminal className="w-3.5 h-3.5" />
              {language || 'code'}
            </span>
            <CopyButton text={content} />
          </div>
          <pre className="p-4 overflow-x-auto whitespace-pre leading-relaxed"><code dangerouslySetInnerHTML={{ __html: highlightCode(content, language) }} /></pre>
        </div>
      );
    }

    // Process paragraphs, headings, blockquotes, lists and tables
    const lines = part.split('\n');
    const renderedElements = [];
    let listItems = [];
    let listType = null; // 'ul' or 'ol'
    let tableRows = [];
    let inTable = false;

    const flushList = (key) => {
      if (listItems.length > 0) {
        if (listType === 'ul') {
          renderedElements.push(<ul key={key} className="list-disc pl-6 mb-3 space-y-1">{listItems}</ul>);
        } else {
          renderedElements.push(<ol key={key} className="list-decimal pl-6 mb-3 space-y-1">{listItems}</ol>);
        }
        listItems = [];
        listType = null;
      }
    };

    const flushTable = (key) => {
      if (tableRows.length > 0) {
        // Simple heuristic: if row 1 is delimiter like |---|---|
        let headerRow = tableRows[0];
        let bodyRows = tableRows.slice(1);
        let hasHeader = false;
        
        if (tableRows.length > 1 && tableRows[1].every(cell => cell.trim().startsWith('-'))) {
          bodyRows = tableRows.slice(2);
          hasHeader = true;
        }

        renderedElements.push(
          <div key={key} className="overflow-x-auto my-3">
            <table className="min-w-full border-collapse border border-zinc-850">
              {hasHeader && (
                <thead>
                  <tr className="bg-zinc-900 border-b border-zinc-800">
                    {headerRow.map((cell, cIdx) => (
                      <th key={cIdx} className="border border-zinc-800 px-3 py-1.5 text-left text-xs font-semibold text-white">
                        {parseInlineStyles(cell)}
                      </th>
                    ))}
                  </tr>
                </thead>
              )}
              <tbody>
                {bodyRows.map((row, rIdx) => (
                  <tr key={rIdx} className="border-b border-zinc-900 hover:bg-zinc-900/20">
                    {row.map((cell, cIdx) => (
                      <td key={cIdx} className="border border-zinc-800 px-3 py-1.5 text-xs text-zinc-350">
                        {parseInlineStyles(cell)}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        );
        tableRows = [];
        inTable = false;
      }
    };

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      const trimmed = line.trim();

      // Check table
      if (trimmed.startsWith('|') && trimmed.endsWith('|')) {
        flushList(`list-before-table-${idx}-${i}`);
        inTable = true;
        const cells = trimmed.split('|').map(c => c.trim()).filter((_, cIdx, arr) => cIdx > 0 && cIdx < arr.length - 1);
        tableRows.push(cells);
        continue;
      } else if (inTable) {
        flushTable(`table-${idx}-${i}`);
      }

      // Check headings
      if (trimmed.startsWith('### ')) {
        flushList(`list-before-h3-${idx}-${i}`);
        renderedElements.push(<h4 key={`h3-${idx}-${i}`} className="text-sm font-semibold text-white mt-4 mb-2">{parseInlineStyles(trimmed.substring(4))}</h4>);
        continue;
      }
      if (trimmed.startsWith('## ')) {
        flushList(`list-before-h2-${idx}-${i}`);
        renderedElements.push(<h3 key={`h2-${idx}-${i}`} className="text-base font-semibold text-white mt-5 mb-2.5">{parseInlineStyles(trimmed.substring(3))}</h3>);
        continue;
      }
      if (trimmed.startsWith('# ')) {
        flushList(`list-before-h1-${idx}-${i}`);
        renderedElements.push(<h2 key={`h1-${idx}-${i}`} className="text-lg font-bold text-white mt-6 mb-3">{parseInlineStyles(trimmed.substring(2))}</h2>);
        continue;
      }

      // Check bullet point list
      if (trimmed.startsWith('- ') || trimmed.startsWith('* ')) {
        if (listType !== 'ul') {
          flushList(`list-type-change-${idx}-${i}`);
          listType = 'ul';
        }
        listItems.push(<li key={`li-${idx}-${i}`}>{parseInlineStyles(trimmed.substring(2))}</li>);
        continue;
      }

      // Check numbered list
      const numMatch = trimmed.match(/^(\d+)\.\s(.*)/);
      if (numMatch) {
        if (listType !== 'ol') {
          flushList(`list-type-change-${idx}-${i}`);
          listType = 'ol';
        }
        listItems.push(<li key={`li-${idx}-${i}`}>{parseInlineStyles(numMatch[2])}</li>);
        continue;
      }

      // Check blockquote
      if (trimmed.startsWith('> ')) {
        flushList(`list-before-quote-${idx}-${i}`);
        renderedElements.push(
          <blockquote key={`quote-${idx}-${i}`} className="border-l-2 border-zinc-700 pl-4 py-0.5 my-2 text-zinc-400 italic">
            {parseInlineStyles(trimmed.substring(2))}
          </blockquote>
        );
        continue;
      }

      // Standard text line
      if (trimmed.length > 0) {
        flushList(`list-before-p-${idx}-${i}`);
        renderedElements.push(<p key={`p-${idx}-${i}`} className="mb-2 text-zinc-300">{parseInlineStyles(line)}</p>);
      } else {
        flushList(`list-before-space-${idx}-${i}`);
      }
    }

    flushList(`list-end-${idx}`);
    flushTable(`table-end-${idx}`);

    return (
      <div key={idx} className="markdown-body text-sm">
        {renderedElements}
      </div>
    );
  });
};

const CopyButton = ({ text }) => {
  const [copied, setCopied] = useState(false);
  const handleCopy = () => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <button 
      onClick={handleCopy}
      className="hover:text-white transition-colors duration-150 flex items-center gap-1.5"
    >
      {copied ? <Check className="w-3.5 h-3.5 text-zinc-200" /> : <Copy className="w-3.5 h-3.5" />}
      <span>{copied ? 'Copied' : 'Copy'}</span>
    </button>
  );
};

const ChatArea = () => {
  const { currentChat, sendMessage, isStreaming, streamingMessage, stopGeneration } = useChat();
  const [inputMessage, setInputMessage] = useState('');
  const [copiedId, setCopiedId] = useState('');
  const [shouldAutoScroll, setShouldAutoScroll] = useState(true);
  
  const scrollContainerRef = useRef(null);
  const lastScrollTopRef = useRef(0);
  const messagesEndRef = useRef(null);
  const textareaRef = useRef(null);

  const scrollToBottom = (behavior = 'auto') => {
    if (scrollContainerRef.current) {
      scrollContainerRef.current.scrollTo({
        top: scrollContainerRef.current.scrollHeight,
        behavior
      });
      // Immediately sync lastScrollTopRef to avoid treating this programmatic scroll as user scroll
      lastScrollTopRef.current = scrollContainerRef.current.scrollHeight - scrollContainerRef.current.clientHeight;
    }
  };

  // Scroll to bottom on chat selection change
  useEffect(() => {
    setShouldAutoScroll(true);
    const timer = setTimeout(() => {
      scrollToBottom('smooth');
      if (scrollContainerRef.current) {
        lastScrollTopRef.current = scrollContainerRef.current.scrollTop;
      }
    }, 100);
    return () => clearTimeout(timer);
  }, [currentChat?.id]);

  // Scroll to bottom on new messages or streaming tokens, if auto-scroll is active
  useEffect(() => {
    if (shouldAutoScroll) {
      scrollToBottom('auto');
    }
  }, [currentChat?.messages, streamingMessage, shouldAutoScroll]);

  const handleScroll = () => {
    const container = scrollContainerRef.current;
    if (!container) return;
    
    const currentScrollTop = container.scrollTop;
    
    // Check if the scroll position is near the bottom (with a threshold of 60px)
    const isAtBottom = container.scrollHeight - currentScrollTop - container.clientHeight < 60;
    
    // Disable auto-scroll only if the user manually scrolled up
    if (currentScrollTop < lastScrollTopRef.current && !isAtBottom) {
      setShouldAutoScroll(false);
    }
    
    // Resume auto-scroll if the user scrolled all the way back to the bottom
    if (isAtBottom) {
      setShouldAutoScroll(true);
    }
    
    lastScrollTopRef.current = currentScrollTop;
  };

  // Auto-expand textarea height
  useEffect(() => {
    const textarea = textareaRef.current;
    if (textarea) {
      textarea.style.height = 'auto';
      textarea.style.height = `${Math.min(textarea.scrollHeight, 200)}px`;
    }
  }, [inputMessage]);

  const handleSend = (e) => {
    e.preventDefault();
    if (!inputMessage.trim() || !currentChat) return;

    sendMessage(currentChat.id, inputMessage.trim());
    setInputMessage('');
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend(e);
    }
  };

  const handleCopyMessage = (text, id) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(''), 2000);
  };

  const handleEditMessage = (text) => {
    setInputMessage(text);
    textareaRef.current?.focus();
  };

  const handleRegenerate = () => {
    if (!currentChat) return;
    const messages = currentChat.messages || [];
    const userMessages = messages.filter(m => m.role === 'user');
    if (userMessages.length > 0) {
      const lastUserMsg = userMessages[userMessages.length - 1];
      sendMessage(currentChat.id, lastUserMsg.content);
    }
  };

  if (!currentChat) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center text-center p-8 bg-[#09090b]">
        <div className="w-12 h-12 border border-zinc-800 bg-zinc-900/40 flex items-center justify-center rounded-2xl mb-4">
          <Terminal className="w-6 h-6 text-zinc-300" />
        </div>
        <h3 className="text-sm font-semibold text-white">No conversation selected</h3>
        <p className="text-xs text-zinc-500 max-w-[280px] mt-1.5 leading-relaxed">
          Create a new session or choose an existing conversation thread from the sidebar.
        </p>
      </div>
    );
  }

  const messages = currentChat.messages || [];
  const userMessages = messages.filter(m => m.role === 'user');
  const hasUserMessages = userMessages.length > 0;

  return (
    <div className="flex-1 flex flex-col h-full bg-[#09090b] overflow-hidden relative">
      {/* Messages Thread Log */}
      <div 
        ref={scrollContainerRef}
        onScroll={handleScroll}
        className="flex-grow overflow-y-auto px-4 py-8"
      >
        <div className="max-w-3xl mx-auto space-y-6">
          <AnimatePresence initial={false}>
            {messages.map((m) => (
              <motion.div
                key={m.id}
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.2 }}
                className={`flex gap-4 ${m.role === 'user' ? 'justify-end' : 'justify-start'}`}
              >
                {m.role !== 'user' && (
                  <div className="w-7 h-7 bg-zinc-800 border border-zinc-700 flex items-center justify-center rounded-lg text-zinc-300 text-xs font-semibold shrink-0 select-none">
                    AI
                  </div>
                )}

                <div className={`group relative max-w-[85%] px-4 py-3 rounded-2xl ${
                  m.role === 'user'
                    ? 'bg-zinc-800 text-zinc-100'
                    : 'text-zinc-200'
                }`}>
                  {m.role === 'user' ? (
                    <div className="space-y-2">
                      <p className="text-sm leading-relaxed whitespace-pre-wrap">{m.content}</p>
                      
                      {/* User Edit Trigger */}
                      <div className="opacity-0 group-hover:opacity-100 transition-opacity absolute right-2 bottom-[-24px] flex items-center gap-1.5">
                        <button
                          onClick={() => handleEditMessage(m.content)}
                          className="p-1 rounded hover:bg-zinc-800 text-zinc-500 hover:text-zinc-300 transition"
                          title="Edit message"
                        >
                          <Edit className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div className="space-y-3">
                      {renderMarkdown(m.content, (code) => handleCopyMessage(code, m.id))}
                      
                      <div className="flex items-center gap-3 border-t border-zinc-900 pt-2.5 mt-2.5">
                        {/* Copy message button */}
                        <button
                          onClick={() => handleCopyMessage(m.content, m.id)}
                          className="p-1 rounded hover:bg-zinc-900 text-zinc-500 hover:text-zinc-300 transition-colors duration-150 flex items-center gap-1 text-[10px]"
                        >
                          {copiedId === m.id ? (
                            <>
                              <CheckCheck className="w-3.5 h-3.5 text-zinc-300" />
                              <span>Copied</span>
                            </>
                          ) : (
                            <>
                              <Copy className="w-3.5 h-3.5" />
                              <span>Copy message</span>
                            </>
                          )}
                        </button>

                        {/* Regenerate Response button (only show on last assistant message) */}
                        {!isStreaming && messages[messages.length - 1].id === m.id && (
                          <button
                            onClick={handleRegenerate}
                            className="p-1 rounded hover:bg-zinc-900 text-zinc-500 hover:text-zinc-300 transition-colors duration-150 flex items-center gap-1 text-[10px]"
                          >
                            <RotateCcw className="w-3.5 h-3.5" />
                            <span>Regenerate</span>
                          </button>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              </motion.div>
            ))}

            {/* Active Streaming Chunk Display */}
            {isStreaming && streamingMessage && (
              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                className="flex gap-4 justify-start"
              >
                <div className="w-7 h-7 bg-zinc-800 border border-zinc-700 flex items-center justify-center rounded-lg text-zinc-300 text-xs font-semibold shrink-0 select-none">
                  AI
                </div>

                <div className="max-w-[85%] px-4 py-3 rounded-2xl text-zinc-200">
                  <div className="space-y-3">
                    {renderMarkdown(streamingMessage, (code) => handleCopyMessage(code, 'stream'))}
                    {/* Pulsing cursor character block */}
                    <span className="inline-block w-1.5 h-3 bg-zinc-400 ml-1 animate-pulse"></span>
                  </div>
                </div>
              </motion.div>
            )}

            {/* Thinking Indicator */}
            {isStreaming && !streamingMessage && (
              <motion.div 
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="flex gap-4 justify-start"
              >
                <div className="w-7 h-7 bg-zinc-800 border border-zinc-700 flex items-center justify-center rounded-lg text-zinc-300 text-xs font-semibold shrink-0 select-none">
                  AI
                </div>
                <div className="px-4 py-3 text-xs text-zinc-500 flex items-center gap-2">
                  <div className="flex gap-1">
                    <span className="w-1.5 h-1.5 rounded-full bg-zinc-500 animate-bounce" style={{ animationDelay: '0ms' }}></span>
                    <span className="w-1.5 h-1.5 rounded-full bg-zinc-500 animate-bounce" style={{ animationDelay: '150ms' }}></span>
                    <span className="w-1.5 h-1.5 rounded-full bg-zinc-500 animate-bounce" style={{ animationDelay: '300ms' }}></span>
                  </div>
                  <span>Nova is thinking...</span>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
          <div ref={messagesEndRef} />
        </div>
      </div>

      <AnimatePresence>
        {!shouldAutoScroll && (
          <motion.button
            initial={{ opacity: 0, scale: 0.9, y: 10 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.9, y: 10 }}
            type="button"
            onClick={() => {
              setShouldAutoScroll(true);
              scrollToBottom('smooth');
            }}
            className="absolute bottom-28 right-8 z-20 px-3.5 py-2 rounded-full bg-zinc-900 border border-zinc-850 text-zinc-300 hover:text-white shadow-xl transition-all hover:bg-zinc-800 flex items-center gap-1.5 text-xs font-medium"
          >
            <ArrowDown className="w-4 h-4 text-zinc-400" />
            <span>Scroll to bottom</span>
          </motion.button>
        )}
      </AnimatePresence>

      {/* Input panel area */}
      <div className="p-4 bg-[#09090b] border-t border-zinc-800 shrink-0 select-none">
        <div className="max-w-3xl mx-auto">
          <form onSubmit={handleSend} className="relative bg-[#18181b] border border-zinc-800 rounded-2xl p-2 flex flex-col focus-within:border-zinc-700 transition">
            <textarea
              ref={textareaRef}
              rows="1"
              value={inputMessage}
              onChange={(e) => setInputMessage(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={isStreaming}
              placeholder="Talk to Nova..."
              className="w-full bg-transparent text-zinc-100 text-sm px-3 py-2 focus:outline-none resize-none placeholder-zinc-500 min-h-[40px] max-h-[200px]"
            />

            <div className="flex items-center justify-between mt-2 pt-2 border-t border-zinc-900/50 px-2">
              {/* Attachment and Microphone items (placeholders as requested) */}
              <div className="flex items-center gap-1 text-zinc-500">
                <button
                  type="button"
                  className="p-2 rounded-lg hover:bg-zinc-800 hover:text-zinc-300 transition"
                  title="Attach file"
                >
                  <Paperclip className="w-4 h-4" />
                </button>
                <button
                  type="button"
                  className="p-2 rounded-lg hover:bg-zinc-800 hover:text-zinc-300 transition"
                  title="Voice input"
                >
                  <Mic className="w-4 h-4" />
                </button>
              </div>

              {/* Action trigger: Stop or Send */}
              <div className="flex items-center gap-2">
                {isStreaming ? (
                  <button
                    type="button"
                    onClick={stopGeneration}
                    className="stripe-btn-danger flex items-center gap-1.5 py-1.5 px-3 text-xs"
                  >
                    <StopCircle className="w-3.5 h-3.5" />
                    Stop
                  </button>
                ) : (
                  <button
                    type="submit"
                    disabled={!inputMessage.trim() || isStreaming}
                    className="stripe-btn-primary flex items-center gap-1.5 py-1.5 px-3 text-xs"
                  >
                    <Send className="w-3.5 h-3.5 text-zinc-950" />
                    Send
                  </button>
                )}
              </div>
            </div>
          </form>
          
          <div className="flex items-center justify-between mt-2 text-[10px] text-zinc-500 px-1 font-mono">
            <span>Press Enter to send, Shift+Enter for newline</span>
            <span>Local Offline AI Session</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ChatArea;
