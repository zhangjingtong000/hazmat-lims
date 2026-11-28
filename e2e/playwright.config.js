/** E2E 配置：在本目录执行 npx playwright test（后端需先起在 8080） */
module.exports = {
  testDir: './tests',
  timeout: 60000,
  reporter: [['list']],
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:8080',
    viewport: { width: 1440, height: 900 }
  }
}
