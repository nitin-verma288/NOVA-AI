import React, { useState, useRef } from 'react';
import api, { API_BASE_URL } from '../services/api';
import { useChat } from '../context/ChatContext';
import { Code, Play, Copy, Check, FileCode, Cpu, ShieldAlert, StopCircle } from 'lucide-react';

const ACTIONS = [
  {
    title: 'Generate Unit Tests',
    desc: 'Generate JUnit 5, Mockito, or general code tests.',
    prompt: 'Write comprehensive unit tests for the following block of code, covering success cases, failure cases, boundary limits, and edge cases. Include Mockito setups if appropriate:\n\n```[language]\n[paste code here]\n```',
    icon: FileCode,
    color: 'border-zinc-800 bg-zinc-900/40 text-zinc-300 hover:border-zinc-700'
  },
  {
    title: 'Optimize Performance',
    desc: 'Analyze computational complexity and refactor code.',
    prompt: 'Analyze the following code snippet. Identify any memory leaks, performance bottlenecks, or high space/time complexities (Big O). Provide an optimized, refactored version of the code and explain the improvements:\n\n```[language]\n[paste code here]\n```',
    icon: Cpu,
    color: 'border-zinc-800 bg-zinc-900/40 text-zinc-300 hover:border-zinc-700'
  },
  {
    title: 'Debug & Fix Errors',
    desc: 'Locate syntax flaws, logic issues, and security bugs.',
    prompt: 'The following code snippet is failing or contains bugs/security risks. Find the logical flaws, explain why they occur, and write a fixed, secure, and clean version of the code:\n\n```[language]\n[paste code here]\n```',
    icon: ShieldAlert,
    color: 'border-zinc-800 bg-zinc-900/40 text-zinc-300 hover:border-zinc-700'
  },
  {
    title: 'Document Codebase',
    desc: 'Create clear docstrings, READMEs, or API references.',
    prompt: 'Add clear, comprehensive docstrings, inline comments, and markdown explanation detailing inputs, outputs, exceptions, and overall design patterns for the following code:\n\n```[language]\n[paste code here]\n```',
    icon: Code,
    color: 'border-zinc-800 bg-zinc-900/40 text-zinc-300 hover:border-zinc-700'
  }
];

const CodeAssistant = () => {
  const { chats, createChat } = useChat();
  const [promptText, setPromptText] = useState('');
  const [output, setOutput] = useState('');
  const [streaming, setStreaming] = useState(false);
  const [copied, setCopied] = useState(false);
  const eventSourceRef = useRef(null);

  const selectAction = (action) => {
    setPromptText(action.prompt);
  };

  const handleCopy = () => {
    navigator.clipboard.writeText(output);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleExecute = async (e) => {
    e.preventDefault();
    if (!promptText.trim() || streaming) return;

    setStreaming(true);
    setOutput('');

    // Ensure we have at least one active chat session to associate the query with
    let chatId;
    if (chats.length > 0) {
      chatId = chats[0].id;
    } else {
      const newChat = await createChat('Code Assistant Workspace');
      chatId = newChat.id;
    }

    const token = localStorage.getItem('token');
    const streamUrl = `${API_BASE_URL}/chat/stream?chatId=${chatId}&message=${encodeURIComponent(promptText)}&token=${token}`;

    const eventSource = new EventSource(streamUrl);
    eventSourceRef.current = eventSource;

    eventSource.onmessage = (event) => {
      if (event.data === '[DONE]') {
        eventSource.close();
        setStreaming(false);
      } else {
        setOutput((prev) => prev + event.data);
      }
    };

    eventSource.onerror = (error) => {
      console.error('SSE code stream error', error);
      eventSource.close();
      setStreaming(false);
    };
  };

  const handleStop = () => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      setStreaming(false);
    }
  };

  return (
    <div className="flex-1 overflow-hidden flex flex-col h-full bg-[#09090b] p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3 border-b border-zinc-800 pb-5 shrink-0">
        <div className="w-10 h-10 border border-zinc-800 bg-zinc-900 flex items-center justify-center rounded-xl">
          <Code className="w-5 h-5 text-zinc-300" />
        </div>
        <div>
          <h2 className="text-base font-bold text-white tracking-tight">Code Assistant Workspace</h2>
          <p className="text-xs text-zinc-400">Run syntax repairs, perform runtime refactorings, or draft tests locally</p>
        </div>
      </div>

      <div className="flex-grow overflow-hidden grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Left Side: Input console + Actions */}
        <div className="flex flex-col space-y-4 overflow-hidden h-full">
          {/* Quick actions cards */}
          <div className="grid grid-cols-2 gap-3 shrink-0 select-none">
            {ACTIONS.map((act) => {
              const Icon = act.icon;
              return (
                <button
                  key={act.title}
                  onClick={() => selectAction(act)}
                  className={`p-3 rounded-xl border text-left flex flex-col gap-1 transition duration-150 ${act.color}`}
                >
                  <div className="flex items-center gap-2">
                    <Icon className="w-4 h-4 shrink-0 text-zinc-400" />
                    <span className="text-xs font-semibold text-white tracking-wide truncate">{act.title}</span>
                  </div>
                  <span className="text-[10px] text-zinc-500 leading-tight">{act.desc}</span>
                </button>
              );
            })}
          </div>

          {/* Form input */}
          <form onSubmit={handleExecute} className="flex-grow flex flex-col space-y-3 min-h-[30vh]">
            <textarea
              required
              value={promptText}
              onChange={(e) => setPromptText(e.target.value)}
              placeholder="Paste code blocks here and adjust parameters..."
              className="flex-grow w-full p-4 bg-[#18181b] border border-zinc-805 text-zinc-200 text-xs font-mono resize-none focus:outline-none focus:border-zinc-700 rounded-xl leading-relaxed"
            />
            
            <div className="flex gap-3">
              {streaming ? (
                <button
                  type="button"
                  onClick={handleStop}
                  className="stripe-btn-danger w-full flex items-center justify-center gap-2 py-2.5"
                >
                  <StopCircle className="w-4 h-4" />
                  Halt Compilation
                </button>
              ) : (
                <button
                  type="submit"
                  disabled={!promptText.trim()}
                  className="stripe-btn-primary w-full flex items-center justify-center gap-2 py-2.5"
                >
                  <Play className="w-4 h-4" />
                  Compile & Run
                </button>
              )}
            </div>
          </form>
        </div>

        {/* Right Side: Output editor */}
        <div className="premium-card p-5 flex flex-col space-y-4 h-full overflow-hidden">
          <div className="flex items-center justify-between border-b border-zinc-800 pb-3 shrink-0 select-none">
            <span className="text-xs font-bold text-white uppercase tracking-wider flex items-center gap-2">
              <Code className="w-4 h-4 text-zinc-400" />
              Generated Response
            </span>

            {output && (
              <button
                onClick={handleCopy}
                className="stripe-btn-secondary py-1 px-2.5 text-[10px]"
              >
                {copied ? <Check className="w-3 h-3 text-emerald-500" /> : <Copy className="w-3 h-3" />}
                {copied ? 'Copied' : 'Copy Output'}
              </button>
            )}
          </div>

          <div className="flex-grow bg-zinc-900/30 p-4 rounded-xl border border-zinc-850 overflow-y-auto">
            {output ? (
              <pre className="text-xs font-mono text-zinc-350 whitespace-pre-wrap leading-relaxed">
                {output}
              </pre>
            ) : (
              <div className="h-full flex flex-col items-center justify-center text-center text-zinc-650 select-none">
                <Code className="w-8 h-8 mb-2 text-zinc-700" />
                <p className="text-xs text-zinc-500 leading-normal">Compiled refactor outputs and generated mock tests will display here in real-time.</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default CodeAssistant;
