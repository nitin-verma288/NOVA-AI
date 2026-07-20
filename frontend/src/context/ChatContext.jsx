import React, { createContext, useContext, useState, useRef, useEffect } from 'react';
import api, { API_BASE_URL } from '../services/api';

const ChatContext = createContext(null);

export const ChatProvider = ({ children }) => {
  const [chats, setChats] = useState([]);
  const [currentChat, setCurrentChat] = useState(null);
  const [loading, setLoading] = useState(false);
  const [isStreaming, setIsStreaming] = useState(false);
  const [streamingMessage, setStreamingMessage] = useState(''); // Keep as placeholder for typing indicator/fallback

  const eventSourceRef = useRef(null);

  // Load chat history on mount
  const fetchChats = async (selectId = null) => {
    setLoading(true);
    try {
      const response = await api.get('/chat/history');
      const fetchedChats = response.data || [];
      
      // Sort pinned chats first, then by updatedAt
      const sorted = [...fetchedChats].sort((a, b) => {
        if (a.isPinned !== b.isPinned) {
          return b.isPinned ? 1 : -1;
        }
        return new Date(b.updatedAt) - new Date(a.updatedAt);
      });
      
      setChats(sorted);

      if (selectId) {
        const selected = sorted.find(c => c.id === selectId);
        if (selected) {
          setCurrentChat(selected);
        }
      } else {
        setCurrentChat(prev => {
          if (!prev) return null;
          const updated = sorted.find(c => c.id === prev.id);
          return updated || prev;
        });
      }
    } catch (error) {
      console.error('Failed to fetch chat history', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchChats();
  }, []);

  const selectChat = (chatId) => {
    const selected = chats.find(c => c.id === chatId);
    if (selected) {
      setCurrentChat(selected);
      setStreamingMessage('');
    }
  };

  const createChat = async (title = 'New Conversation') => {
    setLoading(true);
    try {
      const response = await api.post('/chat/create', { title });
      const newChat = response.data;
      
      setChats(prev => [newChat, ...prev]);
      setCurrentChat(newChat);
      return newChat;
    } catch (error) {
      console.error('Failed to create chat', error);
    } finally {
      setLoading(false);
    }
  };

  const deleteChat = async (chatId) => {
    try {
      await api.delete(`/chat/delete/${chatId}`);
      setChats(prev => prev.filter(c => c.id !== chatId));
      setCurrentChat(prev => {
        if (prev && prev.id === chatId) {
          return null;
        }
        return prev;
      });
    } catch (error) {
      console.error('Failed to delete chat', error);
    }
  };

  const togglePinChat = async (chatId) => {
    try {
      const response = await api.put(`/chat/pin/${chatId}`);
      const updatedChat = response.data;

      setChats(prev => {
        const mapped = prev.map(c => c.id === chatId ? { ...c, isPinned: updatedChat.isPinned } : c);
        return mapped.sort((a, b) => {
          if (a.isPinned !== b.isPinned) {
            return b.isPinned ? 1 : -1;
          }
          return new Date(b.updatedAt) - new Date(a.updatedAt);
        });
      });

      setCurrentChat(prev => {
        if (prev && prev.id === chatId) {
          return { ...prev, isPinned: updatedChat.isPinned };
        }
        return prev;
      });
    } catch (error) {
      console.error('Failed to pin chat', error);
    }
  };

  const syncChatWithBackend = async (chatId, expectedContent) => {
    console.log('[DEBUG] ChatContext: syncChatWithBackend called', { chatId, expectedContentLength: expectedContent.length });
    const fetchAndSwap = async () => {
      try {
        console.log('[DEBUG] ChatContext: Sending API request to /chat/history');
        const response = await api.get('/chat/history');
        console.log('[DEBUG] ChatContext: Received API response from /chat/history', { status: response.status, dataLength: response.data?.length });
        const serverChats = response.data || [];
        const updatedChat = serverChats.find(c => c.id === chatId);
        console.log('[DEBUG] ChatContext: Found matching chat in history', { 
          chatId, 
          title: updatedChat?.title, 
          messageCount: updatedChat?.messages?.length,
          messages: updatedChat?.messages?.map(m => ({ id: m.id, role: m.role, contentLength: m.content?.length }))
        });
        const hasMsg = updatedChat?.messages?.some(
          m => m.role === 'assistant' && m.content.trim() === expectedContent.trim()
        );
        console.log('[DEBUG] ChatContext: Check if expected assistant message is present in response', { hasMsg });
        return { serverChats, updatedChat, hasMsg };
      } catch (err) {
        console.error('[DEBUG] ChatContext: Failed to sync history from backend', err);
        return null;
      }
    };

    // Try immediately
    let res = await fetchAndSwap();

    // If not found, wait 500ms and retry once to account for DB write latency
    if (!res || !res.hasMsg) {
      console.log('[DEBUG] ChatContext: Expected message not found in first fetch, waiting 500ms for DB write latency...');
      await new Promise(resolve => setTimeout(resolve, 500));
      res = await fetchAndSwap();
    }

    if (res && res.serverChats) {
      const sorted = [...res.serverChats].sort((a, b) => {
        if (a.isPinned !== b.isPinned) {
          return b.isPinned ? 1 : -1;
        }
        return new Date(b.updatedAt) - new Date(a.updatedAt);
      });
      
      console.log('[DEBUG] ChatContext: Updating chats state');
      setChats(prev => {
        console.log('[DEBUG] ChatContext: React state before update (chats)', { count: prev.length });
        const next = sorted;
        console.log('[DEBUG] ChatContext: React state after update (chats)', { count: next.length });
        return next;
      });

      if (res.updatedChat) {
        setCurrentChat(prev => {
          console.log('[DEBUG] ChatContext: React state before update (currentChat)', { 
            id: prev?.id, 
            messageCount: prev?.messages?.length,
            messages: prev?.messages?.map(m => ({ id: m.id, role: m.role, isTemp: m.isTemp, content: m.content }))
          });
          if (prev && prev.id === chatId) {
            const next = res.updatedChat;
            console.log('[DEBUG] ChatContext: React state after update (currentChat)', { 
              id: next?.id, 
              messageCount: next?.messages?.length,
              messages: next?.messages?.map(m => ({ id: m.id, role: m.role, content: m.content }))
            });
            return next;
          }
          return prev;
        });
      }
    }
  };

  const sendMessage = (chatId, messageText) => {
    if (isStreaming) return;

    console.log('[DEBUG] ChatContext: sendMessage entered', { chatId, messageText });
    setIsStreaming(true);
    setStreamingMessage('');

    const tempUserMsgId = `temp-user-${Date.now()}`;
    const userMsg = {
      id: tempUserMsgId,
      role: 'user',
      content: messageText,
      createdAt: new Date().toISOString()
    };

    const tempAssistantMsgId = `temp-assistant-${Date.now()}`;
    const assistantMsg = {
      id: tempAssistantMsgId,
      role: 'assistant',
      content: '',
      isTemp: true,
      createdAt: new Date().toISOString()
    };

    // Inject temporary user and assistant messages in-place immediately
    setCurrentChat(prev => {
      console.log('[DEBUG] ChatContext: React state before update (currentChat - inject temp)', {
        id: prev?.id,
        messages: prev?.messages?.map(m => ({ id: m.id, role: m.role, content: m.content }))
      });
      if (!prev || prev.id !== chatId) return prev;
      const next = {
        ...prev,
        messages: [...(prev.messages || []), userMsg, assistantMsg]
      };
      console.log('[DEBUG] ChatContext: React state after update (currentChat - inject temp)', {
        id: next?.id,
        messages: next?.messages?.map(m => ({ id: m.id, role: m.role, content: m.content }))
      });
      return next;
    });

    setChats(prevChats => {
      console.log('[DEBUG] ChatContext: React state before update (chats - inject temp)');
      const next = prevChats.map(c => {
        if (c.id === chatId) {
          return {
            ...c,
            messages: [...(c.messages || []), userMsg, assistantMsg],
            updatedAt: new Date().toISOString()
          };
        }
        return c;
      });
      console.log('[DEBUG] ChatContext: React state after update (chats - inject temp)');
      return next;
    });

    const token = localStorage.getItem('token');
    const streamUrl = `${API_BASE_URL}/chat/stream?chatId=${chatId}&message=${encodeURIComponent(messageText)}&token=${token}`;
    console.log('[DEBUG] ChatContext: Opening EventSource', { streamUrl });

    const eventSource = new EventSource(streamUrl);
    eventSourceRef.current = eventSource;

    let accumulatedResponse = '';

    eventSource.onmessage = (event) => {
      console.log('[DEBUG] ChatContext: eventSource.onmessage received chunk', { data: event.data });
      if (event.data === '[DONE]') {
        console.log('[DEBUG] ChatContext: eventSource.onmessage [DONE] received. Closing stream.');
        eventSource.close();
        setIsStreaming(false);
        setStreamingMessage('');
        // Sync with backend to fetch the official messages with database IDs
        syncChatWithBackend(chatId, accumulatedResponse);
      } else {
        let chunkContent = '';
        try {
          const parsed = JSON.parse(event.data);
          if (parsed && typeof parsed.content === 'string') {
            chunkContent = parsed.content;
          } else {
            chunkContent = event.data;
          }
        } catch (e) {
          chunkContent = event.data;
        }

        accumulatedResponse += chunkContent;
        setStreamingMessage(accumulatedResponse);

        // Update the temporary assistant message content in real time
        setCurrentChat(prev => {
          if (!prev || prev.id !== chatId) return prev;
          const updatedMessages = prev.messages.map(m => {
            if (m.id === tempAssistantMsgId) {
              return { ...m, content: accumulatedResponse };
            }
            return m;
          });
          return { ...prev, messages: updatedMessages };
        });

        setChats(prevChats => {
          return prevChats.map(c => {
            if (c.id === chatId) {
              const updatedMessages = c.messages.map(m => {
                if (m.id === tempAssistantMsgId) {
                  return { ...m, content: accumulatedResponse };
                }
                return m;
              });
              return { ...c, messages: updatedMessages };
            }
            return c;
          });
        });
      }
    };

    eventSource.onerror = (error) => {
      console.error('[DEBUG] ChatContext: eventSource.onerror received error', error);
      eventSource.close();
      setIsStreaming(false);
      setStreamingMessage('');
      // Sync on error to fetch what was completed
      syncChatWithBackend(chatId, accumulatedResponse);
    };
  };

  const stopGeneration = () => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      setIsStreaming(false);
      setStreamingMessage('');
      
      // Let backend persist what was streamed so far
      // Wait 500ms and sync history to grab official records
      setTimeout(() => {
        if (currentChat) {
          fetchChats(currentChat.id);
        }
      }, 500);
    }
  };

  return (
    <ChatContext.Provider value={{
      chats,
      currentChat,
      loading,
      isStreaming,
      streamingMessage,
      fetchChats,
      selectChat,
      createChat,
      deleteChat,
      togglePinChat,
      sendMessage,
      stopGeneration
    }}>
      {children}
    </ChatContext.Provider>
  );
};

export const useChat = () => useContext(ChatContext);
