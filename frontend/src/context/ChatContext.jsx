import React, { createContext, useContext, useState, useRef, useEffect } from 'react';
import api, { API_BASE_URL } from '../services/api';

const ChatContext = createContext(null);

export const ChatProvider = ({ children }) => {
  const [chats, setChats] = useState([]);
  const [currentChat, setCurrentChat] = useState(null);
  const [loading, setLoading] = useState(false);
  const [isStreaming, setIsStreaming] = useState(false);
  const [streamingMessage, setStreamingMessage] = useState('');
  
  const eventSourceRef = useRef(null);

  const mergeMessages = (localMessages = [], serverMessages = []) => {
    if (!localMessages || localMessages.length === 0) return serverMessages || [];
    if (!serverMessages || serverMessages.length === 0) return localMessages;

    const merged = [];
    const serverMessageMap = new Map();
    const serverRoleContentMap = new Map();

    serverMessages.forEach(msg => {
      if (msg.id) {
        serverMessageMap.set(String(msg.id), msg);
      }
      serverRoleContentMap.set(`${msg.role}:${msg.content}`, msg);
    });

    const usedServerIds = new Set();

    localMessages.forEach(localMsg => {
      let matchedServerMsg = null;
      if (localMsg.id && serverMessageMap.has(String(localMsg.id))) {
        matchedServerMsg = serverMessageMap.get(String(localMsg.id));
      } else {
        const key = `${localMsg.role}:${localMsg.content}`;
        if (serverRoleContentMap.has(key)) {
          matchedServerMsg = serverRoleContentMap.get(key);
        }
      }

      if (matchedServerMsg) {
        merged.push(matchedServerMsg);
        usedServerIds.add(String(matchedServerMsg.id));
      } else {
        merged.push(localMsg);
      }
    });

    serverMessages.forEach(serverMsg => {
      if (!usedServerIds.has(String(serverMsg.id))) {
        merged.push(serverMsg);
      }
    });

    return merged.sort((a, b) => {
      const timeA = new Date(a.createdAt).getTime();
      const timeB = new Date(b.createdAt).getTime();
      if (timeA !== timeB) return timeA - timeB;
      return String(a.id).localeCompare(String(b.id));
    });
  };

  const fetchChats = async () => {
    setLoading(true);
    try {
      const response = await api.get('/chat/history');
      
      setChats(prevChats => {
        return response.data.map(serverChat => {
          const prevChat = prevChats.find(c => c.id === serverChat.id);
          if (prevChat) {
            const merged = mergeMessages(prevChat.messages, serverChat.messages);
            return {
              ...serverChat,
              messages: merged
            };
          }
          return serverChat;
        });
      });

      setCurrentChat(prev => {
        if (!prev) return null;
        const updated = response.data.find(c => c.id === prev.id);
        if (updated) {
          const merged = mergeMessages(prev.messages, updated.messages);
          return {
            ...updated,
            messages: merged
          };
        }
        return prev;
      });
    } catch (error) {
      console.error('Failed to fetch chat history', error);
    } finally {
      setLoading(false);
    }
  };

  const syncChatWithBackend = async (chatId, expectedAssistantContent) => {
    const maxAttempts = 20;
    const interval = 1000; // poll every 1 second
    
    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        const response = await api.get('/chat/history');
        const updatedChat = response.data.find(c => c.id === chatId);
        
        const isSaved = updatedChat && updatedChat.messages && updatedChat.messages.some(
          m => m.role === 'assistant' && m.content === expectedAssistantContent
        );
        
        if (isSaved) {
          setChats(prevChats => {
            return response.data.map(serverChat => {
              const prevChat = prevChats.find(c => c.id === serverChat.id);
              if (prevChat) {
                return {
                  ...serverChat,
                  messages: mergeMessages(prevChat.messages, serverChat.messages)
                };
              }
              return serverChat;
            });
          });
          
          setCurrentChat(prev => {
            if (!prev) return null;
            if (prev.id === chatId) {
              return {
                ...updatedChat,
                messages: mergeMessages(prev.messages, updatedChat.messages)
              };
            }
            const updated = response.data.find(c => c.id === prev.id);
            if (updated) {
              return {
                ...updated,
                messages: mergeMessages(prev.messages, updated.messages)
              };
            }
            return prev;
          });
          console.log(`Backend confirmed message saved on attempt ${attempt}`);
          return;
        }
      } catch (error) {
        console.error('Error syncing with backend', error);
      }
      await new Promise(resolve => setTimeout(resolve, interval));
    }
    
    console.warn('Timed out waiting for backend to save assistant message. Fetching chats anyway.');
    fetchChats();
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
      setChats(prev => [response.data, ...prev]);
      setCurrentChat(response.data);
      return response.data;
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
      if (currentChat && currentChat.id === chatId) {
        setCurrentChat(null);
      }
    } catch (error) {
      console.error('Failed to delete chat', error);
    }
  };

  const togglePinChat = async (chatId) => {
    try {
      const response = await api.put(`/chat/pin/${chatId}`);
      setChats(prev => prev.map(c => {
        if (c.id === chatId) {
          const merged = mergeMessages(c.messages, response.data.messages);
          return {
            ...response.data,
            messages: merged
          };
        }
        return c;
      }).sort((a, b) => b.isPinned - a.isPinned || new Date(b.updatedAt) - new Date(a.updatedAt)));
      
      if (currentChat && currentChat.id === chatId) {
        setCurrentChat(prev => {
          if (!prev) return null;
          return { ...prev, isPinned: response.data.isPinned };
        });
      }
    } catch (error) {
      console.error('Failed to pin chat', error);
    }
  };

  const sendMessage = (chatId, message) => {
    if (isStreaming) return;

    setIsStreaming(true);
    setStreamingMessage('');

    // Pre-inject user message locally to avoid UI latency
    const localUserMsg = {
      id: Date.now().toString(),
      role: 'user',
      content: message,
      createdAt: new Date().toISOString()
    };

    setCurrentChat(prev => {
      if (!prev) return null;
      return {
        ...prev,
        messages: [...(prev.messages || []), localUserMsg]
      };
    });

    const token = localStorage.getItem('token');
    const streamUrl = `${API_BASE_URL}/chat/stream?chatId=${chatId}&message=${encodeURIComponent(message)}&token=${token}`;

    const eventSource = new EventSource(streamUrl);
    eventSourceRef.current = eventSource;

    let accumulatedResponse = '';

    eventSource.onmessage = (event) => {
      if (event.data === '[DONE]') {
        eventSource.close();

        // Create completed assistant message
        const localAssistantMsg = {
          id: (Date.now() + 1).toString(),
          role: 'assistant',
          content: accumulatedResponse,
          createdAt: new Date().toISOString()
        };

        setCurrentChat(prev => {
          if (!prev) return null;
          const messages = prev.messages || [];
          const exists = messages.some(m => m.content === localAssistantMsg.content && m.role === 'assistant');
          if (exists) {
            return prev;
          }
          return {
            ...prev,
            messages: [...messages, localAssistantMsg]
          };
        });

        setIsStreaming(false);
        setStreamingMessage('');
        
        // Wait until backend confirms that the assistant message has been saved
        syncChatWithBackend(chatId, accumulatedResponse);
      } else {
        try {
          const parsed = JSON.parse(event.data);
          if (parsed && typeof parsed.content === 'string') {
            accumulatedResponse += parsed.content;
            setStreamingMessage(accumulatedResponse);
          } else {
            accumulatedResponse += event.data;
            setStreamingMessage(accumulatedResponse);
          }
        } catch (e) {
          accumulatedResponse += event.data;
          setStreamingMessage(accumulatedResponse);
        }
      }
    };

    eventSource.onerror = (error) => {
      console.error('SSE Stream error', error);
      eventSource.close();
      setIsStreaming(false);
    };
  };

  const stopGeneration = () => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      setIsStreaming(false);
      
      // If stopped, we append the partial response generated so far to the conversation history
      const localAssistantStoppedMsg = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: streamingMessage + ' *[Generation stopped by user]*',
        createdAt: new Date().toISOString()
      };

      setCurrentChat(prev => {
        if (!prev) return null;
        return {
          ...prev,
          messages: [...(prev.messages || []), localAssistantStoppedMsg]
        };
      });

      setStreamingMessage('');
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
