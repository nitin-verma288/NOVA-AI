import React, { useState, useRef, useEffect } from 'react';
import { useChat } from '../context/ChatContext';
import { useAuth } from '../context/AuthContext';
import Markdown from './Markdown';
import { 
  Send, 
  StopCircle, 
  RefreshCw, 
  Copy, 
  Check, 
  ArrowDown, 
  Bot, 
  User, 
  Sparkles, 
  Plus, 
  MessageSquare,
  Terminal
} from 'lucide-react';

const SUGGESTED_PROMPTS = [
  {
    title: 'Generate Unit Tests',
    desc: 'Write comprehensive JUnit/Mockito code tests',
    text: 'Write comprehensive unit tests for a standard Java Spring Boot Controller endpoint, covering success and failure validation cases.'
  },
  {
    title: 'Analyze Big O Complexity',
    desc: 'Optimize space and time bottlenecks',
    text: 'Analyze the computational complexity of nested loop operations and explain how to optimize them to O(N log N).'
  },
  {
    title: 'Design API Schema',
    desc: 'Draft stable JSON schema designs',
    text: 'Create a clean, REST-compliant JSON payload schema design for an e-commerce cart management checkout pipeline.'
  },
  {
    title: 'Explain System Architecture',
    desc: 'Deconstruct microservice pipelines',
    text: 'Explain the difference between event-driven architecture using Kafka vs. traditional synchronous HTTP REST calls.'
  }
];

const ChatArea = ({ sidebarCollapsed }) => {
  const { 
    currentChat, 
    isStreaming, 
    sendMessage, 
    stopGeneration,
    createChat,
    chats
  } = useChat();
  const { user } = useAuth();
  
  const [inputText, setInputText] = useState('');
  const [copiedMsgId, setCopiedMsgId] = useState(null);
  const [autoScroll, setAutoScroll] = useState(true);

  const scrollRef = useRef(null);
  const textareaRef = useRef(null);

  // Auto-scroll logic
  const scrollToBottom = () => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  };

  const handleScroll = () => {
    if (!scrollRef.current) return;
    const { scrollTop, clientHeight, scrollHeight } = scrollRef.current;
    
    // Check if the user is scrolled up
    const isAtBottom = scrollHeight - scrollTop - clientHeight < 60;
    setAutoScroll(isAtBottom);
  };

  useEffect(() => {
    if (autoScroll) {
      scrollToBottom();
    }
  }, [currentChat?.messages, autoScroll]);

  // Handle auto-resizing of the input textarea
  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 200)}px`;
    }
  }, [inputText]);

  const handleSend = async (textToSend) => {
    if (!textToSend.trim()) return;

    let targetChatId = currentChat?.id;

    // If no chat session is active, create a new one first
    if (!targetChatId) {
      const newChat = await createChat(textToSend.substring(0, 30) + '...');
      if (newChat) {
        targetChatId = newChat.id;
      } else {
        console.error('Failed to create chat on message send');
        return;
      }
    }

    setInputText('');
    setAutoScroll(true);
    sendMessage(targetChatId, textToSend);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (isStreaming) return;
    handleSend(inputText);
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e);
    }
  };

  const handleCopyMessage = (msgId, content) => {
    navigator.clipboard.writeText(content);
    setCopiedMsgId(msgId);
    setTimeout(() => setCopiedMsgId(null), 2000);
  };

  const handleRegenerate = () => {
    if (!currentChat || isStreaming) return;
    const messages = currentChat.messages || [];
    const userMessages = messages.filter(m => m.role === 'user');
    if (userMessages.length > 0) {
      const lastUserMsg = userMessages[userMessages.length - 1];
      setAutoScroll(true);
      sendMessage(currentChat.id, lastUserMsg.content);
    }
  };

  const messages = currentChat?.messages || [];
  console.log('[DEBUG] ChatArea: rendering messages', { 
    currentChatId: currentChat?.id, 
    messagesCount: messages.length, 
    messages: messages.map(m => ({ id: m.id, role: m.role, isTemp: m.isTemp, content: m.content })) 
  });

  return (
    <div className="flex flex-col h-full bg-[#09090b] relative overflow-hidden select-text">
      {/* Scrollable messages container */}
      <div 
        ref={scrollRef}
        onScroll={handleScroll}
        className="flex-1 overflow-y-auto px-4 md:px-6 py-6 space-y-6 scrollbar-thin scroll-smooth"
      >
        {messages.length === 0 ? (
          /* Empty state */
          <div className="max-w-2xl mx-auto pt-10 md:pt-16 flex flex-col items-center justify-center text-center space-y-8 select-none">
            <div className="flex flex-col items-center space-y-3">
              <div className="w-14 h-14 rounded-2xl bg-zinc-900 border border-zinc-800 flex items-center justify-center shadow-lg relative">
                <Sparkles className="w-6 h-6 text-zinc-300 animate-pulse" />
                <div className="absolute -inset-0.5 bg-gradient-to-r from-zinc-500 to-zinc-700 rounded-2xl blur opacity-20 -z-10"></div>
              </div>
              <h2 className="text-xl font-bold tracking-tight text-white mt-4">NOVA Intelligence Engine</h2>
              <p className="text-xs text-zinc-400 max-w-sm leading-relaxed">
                Connect and query local LLM nodes instantly. Enter queries below or select quick action prompts.
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5 w-full">
              {SUGGESTED_PROMPTS.map((prompt) => (
                <button
                  key={prompt.title}
                  onClick={() => handleSend(prompt.text)}
                  className="p-4 rounded-xl border border-zinc-850 bg-zinc-900/20 text-left hover:border-zinc-750 hover:bg-zinc-900/50 transition duration-150 flex flex-col justify-between space-y-1"
                >
                  <span className="text-xs font-semibold text-zinc-200">{prompt.title}</span>
                  <span className="text-[10px] text-zinc-500 leading-normal">{prompt.desc}</span>
                </button>
              ))}
            </div>
          </div>
        ) : (
          /* Conversation stream */
          <div className="max-w-3xl mx-auto space-y-6">
            {messages.map((msg, index) => {
              const isUser = msg.role === 'user';
              const isLastMsg = index === messages.length - 1;
              
              return (
                <div 
                  key={msg.id || index}
                  className={`flex gap-3.5 ${isUser ? 'justify-end' : 'justify-start'}`}
                >
                  {/* Left avatar icon */}
                  {!isUser && (
                    <div className="w-8 h-8 rounded-lg border border-zinc-800 bg-zinc-900/60 flex items-center justify-center shrink-0 shadow-sm mt-0.5">
                      <Bot className="w-4 h-4 text-zinc-400" />
                    </div>
                  )}

                  {/* Message body wrapper */}
                  <div className="flex flex-col space-y-1.5 max-w-[85%]">
                    {/* Username or identity label */}
                    <span className="text-[10px] font-bold text-zinc-500 uppercase tracking-wider select-none px-1">
                      {isUser ? (user?.username || 'You') : 'NOVA AI'}
                    </span>

                    {/* Message box */}
                    <div 
                      className={`rounded-2xl px-4 py-3 shadow-sm border ${
                        isUser 
                          ? 'bg-zinc-800/50 border-zinc-750 text-zinc-200' 
                          : 'bg-zinc-900/30 border-zinc-850 text-zinc-300'
                      }`}
                    >
                      {isUser ? (
                        <p className="text-sm whitespace-pre-wrap leading-relaxed">{msg.content}</p>
                      ) : (
                        <Markdown text={msg.content} />
                      )}
                    </div>

                    {/* Assistant control bar */}
                    {!isUser && (
                      <div className="flex items-center gap-2.5 px-1 select-none">
                        <button
                          onClick={() => handleCopyMessage(msg.id, msg.content)}
                          className="flex items-center gap-1.5 text-[10px] text-zinc-500 hover:text-zinc-300 transition-colors"
                          title="Copy full response"
                        >
                          {copiedMsgId === msg.id ? (
                            <>
                              <Check className="w-3 h-3 text-emerald-500" />
                              <span className="text-emerald-500 font-semibold">Copied</span>
                            </>
                          ) : (
                            <>
                              <Copy className="w-3 h-3" />
                              <span>Copy</span>
                            </>
                          )}
                        </button>

                        {/* Regenerate visible on last assistant message when not streaming */}
                        {isLastMsg && !isStreaming && (
                          <button
                            onClick={handleRegenerate}
                            className="flex items-center gap-1.5 text-[10px] text-zinc-500 hover:text-zinc-300 transition-colors"
                            title="Regenerate this response"
                          >
                            <RefreshCw className="w-3 h-3" />
                            <span>Regenerate</span>
                          </button>
                        )}
                      </div>
                    )}
                  </div>

                  {/* Right avatar icon */}
                  {isUser && (
                    <div className="w-8 h-8 rounded-lg border border-zinc-850 bg-zinc-900/40 flex items-center justify-center shrink-0 mt-0.5">
                      <User className="w-4 h-4 text-zinc-500" />
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Floating scroll to bottom anchor */}
      {!autoScroll && (
        <button
          onClick={() => {
            setAutoScroll(true);
            scrollToBottom();
          }}
          className="absolute right-6 bottom-28 md:right-10 p-2 rounded-full border border-zinc-800 bg-zinc-900/90 text-zinc-400 hover:text-white shadow-lg hover:scale-105 transition-all duration-150 z-10"
          title="Scroll to bottom"
        >
          <ArrowDown className="w-4 h-4" />
        </button>
      )}

      {/* Bottom control panel / input box */}
      <div className="border-t border-zinc-850 bg-[#09090b]/80 backdrop-blur-md px-4 py-4 shrink-0 z-10">
        <div className="max-w-3xl mx-auto relative">
          
          {/* Floating Action: Stop generating */}
          {isStreaming && (
            <div className="flex justify-center mb-2.5">
              <button
                type="button"
                onClick={stopGeneration}
                className="py-1.5 px-3.5 rounded-lg border border-red-950 bg-red-950/20 text-red-400 text-xs font-semibold hover:bg-red-950/40 hover:border-red-900 transition-all flex items-center gap-2 shadow-sm select-none"
              >
                <StopCircle className="w-4 h-4 animate-pulse" />
                Halt Response Stream
              </button>
            </div>
          )}

          <form onSubmit={handleSubmit} className="relative flex items-center">
            <textarea
              ref={textareaRef}
              rows={1}
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Ask anything (Press Enter to send, Shift+Enter for new line)..."
              className="w-full pl-4 pr-12 py-3 bg-[#141416] border border-zinc-850 text-zinc-200 text-xs rounded-xl placeholder-zinc-500 focus:outline-none focus:border-zinc-700 resize-none max-h-48 overflow-y-auto leading-relaxed scrollbar-none"
            />
            
            <div className="absolute right-2.5 top-1/2 -translate-y-1/2 flex items-center">
              {isStreaming ? (
                <button
                  type="button"
                  onClick={stopGeneration}
                  className="p-1.5 rounded-lg bg-red-900/20 text-red-400 border border-red-900/30 hover:bg-red-900/40 transition-colors"
                  title="Stop generating"
                >
                  <StopCircle className="w-4 h-4" />
                </button>
              ) : (
                <button
                  type="submit"
                  disabled={!inputText.trim()}
                  className="p-1.5 rounded-lg bg-zinc-800 text-zinc-300 hover:text-white hover:bg-zinc-700 disabled:opacity-30 disabled:hover:bg-zinc-800 disabled:hover:text-zinc-500 transition-colors"
                  title="Send message"
                >
                  <Send className="w-4 h-4" />
                </button>
              )}
            </div>
          </form>
          
          <div className="mt-2 text-center select-none">
            <span className="text-[9px] text-zinc-600 font-mono tracking-wider">
              RUNNING LOCAL OLLAMA SUITE // POWERED BY GEMMA3 & QWEN3
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ChatArea;
