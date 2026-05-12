import React from 'react';
import '@radix-ui/themes/styles.css';
import './src/styles/scrollbar.css';
import './styles.css';
import { Theme } from '@radix-ui/themes';
import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';

import { AuthProvider } from './src/contexts/AuthContext.tsx';
import ProtectedRoute from './src/components/auth/ProtectedRoute.tsx';
import ErrorBoundary from './src/components/ErrorBoundary.tsx';
import Home from './src/pages/Home.tsx';
import ExecutionHistory from './src/pages/ExecutionHistory.tsx';
import AuthPage from './src/pages/AuthPage.tsx';
import NotFound from './src/pages/NotFound.tsx';

const App: React.FC = () => {
  return (
    <ErrorBoundary>
      <Theme appearance="dark" radius="large" scaling="100%">
        <AuthProvider>
          <Router future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
            <main className="min-h-screen font-sans bg-[#050816]">
              <Routes>
                {/* Auth */}
                <Route path="/auth" element={<AuthPage />} />

                {/* Main app — all protected */}
                <Route
                  path="/"
                  element={
                    <ProtectedRoute>
                      <Home />
                    </ProtectedRoute>
                  }
                />
                {/* /chat and /dashboard both render Home — view state is managed inside Home */}
                <Route
                  path="/chat"
                  element={
                    <ProtectedRoute>
                      <Home />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/dashboard"
                  element={
                    <ProtectedRoute>
                      <Home />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/history"
                  element={
                    <ProtectedRoute>
                      <ExecutionHistory />
                    </ProtectedRoute>
                  }
                />

                {/* Catch-all */}
                <Route path="*" element={<NotFound />} />
              </Routes>
              <ToastContainer
                position="top-right"
                autoClose={3000}
                theme="dark"
                hideProgressBar={false}
                newestOnTop
                closeOnClick
                pauseOnHover
              />
            </main>
          </Router>
        </AuthProvider>
      </Theme>
    </ErrorBoundary>
  );
};

export default App;