import React, { createContext, useContext, useEffect, useState, ReactNode, useCallback } from 'react';
import { toast } from 'react-toastify';
import type { User, AuthContextType, LoginRequest, SignupRequest } from '../types/auth';
import { authService } from '../services/authService';

const AuthContext = createContext<AuthContextType | undefined>(undefined);

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isInitialized, setIsInitialized] = useState(false);

  // Initialize auth state from storage and handle token refresh
  const initializeAuth = useCallback(async () => {
    if (isInitialized) return;
    
    try {
      setIsLoading(true);
      
      // Get initial state from storage
      const { user: storedUser, isAuthenticated } = authService.initializeFromStorage();
      
      if (isAuthenticated && storedUser) {
        // Valid token and user data exists
        setUser(storedUser);
        
        // Check if token needs refresh
        if (authService.shouldRefreshToken()) {
          try {
            const response = await authService.refreshToken();
            if (response.user) {
              setUser(response.user);
            }
          } catch (error) {
            console.warn('Token refresh failed during initialization:', error);
            // Don't clear user state immediately, let them continue with current session
          }
        }
      } else if (authService.getRefreshToken()) {
        // No valid access token but refresh token exists
        try {
          const response = await authService.refreshToken();
          if (response.user) {
            setUser(response.user);
          }
        } catch (error) {
          console.warn('Token refresh failed:', error);
          // Clear invalid tokens
          await authService.logout();
          setUser(null);
        }
      } else {
        // No valid tokens
        setUser(null);
      }
    } catch (error) {
      console.error('Auth initialization failed:', error);
      // Clear potentially corrupted state
      await authService.logout();
      setUser(null);
    } finally {
      setIsLoading(false);
      setIsInitialized(true);
    }
  }, [isInitialized]);

  useEffect(() => {
    initializeAuth();
  }, [initializeAuth]);

  const login = async (credentials: LoginRequest): Promise<void> => {
    try {
      setIsLoading(true);
      const response = await authService.login(credentials);
      setUser(response.user);
      toast.success('Login successful!');
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Login failed';
      toast.error(message);
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const signup = async (data: SignupRequest): Promise<void> => {
    try {
      setIsLoading(true);
      const response = await authService.signup(data);
      setUser(response.user);
      toast.success('Account created successfully!');
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Signup failed';
      toast.error(message);
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const logout = useCallback(async (): Promise<void> => {
    try {
      await authService.logout();
      setUser(null);
      toast.info('Logged out successfully');
    } catch (error) {
      console.error('Logout error:', error);
      // Force clear state even if logout request fails
      setUser(null);
      toast.info('Logged out');
    }
  }, []);

  const refreshToken = useCallback(async (): Promise<void> => {
    try {
      const response = await authService.refreshToken();
      if (response.user) {
        setUser(response.user);
      }
    } catch (error) {
      console.error('Token refresh failed:', error);
      // Clear user state and tokens on refresh failure
      setUser(null);
      await authService.logout();
      throw error;
    }
  }, []);

  // Provide stable context value
  const contextValue: AuthContextType = {
    user,
    isAuthenticated: !!user && authService.isAuthenticated(),
    isLoading,
    login,
    signup,
    logout,
    refreshToken,
  };

  // Don't render children until auth is initialized
  if (!isInitialized) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-[#0B1426]">
        <div className="text-center space-y-4">
          <div className="w-8 h-8 border-2 border-purple-500 border-t-transparent rounded-full animate-spin mx-auto"></div>
          <p className="text-slate-400 text-sm">Initializing...</p>
        </div>
      </div>
    );
  }

  return (
    <AuthContext.Provider value={contextValue}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};