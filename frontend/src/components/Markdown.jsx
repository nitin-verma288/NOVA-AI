import React, { useState } from 'react';
import { Terminal, Copy, Check } from 'lucide-react';

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

  // Keywords
  const keywords = /\b(const|let|var|function|return|if|else|for|while|do|switch|case|break|continue|import|export|from|class|extends|new|this|typeof|instanceof|try|catch|finally|throw|async|await|public|private|protected|void|int|double|float|String|boolean|interface|struct|fn|mut|impl|pub|use|namespace|using|std|package|private)\b/g;
  escaped = escaped.replace(keywords, '<span class="code-keyword">$1</span>');

  // Strings
  escaped = escaped.replace(/(["'])(?:\\.|[^\\])*?\1/g, '<span class="code-string">$&</span>');

  // Numbers
  escaped = escaped.replace(/\b(\d+(?:\.\d+)?)\b/g, '<span class="code-number">$1</span>');

  // Comments
  escaped = escaped.replace(/(\/\/.*|\/\*[\s\S]*?\*\/|&lt;!--[\s\S]*?--&gt;)/g, '<span class="code-comment">$1</span>');

  console.log("HIGHLIGHT OUTPUT:",escaped);

  return escaped;
};

const renderInline = (text) => {
  let html = escapeHtml(text);
  
  // Bold: **text**
  html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
  html = html.replace(/__(.*?)__/g, '<strong>$1</strong>');
  
  // Italic: *text*
  html = html.replace(/\*(.*?)\*/g, '<em>$1</em>');
  html = html.replace(/_(.*?)_/g, '<em>$1</em>');
  
  // Inline code: `code`
  html = html.replace(/`(.*?)`/g, '<code class="px-1.5 py-0.5 rounded bg-zinc-800 text-zinc-200 text-[11px] font-mono">$1</code>');
  
  return <span dangerouslySetInnerHTML={{ __html: html }} />;
};

const CodeBlock = ({ content, language }) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="my-4 rounded-xl border border-zinc-800 bg-[#141416] overflow-hidden font-mono text-xs shadow-md">
      <div className="flex items-center justify-between px-4 py-2.5 bg-[#0e0e10] border-b border-zinc-800 text-[11px] text-zinc-400 select-none">
        <span className="uppercase font-semibold flex items-center gap-1.5">
          <Terminal className="w-3.5 h-3.5 text-zinc-500" />
          {language || 'code'}
        </span>
        <button 
          onClick={handleCopy}
          className="hover:text-white transition-colors duration-150 flex items-center gap-1.5 py-0.5 px-1.5 rounded hover:bg-zinc-800"
        >
          {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
          <span>{copied ? 'Copied' : 'Copy'}</span>
        </button>
      </div>
      <pre className="p-4 overflow-x-auto whitespace-pre leading-relaxed scrollbar-thin">
        <code dangerouslySetInnerHTML={{ __html: highlightCode(content, language) }} />
      </pre>
    </div>
  );
};

export const parseMarkdown = (text) => {
  if (!text) return [];
  const lines = text.split('\n');
  const blocks = [];
  let currentBlock = null;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();

    // 1. Code block handling
    if (trimmed.startsWith('```')) {
      if (currentBlock && currentBlock.type === 'code') {
        // Close code block
        blocks.push(currentBlock);
        currentBlock = null;
      } else {
        // Flush active block
        if (currentBlock) blocks.push(currentBlock);
        // Open new code block
        const lang = trimmed.substring(3).trim().toLowerCase();
        currentBlock = {
          type: 'code',
          lang: lang || 'text',
          lines: []
        };
      }
      continue;
    }

    if (currentBlock && currentBlock.type === 'code') {
      currentBlock.lines.push(line);
      continue;
    }

    // 2. Table handling
    if (trimmed.startsWith('|')) {
      if (currentBlock && currentBlock.type === 'table') {
        currentBlock.lines.push(trimmed);
      } else {
        if (currentBlock) blocks.push(currentBlock);
        currentBlock = {
          type: 'table',
          lines: [trimmed]
        };
      }
      continue;
    } else if (currentBlock && currentBlock.type === 'table') {
      blocks.push(currentBlock);
      currentBlock = null;
    }

    // 3. Headers
    if (trimmed.startsWith('#')) {
      const match = trimmed.match(/^(#{1,6})\s+(.*)$/);
      if (match) {
        if (currentBlock) blocks.push(currentBlock);
        blocks.push({
          type: 'header',
          level: match[1].length,
          content: match[2]
        });
        currentBlock = null;
        continue;
      }
    }

    // 4. Blockquote
    if (trimmed.startsWith('>')) {
      const content = trimmed.substring(1).trim();
      if (currentBlock && currentBlock.type === 'blockquote') {
        currentBlock.lines.push(content);
      } else {
        if (currentBlock) blocks.push(currentBlock);
        currentBlock = {
          type: 'blockquote',
          lines: [content]
        };
      }
      continue;
    } else if (currentBlock && currentBlock.type === 'blockquote') {
      blocks.push(currentBlock);
      currentBlock = null;
    }

    // 5. Unordered List
    if (trimmed.startsWith('- ') || trimmed.startsWith('* ')) {
      const content = trimmed.substring(2);
      if (currentBlock && currentBlock.type === 'ul') {
        currentBlock.items.push(content);
      } else {
        if (currentBlock) blocks.push(currentBlock);
        currentBlock = {
          type: 'ul',
          items: [content]
        };
      }
      continue;
    } else if (currentBlock && currentBlock.type === 'ul') {
      blocks.push(currentBlock);
      currentBlock = null;
    }

    // 6. Ordered List
    const olMatch = trimmed.match(/^(\d+)\.\s+(.*)$/);
    if (olMatch) {
      const content = olMatch[2];
      if (currentBlock && currentBlock.type === 'ol') {
        currentBlock.items.push(content);
      } else {
        if (currentBlock) blocks.push(currentBlock);
        currentBlock = {
          type: 'ol',
          items: [content]
        };
      }
      continue;
    } else if (currentBlock && currentBlock.type === 'ol') {
      blocks.push(currentBlock);
      currentBlock = null;
    }

    // 7. Paragraph or Empty line
    if (trimmed === '') {
      if (currentBlock) {
        blocks.push(currentBlock);
        currentBlock = null;
      }
    } else {
      if (currentBlock && currentBlock.type === 'paragraph') {
        currentBlock.content += '\n' + line;
      } else {
        if (currentBlock) blocks.push(currentBlock);
        currentBlock = {
          type: 'paragraph',
          content: line
        };
      }
    }
  }

  if (currentBlock) {
    blocks.push(currentBlock);
  }

  return blocks;
};

const Markdown = ({ text }) => {
  const blocks = parseMarkdown(text);

  return (
    <div className="markdown-body space-y-3 text-sm leading-relaxed text-zinc-300">
      {blocks.map((block, idx) => {
        switch (block.type) {
          case 'header': {
            const Tag = `h${block.level}`;
            const sizeClass = 
              block.level === 1 ? 'text-lg font-bold text-white mt-4 mb-2' :
              block.level === 2 ? 'text-base font-semibold text-white mt-3 mb-1.5' :
              'text-sm font-semibold text-white mt-2 mb-1';
            return <Tag key={idx} className={sizeClass}>{renderInline(block.content)}</Tag>;
          }
          case 'paragraph':
            return <p key={idx} className="mb-2 leading-relaxed">{renderInline(block.content)}</p>;
          case 'code':
            return <CodeBlock key={idx} content={block.lines.join('\n')} language={block.lang} />;
          case 'ul':
            return (
              <ul key={idx} className="list-disc pl-6 mb-3 space-y-1">
                {block.items.map((item, itemIdx) => (
                  <li key={itemIdx}>{renderInline(item)}</li>
                ))}
              </ul>
            );
          case 'ol':
            return (
              <ol key={idx} className="list-decimal pl-6 mb-3 space-y-1">
                {block.items.map((item, itemIdx) => (
                  <li key={itemIdx}>{renderInline(item)}</li>
                ))}
              </ol>
            );
          case 'blockquote':
            return (
              <blockquote key={idx} className="border-l-2 border-zinc-700 pl-4 py-0.5 my-2 text-zinc-400 italic">
                {block.lines.map((line, lIdx) => (
                  <p key={lIdx}>{renderInline(line)}</p>
                ))}
              </blockquote>
            );
          case 'table': {
            const rows = block.lines.map(line => {
              return line
                .split('|')
                .map(cell => cell.trim())
                .filter((_, cIdx, arr) => cIdx > 0 && cIdx < arr.length - 1);
            });
            
            if (rows.length === 0) return null;
            
            const hasHeader = rows.length > 1 && rows[1].every(cell => cell.startsWith('-') || cell.includes('---'));
            const headerRow = rows[0];
            const bodyRows = hasHeader ? rows.slice(2) : rows.slice(1);
            
            return (
              <div key={idx} className="overflow-x-auto my-3 rounded-lg border border-zinc-800 max-w-full">
                <table className="min-w-full border-collapse text-xs text-left">
                  <thead>
                    <tr className="bg-zinc-900 border-b border-zinc-800">
                      {headerRow.map((cell, cIdx) => (
                        <th key={cIdx} className="px-4 py-2 font-semibold text-zinc-200">
                          {renderInline(cell)}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {bodyRows.map((row, rIdx) => (
                      <tr key={rIdx} className="border-b border-zinc-850 hover:bg-zinc-900/30">
                        {row.map((cell, cIdx) => (
                          <td key={cIdx} className="px-4 py-2 text-zinc-400">
                            {renderInline(cell)}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            );
          }
          default:
            return null;
        }
      })}
    </div>
  );
};

export default Markdown;
