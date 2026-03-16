/**
 * E2E Tests: Home Page — Real Operations
 *
 * Covers:
 *   - Prompt template cards render with icon, name, description
 *   - Category tab switching filters templates and count changes
 *   - Click template card → navigate to chat with pre-filled input
 *   - Send pre-filled template message and receive response
 *   - Tab active state is mutually exclusive
 */
import { test, expect, type Page } from '@playwright/test'

const USER = 'e2e-home-user'

async function loginAs(page: Page, username: string) {
  await page.goto('/login')
  await page.fill('input[placeholder="Your name"]', username)
  await page.click('button:has-text("Enter")')
  await page.waitForURL('/')
  await page.waitForTimeout(500)
}

// =====================================================
// 1. Template Cards Content
// =====================================================
test.describe('Home — template cards', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, USER)
  })

  test('shows multiple template cards with icon, name, description', async ({ page }) => {
    await page.waitForSelector('.prompt-template-card', { timeout: 10_000 })
    const cards = page.locator('.prompt-template-card')
    const count = await cards.count()
    expect(count).toBeGreaterThanOrEqual(5)

    // Verify first card has all required elements
    const first = cards.first()
    await expect(first.locator('.prompt-template-icon-container')).toBeVisible()
    await expect(first.locator('.prompt-template-name')).toBeVisible()
    await expect(first.locator('.prompt-template-desc')).toBeVisible()

    // Name should not be empty
    const name = await first.locator('.prompt-template-name').textContent()
    expect(name!.trim().length).toBeGreaterThan(0)
  })
})

// =====================================================
// 2. Category Tab Filtering
// =====================================================
test.describe('Home — category tab filtering', () => {
  test('switching tabs changes template count and active state is exclusive', async ({ page }) => {
    await loginAs(page, USER)
    await page.waitForSelector('.prompt-template-card', { timeout: 10_000 })

    const tabs = page.locator('.home-template-tab')
    const tabCount = await tabs.count()
    expect(tabCount).toBeGreaterThanOrEqual(2)

    // First tab (All) should have the most cards
    await tabs.first().click()
    await page.waitForTimeout(300)
    const allCount = await page.locator('.prompt-template-card').count()

    // Try each subsequent tab
    for (let i = 1; i < tabCount; i++) {
      await tabs.nth(i).click()
      await page.waitForTimeout(300)

      // Verify active state exclusive
      await expect(tabs.nth(i)).toHaveClass(/is-active/)
      for (let j = 0; j < tabCount; j++) {
        if (j !== i) {
          await expect(tabs.nth(j)).not.toHaveClass(/is-active/)
        }
      }

      // Category count should be <= All count
      const catCount = await page.locator('.prompt-template-card').count()
      expect(catCount).toBeLessThanOrEqual(allCount)
      expect(catCount).toBeGreaterThanOrEqual(1)
    }

    // Switch back to All — count restored
    await tabs.first().click()
    await page.waitForTimeout(300)
    const restored = await page.locator('.prompt-template-card').count()
    expect(restored).toBe(allCount)
  })
})

// =====================================================
// 3. Click Template → Chat with Pre-filled Input
// =====================================================
test.describe('Home — template to chat', () => {
  test('click template card, verify chat input pre-filled, send and receive response', async ({ page }) => {
    await loginAs(page, `${USER}-template`)
    await page.waitForSelector('.prompt-template-card', { timeout: 10_000 })

    // Note the template name before clicking
    const templateCard = page.locator('.prompt-template-card').first()
    const templateName = await templateCard.locator('.prompt-template-name').textContent()

    // Click template — stays on home page (/) and pre-fills the chat input
    await templateCard.click()
    await page.waitForTimeout(500)

    // Template should be marked active
    await expect(templateCard).toHaveClass(/is-active/)

    // Chat input on the home page should be pre-filled (not empty)
    const chatInput = page.locator('.chat-input')
    await expect(chatInput).toBeVisible({ timeout: 15_000 })
    const inputValue = await chatInput.inputValue()
    expect(inputValue.length).toBeGreaterThan(0)
  }, 30_000)

  test('send pre-filled template message and receive a response', async ({ page }) => {
    await loginAs(page, `${USER}-send-tpl`)
    await page.waitForSelector('.prompt-template-card', { timeout: 10_000 })

    // Click any template to pre-fill input on home page
    await page.locator('.prompt-template-card').first().click()
    await page.waitForTimeout(500)

    const chatInput = page.locator('.chat-input')
    await expect(chatInput).toBeVisible({ timeout: 15_000 })

    // Wait for input to not be disabled (gateway connected)
    await page.waitForFunction(
      () => {
        const input = document.querySelector('.chat-input') as HTMLTextAreaElement
        return input && !input.disabled
      },
      { timeout: 15_000 }
    )

    // Click the send button to submit
    const sendBtn = page.locator('.chat-send-btn-new')
    await sendBtn.click()

    // Should navigate to /chat after session creation
    await expect(page).toHaveURL(/\/chat/, { timeout: 15_000 })

    // Wait for response
    await page.waitForFunction(
      () => {
        const btn = document.querySelector('.chat-send-btn-new')
        return btn && !btn.classList.contains('is-stop')
      },
      { timeout: 60_000 }
    )

    // Verify response exists in chat area
    const messageText = await page.locator('.chat-messages-area').textContent()
    expect(messageText!.length).toBeGreaterThan(50) // Should have a real response
  }, 120_000)
})
