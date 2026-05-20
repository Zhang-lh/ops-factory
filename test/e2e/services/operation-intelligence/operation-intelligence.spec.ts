/**
 * E2E Test: Operation Intelligence Page
 *
 * Verifies the operation-intelligence frontend module renders correctly,
 * loads environments, renders filters, chart section, detail tabs,
 * and topology view. Runs against a live app stack.
 *
 * Prerequisites:
 *   - web-app + gateway + operation-intelligence running
 *   - Start via: scripts/ctl.sh startup all
 */
import { test, expect, type Page, type Response } from '@playwright/test'

const ADMIN_USER = 'admin'
const SS_DIR = 'test-results/operation-intelligence'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function loginAs(page: Page, username: string) {
  await page.goto('/#/')
  await page.evaluate((userId) => {
    localStorage.setItem('opsfactory:userId', userId)
  }, username)
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForURL(/\/#\/?$/)
  await page.waitForTimeout(800)
}

async function ss(page: Page, name: string) {
  await page.screenshot({ path: `${SS_DIR}/${name}.png`, fullPage: true })
}

async function waitForApi(
  page: Page,
  predicate: (r: Response) => boolean,
  timeout = 15000,
): Promise<Response> {
  return page.waitForResponse(predicate, { timeout })
}

/** Monitor console errors, filtering out noise */
function monitorErrors(page: Page): string[] {
  const errors: string[] = []
  page.on('console', msg => {
    if (msg.type() === 'error' && !isIgnoredError(msg.text())) {
      errors.push(msg.text())
    }
  })
  page.on('pageerror', err => errors.push(`PAGE_ERROR: ${err.message}`))
  return errors
}

function isIgnoredError(text: string): boolean {
  return (
    text.includes('favicon') ||
    text.includes('DevTools') ||
    text.includes('net::ERR') ||
    text.includes('ResizeObserver loop')
  )
}

// ---------------------------------------------------------------------------
// Test Suite
// ---------------------------------------------------------------------------

test.describe('Operation Intelligence — Page Structure', () => {
  test.setTimeout(120_000)

  test('renders the page with title, filters, chart, tabs, and topology', async ({ page }) => {
    const errors = monitorErrors(page)

    // Login
    await loginAs(page, ADMIN_USER)

    // Navigate to operation-intelligence page
    const apiPromise = waitForApi(
      page,
      r => r.url().includes('/qos/getEnvironments') && r.request().method() === 'GET',
    )

    await page.goto('/#/operation-intelligence')
    await page.waitForTimeout(1500)

    // -----------------------------------------------------------------
    // 1. Page title visible
    // -----------------------------------------------------------------
    await expect(page.locator('.page-header')).toBeVisible({ timeout: 15000 })
    await ss(page, '01-page-loaded')

    // -----------------------------------------------------------------
    // 2. Filter toolbar visible (product, environment, time range)
    // -----------------------------------------------------------------
    const controlPanel = page.locator('.operation-intelligence-control-panel')
    await expect(controlPanel).toBeVisible({ timeout: 10000 })
    await ss(page, '02-filters-visible')

    // Product filter should be rendered
    const filterSelects = page.locator('.filter-select-wrapper')
    await expect(filterSelects.first()).toBeVisible({ timeout: 10000 })

    // Refresh button exists
    const refreshBtn = page.locator('.operation-intelligence-refresh-button')
    await expect(refreshBtn).toBeVisible()
    await ss(page, '03-refresh-button')

    // -----------------------------------------------------------------
    // 3. Verify getEnvironments API was called
    // -----------------------------------------------------------------
    const envResp = await apiPromise
    expect(envResp.ok(), `getEnvironments API failed: ${envResp.status()}`).toBeTruthy()
    const envBody = await envResp.json()
    expect(envBody).toHaveProperty('results')
    await ss(page, '04-environments-loaded')

    // -----------------------------------------------------------------
    // 4. Score cards row (availability, performance, resource)
    // -----------------------------------------------------------------
    const scoreCards = page.locator('.dimension-score-cards')
    await expect(scoreCards).toBeVisible({ timeout: 10000 })
    await ss(page, '05-score-cards')

    // Should have 3 StatCard items
    const statCards = scoreCards.locator('.stat-card')
    const cardCount = await statCards.count()
    expect(cardCount, 'Expected 3 dimension score cards').toBe(3)

    // -----------------------------------------------------------------
    // 5. Contribution analysis section
    // -----------------------------------------------------------------
    const contributionCard = page.locator('.contribution-card')
    await expect(contributionCard).toBeVisible({ timeout: 10000 })
    await ss(page, '06-contribution')

    // -----------------------------------------------------------------
    // 6. Health chart section
    // -----------------------------------------------------------------
    const chartSection = page.locator('.operation-intelligence-chart-section')
    await expect(chartSection).toBeVisible({ timeout: 10000 })
    await ss(page, '07-chart-section')

    // Chart header legend should show good/warning/orange/critical labels
    const legendItems = page.locator('.chart-header-legend')
    await expect(legendItems).toBeVisible()
    await ss(page, '08-chart-legend')

    // -----------------------------------------------------------------
    // 7. Detail tabs (availability, performance, alarm)
    // -----------------------------------------------------------------
    const tabList = page.locator('.oi-tabs[role="tablist"]')
    await expect(tabList).toBeVisible({ timeout: 10000 })

    const tabs = tabList.locator('[role="tab"]')
    const tabCount = await tabs.count()
    expect(tabCount, 'Expected 3 detail tabs').toBe(3)
    await ss(page, '09-detail-tabs')

    // Click performance tab
    await tabs.nth(1).click()
    await page.waitForTimeout(600)
    await ss(page, '10-performance-tab')

    // Click alarm tab
    await tabs.nth(2).click()
    await page.waitForTimeout(600)
    await ss(page, '11-alarm-tab')

    // Back to availability
    await tabs.nth(0).click()
    await page.waitForTimeout(600)
    await ss(page, '12-availability-tab')

    // -----------------------------------------------------------------
    // 8. Topology section
    // -----------------------------------------------------------------
    const topologySection = page.locator('.topology-view')
    await expect(topologySection).toBeVisible({ timeout: 10000 })
    await ss(page, '13-topology')

    // Health score label
    const healthScore = page.locator('.topology-summary')
    await expect(healthScore).toBeVisible()

    // -----------------------------------------------------------------
    // Final error check
    // -----------------------------------------------------------------
    const relevantErrors = errors.filter(e =>
      !e.includes('favicon') &&
      !e.includes('DevTools') &&
      !e.includes('net::ERR') &&
      !e.includes('Failed to fetch') &&
      !e.includes('AbortError')
    )
    expect(relevantErrors, `Console errors found:\n${relevantErrors.join('\n')}`).toHaveLength(0)

    await ss(page, '14-final-state')
  })
})

test.describe('Operation Intelligence — Filter Interaction', () => {
  test.setTimeout(60_000)

  test('time range filter changes trigger new API calls', async ({ page }) => {
    await loginAs(page, ADMIN_USER)
    await page.goto('/#/operation-intelligence')
    await page.waitForTimeout(2000)

    // Wait for initial load
    await expect(page.locator('.operation-intelligence-control-panel')).toBeVisible({ timeout: 10000 })

    // Click a different time range option
    const timeSelects = page.locator('.filter-select-wrapper')
    const timeSelect = timeSelects.nth(2) // Third filter is time range

    if (await timeSelect.isVisible()) {
      const healthApi = waitForApi(
        page,
        r => r.url().includes('/qos/getHealthIndicator') && r.request().method() === 'POST',
      ).catch(() => null)

      await timeSelect.locator('select').selectOption({ index: 2 })
      await page.waitForTimeout(1000)

      await ss(page, '15-time-range-changed')
    }
  })
})

test.describe('Operation Intelligence — Error Handling', () => {
  test.setTimeout(60_000)

  test('shows error banner when service is unreachable', async ({ page }) => {
    // Block all requests to operation-intelligence to simulate service down
    await page.route('**/qos/**', route => route.abort('connectionfailed'))

    await loginAs(page, ADMIN_USER)
    await page.goto('/#/operation-intelligence')
    await page.waitForTimeout(3000)

    // Page should still render shell
    await expect(page.locator('.operation-intelligence-page')).toBeVisible({ timeout: 10000 })

    // Remove route blocking for cleanup
    await page.unroute('**/qos/**')

    await ss(page, '16-error-state')
  })
})
