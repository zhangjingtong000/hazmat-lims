/**
 * E2E 配置：在本目录执行 npx playwright test（后端需先起在 8080）
 *
 * 单 worker 串行：用例共用同一份演示库，部分断言依赖"复核出库前快照 → 出库后比对"的
 * 批次余量动态基数（accept A09/A10），而 review R07 也会在同一化学品上走出库与归还。
 * 多 worker 并发时两次扣减/回补交错，快照与断言之间会被别的用例改账，断言随机差 1。
 * 串行让每个文件内的用例按序独占演示数据，结果才是确定的。
 */
module.exports = {
  testDir: './tests',
  timeout: 60000,
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:8080',
    viewport: { width: 1440, height: 900 }
  }
}
