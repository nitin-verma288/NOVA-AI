import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { motion } from 'framer-motion';
import { Shield, AlertCircle } from 'lucide-react';

const Login = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await login(username, password);
      navigate('/');
    } catch (err) {
      let serverMsg = err.response?.data?.message;
      if (err.response?.data?.details) {
        const details = Object.entries(err.response.data.details)
          .map(([key, val]) => `${key}: ${val}`)
          .join(', ');
        serverMsg = `${serverMsg || 'Validation Error'} (${details})`;
      }
      if (!serverMsg) {
        serverMsg = err.response?.data?.error 
          || (err.response?.data && typeof err.response.data === 'string' ? err.response.data : null)
          || err.message 
          || 'Access Denied: Invalid credentials.';
      }
      setError(serverMsg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="h-screen w-screen bg-[#09090b] flex items-center justify-center font-sans">
      <motion.div 
        initial={{ opacity: 0, y: 15 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
        className="w-full max-w-[400px] p-8 bg-[#18181b] border border-zinc-800 rounded-2xl mx-4"
      >
        {/* Brand header */}
        <div className="flex flex-col items-center mb-6">
          <div className="w-10 h-10 border border-zinc-800 bg-[#27272a]/40 flex items-center justify-center rounded-xl mb-3">
            <Shield className="w-5 h-5 text-zinc-200" />
          </div>
          <h2 className="text-xl font-bold tracking-tight text-white">
            Sign in to Nova AI
          </h2>
          <p className="text-xs text-zinc-400 mt-1">Enter your credentials to access the console</p>
        </div>

        {error && (
          <motion.div 
            initial={{ opacity: 0, y: -4 }}
            animate={{ opacity: 1, y: 0 }}
            className="mb-4 p-3 rounded-lg bg-red-950/30 border border-red-900/50 text-red-200 text-xs flex items-start gap-2.5"
          >
            <AlertCircle className="w-4 h-4 mt-0.5 shrink-0 text-red-400" />
            <span>{error}</span>
          </motion.div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-zinc-400 mb-1.5 uppercase tracking-wider">Username</label>
            <input 
              type="text" 
              required
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="e.g. admin"
              className="clerk-input"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-zinc-400 mb-1.5 uppercase tracking-wider">Password</label>
            <input 
              type="password" 
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              className="clerk-input"
            />
          </div>

          <button 
            type="submit" 
            disabled={loading}
            className="stripe-btn-primary w-full mt-2"
          >
            {loading ? 'Signing in...' : 'Continue'}
          </button>
        </form>

        <div className="mt-6 text-center border-t border-zinc-800 pt-5">
          <p className="text-xs text-zinc-400">
            No profile configured?{' '}
            <Link to="/signup" className="text-white hover:underline font-semibold transition-colors duration-200">
              Create a profile
            </Link>
          </p>
        </div>
      </motion.div>
    </div>
  );
};

export default Login;
