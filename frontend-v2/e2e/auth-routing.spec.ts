import { expect, test } from '@playwright/test'

const envelope = (data: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data, message: 'OK' }) })

test('unauthenticated users are redirected to login', async ({ page }) => {
  await page.route('**/api/auth/me', route => route.fulfill(envelope({ username: '', displayName: '', authenticated: false, permissions: [], roleCode: '', superAdmin: false })))
  await page.goto('/workflow/definitions')
  await expect(page).toHaveURL(/\/login\?redirect=/)
  await expect(page.getByRole('heading', { name: '登录' })).toBeVisible()
  await expect(page.getByRole('textbox', { name: '账号', exact: true })).toBeVisible()
  await expect(page.locator('#login-password')).toBeVisible()
})

test('cookie session login can enter workbench', async ({ page, context }) => {
  let authenticated = false
  await page.route('**/api/auth/me', route => route.fulfill(envelope(authenticated ? { username: 'e2e-user', displayName: 'E2E User', authenticated: true, permissions: ['WORKBENCH_VIEW'], roleCode: 'USER', superAdmin: false } : { username: '', displayName: '', authenticated: false, permissions: [], roleCode: '', superAdmin: false })))
  await page.route('**/api/auth/login', async route => { authenticated = true; await route.fulfill({ ...envelope({ token: '', username: 'e2e-user', expiresAt: '2099-01-01T00:00:00Z' }), headers: { 'set-cookie': 'platform_session=e2e-session; Path=/; HttpOnly; SameSite=Lax' } }) })
  await page.route('**/api/workbench/summary', route => route.fulfill(envelope({ issues: 0, running: 0, unpublished: 0, successRate24h: 100, successful24h: 1, failed24h: 0, unhealthySources: 0, generatedAt: '2099-01-01T00:00:00Z' })))
  await page.route('**/api/workbench/issues', route => route.fulfill(envelope([])))
  await page.route('**/api/workbench/recent-runs', route => route.fulfill(envelope([])))
  await page.goto('/login')
  const username = page.getByRole('textbox', { name: '账号', exact: true })
  await expect(username).toBeVisible()
  await username.fill('e2e-user')
  await page.locator('#login-password').fill('placeholder-password')
  await page.getByRole('button', { name: /^登录$/ }).click()
  await expect(page).toHaveURL('/')
  await expect.poll(async () => (await context.cookies()).some(cookie => cookie.name === 'platform_session')).toBe(true)
  await expect(page.getByText('工作台', { exact: true }).first()).toBeVisible()
})
