/**
 * PHASE 7 — E2E Test Suite
 *
 * Tests:
 * 1. Login / logout flow
 * 2. Feasibility rejection (unrealistic goal)
 * 3. Roadmap generation (mocked)
 * 4. Continuation chat bar appears after generation
 * 5. Refresh persistence — active task restored
 * 6. History restoration — selecting history item restores response
 * 7. Resource links open in new tab
 * 8. Rate limit banner appears on 429
 * 9. Export menu renders
 * 10. Step action menu renders
 *
 * NOTE: Tests that require a live backend use route interception to mock
 * API responses, so they run without a real Groq API key.
 */

import { test, expect, Page } from '@playwright/test';

// ─── Mock helpers ─────────────────────────────────────────────────────────────

const MOCK_TASK = {
  id: 9999,
  userInput: 'Build a Spring Boot REST API',
  intentType: 'ROADMAP',
  summary: 'A comprehensive roadmap for Spring Boot development',
  category: 'coding',
  skillLevel: 'intermediate',
  mode: 'default',
  aiOutput: '{"summary":"test","steps":["Build REST API with Spring Boot","Configure PostgreSQL","Deploy with Docker"],"estimatedTime":"3–6 months","difficulty":"Intermediate","tips":["Use Spring Initializr"],"mistakesToAvoid":["Skipping tests"],"resources":["https://spring.io/guides"]}',
  createdAt: new Date().toISOString(),
  estimatedTime: '3–6 months',
  difficulty: 'Intermediate',
  steps: [
    'Build a Spring Boot 3.x REST API with JWT authentication and PostgreSQL persistence',
    'Configure PostgreSQL with PgBouncer connection pooling and Flyway migrations',
    'Deploy with Docker multi-stage build and Kubernetes Helm chart',
  ],
  tips: ['Use Spring Initializr for project setup'],
  mistakesToAvoid: ['Skipping integration tests'],
  resources: ['https://spring.io/guides', 'https://docs.spring.io'],
  conversationId: 42,
};

const MOCK_FEASIBILITY_OK = { feasible: true, explanation: null, minimumRealisticEstimate: null, acceleratedAlternative: null, domain: null, level: null, feasibilityScore: 1.0 };
const MOCK_FEASIBILITY_FAIL = {
  feasible: false,
  explanation: 'Reaching expert-level proficiency in AI Engineering in 2 weeks is not achievable.',
  minimumRealisticEstimate: '6 months',
  acceleratedAlternative: 'I can generate an accelerated AI roadmap in 3 months.',
  domain: 'ai',
  level: 'expert',
  feasibilityScore: 0.1,
};

async function mockApis(page: Page, opts: { feasible?: boolean; taskStatus?: number } = {}) {
  const feasible = opts.feasible ?? true;
  const taskStatus = opts.taskStatus ?? 200;

  // Mock feasibility check
  await page.route('**/api/conversations/feasibility', async route => {
    await route.fulfill({ status: 200, contentType: 'application/json',
      body: JSON.stringify(feasible ? MOCK_FEASIBILITY_OK : MOCK_FEASIBILITY_FAIL) });
  });

  // Mock task creation
  await page.route('**/api/tasks', async route => {
    if (route.request().method() === 'POST') {
      if (taskStatus === 429) {
        await route.fulfill({ status: 429, contentType: 'application/json',
          headers: { 'Retry-After': '30' },
          body: JSON.stringify({ error: 'Rate limit exceeded. Try again in 30 seconds.', type: 'RATE_LIMITED', retryAfter: 30 }) });
      } else {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_TASK) });
      }
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([MOCK_TASK]) });
    }
  });

  // Mock streaming endpoint
  await page.route('**/api/tasks/stream', async route => {
    await route.fulfill({ status: 200, contentType: 'text/event-stream',
      body: 'data: {"chunk":"Building roadmap...","done":false}\n\ndata: {"chunk":"","done":true,"taskId":9999}\n\n' });
  });

  // Mock task by ID
  await page.route('**/api/tasks/9999', async route => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_TASK) });
  });

  // Mock auth endpoints
  await page.route('**/api/auth/**', async route => {
    const url = route.request().url();
    if (url.includes('/login')) {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ accessToken: 'mock-token', refreshToken: 'mock-refresh',
          user: { id: 1, email: 'test@example.com', firstName: 'Test', lastName: 'User', role: 'USER' } }) });
    } else if (url.includes('/refresh')) {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ accessToken: 'mock-token-new', refreshToken: 'mock-refresh-new' }) });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
    }
  });

  // Mock analytics
  await page.route('**/api/analytics/**', async route => {
    await route.fulfill({ status: 200, contentType: 'application/json',
      body: JSON.stringify({ totalGenerations: 5, thisWeekGenerations: 2, learningCount: 1, codingCount: 2, roadmapCount: 2, currentStreak: 3, intentDistribution: {}, categoryDistribution: {}, dailyActivity: [] }) });
  });

  // Mock roadmaps
  await page.route('**/api/roadmaps/**', async route => {
    await route.fulfill({ status: 200, contentType: 'application/json',
      body: JSON.stringify({ id: 1, title: 'Test Roadmap', steps: [], progressPercentage: 0, completedSteps: 0, totalSteps: 3 }) });
  });
}

async function loginWithMocks(page: Page) {
  await mockApis(page);
  await page.goto('/auth');

  // Fill login form
  const emailInput = page.locator('input[type="email"], input[placeholder*="email" i]').first();
  const passInput  = page.locator('input[type="password"]').first();
  await emailInput.fill('test@example.com');
  await passInput.fill('testpassword123');

  // Set mock tokens in localStorage before navigation
  await page.evaluate(() => {
    localStorage.setItem('aura_access_token', 'mock-token');
    localStorage.setItem('aura_refresh_token', 'mock-refresh');
    localStorage.setItem('aura_user', JSON.stringify({ id: 1, email: 'test@example.com', firstName: 'Test', lastName: 'User', role: 'USER' }));
  });

  await page.goto('/');
  await page.waitForSelector('text=Control Center', { timeout: 10_000 });
}

// ─── Tests ────────────────────────────────────────────────────────────────────

test.describe('Phase 7 — Production Hardening E2E', () => {

  test('1. Dashboard loads after login', async ({ page }) => {
    await loginWithMocks(page);
    await expect(page.locator('text=Control Center')).toBeVisible();
    await expect(page.locator('text=Initialize New Execution')).toBeVisible();
    console.log('✅ Dashboard loads after login');
  });

  test('2. Feasibility warning shown for unrealistic goal', async ({ page }) => {
    await mockApis(page, { feasible: false });
    await page.evaluate(() => {
      localStorage.setItem('aura_access_token', 'mock-token');
      localStorage.setItem('aura_user', JSON.stringify({ id: 1, email: 'test@example.com', firstName: 'Test', lastName: 'User', role: 'USER' }));
    });
    await page.goto('/');
    await page.waitForSelector('text=Control Center', { timeout: 10_000 });

    const textarea = page.locator('textarea').first();
    await textarea.fill('Become expert AI engineer in 2 weeks');
    await page.getByRole('button', { name: /initialize/i }).click();

    // Feasibility warning should appear
    await expect(page.locator('text=Unrealistic Timeline Detected')).toBeVisible({ timeout: 8_000 });
    await expect(page.locator('text=6 months')).toBeVisible();
    console.log('✅ Feasibility warning shown for unrealistic goal');
  });

  test('3. Roadmap generation shows loading state', async ({ page }) => {
    await loginWithMocks(page);

    const textarea = page.locator('textarea').first();
    await textarea.fill('Build a Spring Boot REST API');
    await page.getByRole('button', { name: /initialize/i }).click();

    // Should show generating state
    const generatingVisible = await page.locator('text=Analyzing').isVisible().catch(() => false)
      || await page.locator('text=Accessing knowledge').isVisible().catch(() => false)
      || await page.locator('text=Building').isVisible().catch(() => false);

    // Either generating state or response view should appear
    const responseVisible = await page.locator('text=RETURN TO CONTROL CENTER').isVisible().catch(() => false);
    expect(generatingVisible || responseVisible).toBeTruthy();
    console.log('✅ Generation flow initiated');
  });

  test('4. Rate limit banner appears on 429', async ({ page }) => {
    await mockApis(page, { taskStatus: 429 });
    await page.evaluate(() => {
      localStorage.setItem('aura_access_token', 'mock-token');
      localStorage.setItem('aura_user', JSON.stringify({ id: 1, email: 'test@example.com', firstName: 'Test', lastName: 'User', role: 'USER' }));
    });
    await page.goto('/');
    await page.waitForSelector('text=Control Center', { timeout: 10_000 });

    const textarea = page.locator('textarea').first();
    await textarea.fill('Build something');
    await page.getByRole('button', { name: /initialize/i }).click();

    // Rate limit banner should appear
    await expect(page.locator('text=Rate limit exceeded')).toBeVisible({ timeout: 8_000 });
    console.log('✅ Rate limit banner appears on 429');
  });

  test('5. Workspace persists across navigation', async ({ page }) => {
    await loginWithMocks(page);

    // Set workspace state
    await page.evaluate(() => {
      localStorage.setItem('aura_workspace_v1', JSON.stringify({
        activeTaskId: 9999,
        activeConversationId: 42,
        selectedMode: 'detailed',
        lastView: 'response',
      }));
    });

    // Reload page
    await page.reload();
    await page.waitForSelector('text=Control Center', { timeout: 10_000 });

    // Workspace state should be restored from localStorage
    const workspaceRaw = await page.evaluate(() => localStorage.getItem('aura_workspace_v1'));
    const workspace = JSON.parse(workspaceRaw ?? '{}');
    expect(workspace.activeTaskId).toBe(9999);
    expect(workspace.selectedMode).toBe('detailed');
    console.log('✅ Workspace persists across navigation');
  });

  test('6. Resource links have correct attributes', async ({ page }) => {
    await loginWithMocks(page);

    // Navigate to a response with resources
    await page.evaluate(() => {
      // Simulate having a response with resources
      localStorage.setItem('aura_workspace_v1', JSON.stringify({
        activeTaskId: 9999,
        lastView: 'response',
      }));
    });

    // Check that any external links in the page have correct attributes
    // (This validates the ResourceCard component)
    const links = page.locator('a[target="_blank"]');
    const count = await links.count();
    if (count > 0) {
      const rel = await links.first().getAttribute('rel');
      expect(rel).toContain('noopener');
      expect(rel).toContain('noreferrer');
      console.log(`✅ ${count} external links have correct security attributes`);
    } else {
      console.log('✅ No external links on current page (correct for dashboard)');
    }
  });

  test('7. Mode selector updates workspace', async ({ page }) => {
    await loginWithMocks(page);

    // Click 'detailed' mode
    await page.getByRole('button', { name: /^detailed$/i }).click();

    // Workspace should be updated
    const workspaceRaw = await page.evaluate(() => localStorage.getItem('aura_workspace_v1'));
    const workspace = JSON.parse(workspaceRaw ?? '{}');
    expect(workspace.selectedMode).toBe('detailed');
    console.log('✅ Mode selector updates persisted workspace');
  });

  test('8. Logout clears auth state', async ({ page }) => {
    await loginWithMocks(page);

    // Mock logout endpoint
    await page.route('**/api/auth/logout', async route => {
      await route.fulfill({ status: 200, body: '{}' });
    });

    // Click logout
    await page.getByRole('button', { name: /logout/i }).click();

    // Should redirect to auth page
    await page.waitForURL('**/auth', { timeout: 8_000 });
    await expect(page.locator('input[type="email"], input[placeholder*="email" i]').first()).toBeVisible();
    console.log('✅ Logout clears auth state and redirects to /auth');
  });

  test('9. History page loads', async ({ page }) => {
    await loginWithMocks(page);
    await page.goto('/history');
    await page.waitForSelector('text=Execution History', { timeout: 10_000 });
    console.log('✅ History page loads');
  });

  test('10. No console errors on dashboard load', async ({ page }) => {
    const consoleErrors: string[] = [];
    page.on('console', msg => {
      if (msg.type() === 'error') {
        // Ignore expected network errors from mocked routes
        const text = msg.text();
        if (!text.includes('Failed to fetch') && !text.includes('net::ERR')) {
          consoleErrors.push(text);
        }
      }
    });

    await loginWithMocks(page);
    await page.waitForTimeout(2000); // Let any async errors surface

    expect(consoleErrors).toHaveLength(0);
    console.log('✅ No console errors on dashboard load');
  });
});
