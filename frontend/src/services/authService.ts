import type { AuthResponse, LoginRequest, SignupRequest, RefreshTokenRequest, User } from '../types/auth';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';
const AUTH_BASE = `${API_BASE}/api/auth`;

// Storage keys
const STORAGE_KEYS = {
  ACCESS_TOKEN: 'aura_access_token',
  REFRESH_TOKEN: 'aura_refresh_token',
  USER: 'aura_user',
} as const;

class AuthService {
  private accessToken: string | null = null;
  private refreshTokenValue: string | null = null;
  private refreshPromise: Promise<AuthResponse> | null = null;
  private isInitialized = false;

  constructor() {
    this.initialize();
  }

  private initialize(): void {
    if (this.isInitialized) return;
    
    try {
      // Safely load tokens from localStorage
      this.accessToken = this.getStorageItem(STORAGE_KEYS.ACCESS_TOKEN);
      this.refreshTokenValue = this.getStorageItem(STORAGE_KEYS.REFRESH_TOKEN);
      this.isInitialized = true;
    } catch (error) {
      console.warn('Failed to initialize auth service from storage:', error);
      this.clearTokens();
      this.isInitialized = true;
    }
  }

  private getStorageItem(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch (error) {
      console.warn(`Failed to read from localStorage: ${key}`, error);
      return null;
    }
  }

  private setStorageItem(key: string, value: string): void {
    try {
      localStorage.setItem(key, value);
    } catch (error) {
      console.warn(`Failed to write to localStorage: ${key}`, error);
    }
  }

  private removeStorageItem(key: string): void {
    try {
      localStorage.removeItem(key);
    } catch (error) {
      console.warn(`Failed to remove from localStorage: ${key}`, error);
    }
  }

  async login(credentials: LoginRequest): Promise<AuthResponse> {
    try {
      const response = await fetch(`${AUTH_BASE}/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(credentials),
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({ message: 'Login failed' }));
        throw new Error(errorData.message || 'Login failed');
      }

      const authResponse: AuthResponse = await response.json();
      this.setTokens(authResponse.accessToken, authResponse.refreshToken);
      this.setUser(authResponse.user);
      return authResponse;
    } catch (error) {
      console.error('Login error:', error);
      throw error;
    }
  }

  async signup(data: SignupRequest): Promise<AuthResponse> {
    try {
      const response = await fetch(`${AUTH_BASE}/signup`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(data),
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({ message: 'Signup failed' }));
        throw new Error(errorData.message || 'Signup failed');
      }

      const authResponse: AuthResponse = await response.json();
      this.setTokens(authResponse.accessToken, authResponse.refreshToken);
      this.setUser(authResponse.user);
      return authResponse;
    } catch (error) {
      console.error('Signup error:', error);
      throw error;
    }
  }

  async refreshToken(): Promise<AuthResponse> {
    // Prevent multiple simultaneous refresh requests
    if (this.refreshPromise) {
      return this.refreshPromise;
    }

    if (!this.refreshTokenValue) {
      throw new Error('No refresh token available');
    }

    this.refreshPromise = this.performTokenRefresh();
    
    try {
      const result = await this.refreshPromise;
      return result;
    } finally {
      this.refreshPromise = null;
    }
  }

  private async performTokenRefresh(): Promise<AuthResponse> {
    try {
      const response = await fetch(`${AUTH_BASE}/refresh`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ refreshToken: this.refreshTokenValue }),
      });

      if (!response.ok) {
        this.clearTokens();
        throw new Error('Token refresh failed');
      }

      const authResponse: AuthResponse = await response.json();
      this.setTokens(authResponse.accessToken, authResponse.refreshToken);
      if (authResponse.user) {
        this.setUser(authResponse.user);
      }
      return authResponse;
    } catch (error) {
      console.error('Token refresh error:', error);
      this.clearTokens();
      throw error;
    }
  }

  async logout(): Promise<void> {
    if (this.refreshTokenValue) {
      try {
        await fetch(`${AUTH_BASE}/logout`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${this.accessToken}`,
          },
          body: JSON.stringify({ refreshToken: this.refreshTokenValue }),
        });
      } catch (error) {
        console.error('Logout request failed:', error);
      }
    }
    
    this.clearTokens();
  }

  getAccessToken(): string | null {
    return this.accessToken;
  }

  getRefreshToken(): string | null {
    return this.refreshTokenValue;
  }

  isAuthenticated(): boolean {
    return !!this.accessToken && !this.isTokenExpired(this.accessToken);
  }

  getUser(): User | null {
    try {
      const userData = this.getStorageItem(STORAGE_KEYS.USER);
      return userData ? JSON.parse(userData) : null;
    } catch (error) {
      console.warn('Failed to parse user data from storage:', error);
      return null;
    }
  }

  private setTokens(accessToken: string, refreshToken: string): void {
    this.accessToken = accessToken;
    this.refreshTokenValue = refreshToken;
    this.setStorageItem(STORAGE_KEYS.ACCESS_TOKEN, accessToken);
    this.setStorageItem(STORAGE_KEYS.REFRESH_TOKEN, refreshToken);
  }

  private setUser(user: User): void {
    this.setStorageItem(STORAGE_KEYS.USER, JSON.stringify(user));
  }

  private clearTokens(): void {
    this.accessToken = null;
    this.refreshTokenValue = null;
    this.removeStorageItem(STORAGE_KEYS.ACCESS_TOKEN);
    this.removeStorageItem(STORAGE_KEYS.REFRESH_TOKEN);
    this.removeStorageItem(STORAGE_KEYS.USER);
  }

  // Decode JWT payload (without verification - for client-side use only)
  private decodeToken(token: string): any {
    try {
      const base64Url = token.split('.')[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(
        atob(base64)
          .split('')
          .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
      return JSON.parse(jsonPayload);
    } catch (error) {
      console.error('Token decode error:', error);
      return null;
    }
  }

  isTokenExpired(token: string): boolean {
    const decoded = this.decodeToken(token);
    if (!decoded || !decoded.exp) return true;
    
    const currentTime = Math.floor(Date.now() / 1000);
    // Add 30 second buffer to prevent edge cases
    return decoded.exp < (currentTime + 30);
  }

  getUserFromToken(token: string): User | null {
    const decoded = this.decodeToken(token);
    if (!decoded) return null;
    
    return {
      id: decoded.userId,
      email: decoded.sub,
      firstName: decoded.firstName || '',
      lastName: decoded.lastName || '',
      role: decoded.role || 'USER'
    };
  }

  // Check if tokens need refresh
  shouldRefreshToken(): boolean {
    if (!this.accessToken || !this.refreshTokenValue) return false;
    
    // Refresh if access token expires in the next 5 minutes
    const decoded = this.decodeToken(this.accessToken);
    if (!decoded || !decoded.exp) return false;
    
    const currentTime = Math.floor(Date.now() / 1000);
    const timeUntilExpiry = decoded.exp - currentTime;
    return timeUntilExpiry < 300; // 5 minutes
  }

  // Initialize auth state from storage
  initializeFromStorage(): { user: User | null; isAuthenticated: boolean } {
    this.initialize();
    
    const user = this.getUser();
    const isAuthenticated = this.isAuthenticated();
    
    return { user, isAuthenticated };
  }
}

// Export singleton instance
export const authService = new AuthService();

// Export class for testing
export { AuthService };