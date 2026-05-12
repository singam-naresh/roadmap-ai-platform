import React, { Component, ErrorInfo, ReactNode } from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error?: Error;
}

class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    // Only log in development — suppress in production
    if (import.meta.env.DEV) {
      console.error('[ErrorBoundary]', error.message, errorInfo.componentStack);
    }
  }

  handleRetry = () => {
    this.setState({ hasError: false, error: undefined });
  };

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div className="min-h-[200px] flex items-center justify-center p-8">
          <div className="text-center space-y-4 max-w-sm">
            <div className="w-12 h-12 mx-auto bg-red-500/10 rounded-full flex items-center justify-center">
              <AlertTriangle className="w-6 h-6 text-red-400" />
            </div>
            <div className="space-y-1">
              <h3 className="text-base font-semibold text-white">Something went wrong</h3>
              <p className="text-sm text-slate-400">
                This section failed to load. Try refreshing.
              </p>
            </div>
            <button
              onClick={this.handleRetry}
              className="inline-flex items-center gap-2 px-4 py-2 bg-purple-600 hover:bg-purple-500 text-white text-sm rounded-lg transition-colors"
            >
              <RefreshCw size={14} />
              Try Again
            </button>
          </div>
        </div>
      );
    }

    // Guard against null/undefined children to prevent childNodes crash
    if (this.props.children == null) return null;
    return this.props.children;
  }
}

export default ErrorBoundary;
