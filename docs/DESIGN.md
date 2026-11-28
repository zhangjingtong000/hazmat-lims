# 设计说明

README 只保留结论，本文记录实现约束、规则判定方式与验证范围。接口清单见 [api.md](api.md)。

## 1. 系统构成

- 后端：Spring Boot 单应用，21 个 Controller、80 个端点、20 个 MyBatis Mapper，业务规则集中在 service 层。
- 前端：Vue 3 单页应用，28 条路由；构建产物由后端同源提供，服务端口 8080。
- 存储：H2 文件库（MODE=MySQL）或 MySQL 8。22 张基线表加 V2 的 `notify_message`，共 23 张业务表；另有迁移登记表 `schema_version`，全库 24 张。
- 迁移：`schema.sql` 为基线，语句全部带 `IF NOT EXISTS`，重复执行结果相同；`V1__baseline.sql` 只确认迁移基础设施，`V2__notify_message.sql` 追加站内信表。

## 2. 单据闭环与状态守卫

申购 → 多级审批 → 入库双签 → 领用双签出库 → 余量归还 → 废弃销账 → 盘点调账。每一步由服务层校验前置状态，冲突时返回业务码。双签要求登记人不能自任第二签；审批级数由化学品分类决定，1 到 4 级不等。

关键流转（借出、归还、报废、销账）统一写成带状态条件的更新：

```sql
UPDATE ... SET 状态 = 期望下一态 WHERE id = ? AND 状态 = 期望当前态
```

受影响行数是「成功」与「被并发抢先」的判据，判据不放在先查再改里。基础信息修改（`chem_info.update`、`updateStatus`）不套这个形状：先读后写、`WHERE id = ?` 直改，面向单用户维护档案，不承担并发防护。

## 3. 禁配矩阵

### 3.1 存储与查询

`compat_rule(group_a, group_b)` 按有序对存储。判冲突时把柜内在库批次的相容组与规则集两两配对，配对键由 `CompatService.pairKey` 生成：先把两端字符串排序再拼接。顺序在键里被抹平，因此一条规则覆盖两个查询方向——只存 `(酸, 碱)` 一行，按 `(碱, 酸)` 查同样命中。

新增与删除仍成对写两行，两条写操作同事务，不会出现半对规则；这样规则表每对完整，列表展示与人工维护口径一致。防重由 `UNIQUE(group_a, group_b)` 兜底。

### 3.2 自反必须禁止

自反（`group_a = group_b`）在服务层直接拒绝。同组化学品同柜是常态，若允许自反规则，一条数据就能让某类化学品全场无处可放，而且这种故障极难从日志里看出来。`UNIQUE` 只负责防重，禁自反是业务判定，两者分工不能混。

### 3.3 判定口径

- 判定依据是批次实际柜位，台账期望柜位不参与配对。一条化学品的批次可能分散在多柜，只有真正在柜内的批次参与配对。
- 化学品可以带两个相容组（主组与次组），任一命中即算冲突，避免换个组绕过去。
- 相容组是 7 个自定义分组：有机、易燃、酸、碱、氧化剂、还原剂、通用。判的是同柜互斥，不做隔离、隔开、分离三种储存方式的距离计算。

## 4. FEFO 出库

出库按效期先后排队逐批扣减：

1. 取候选批次：在库、有余量、未过期，按效期升序、批号次序排。
2. 用 `SUM(qty_left)` 做一次预检，总量不够直接拒绝，不做任何扣减。
3. 逐批执行 `UPDATE ... SET qty_left = qty_left - ? WHERE id = ? AND status = 0 AND qty_left >= ?`。
4. 受影响行数为 0 就 `continue` 换下一批，本次需求量原样带到下一批。
5. 全部批次扣完仍有缺口则回滚整体；台账侧只扣一次总量，不按批扣。

第 4 步是这套写法与先查再改的分水岭。先查再改在并发下会写出负余量或丢扣减；条件更新把「这一批还够不够」交给数据库判定，`affected=0` 是正常的换批信号。

单据状态 `1→2` 放在所有扣减与明细落库之后才推进，保证「出库中」的单据不可能只有半张明细。

候选集的效期条件必须保留：过期批次默认仍是 `status=0`，没有任务把它改成 3，只按状态与余量筛选会让效期最早的过期批次排在队首。SQL 侧用 `(expiry_date IS NULL OR expiry_date >= CURDATE())` 排除过期批次；没有失效日期的批次按不设期限处理，照常参与出库。

## 5. 账物守恒

不变量只有一句：某化学品台账的 `stock_qty`（在库量）恒等于它所有在库批次（`status=0`）的 `qty_left` 之和。

维护方式是让每一条改库存的路径同时改两边、都在同一事务里、都带条件，不依赖定期对账：入库加批次并加台账；出库扣批次并扣台账；盘盈盘亏按差值同向修正两边；废弃处置扣台账。过期封存的批次按口径已退出 Σ，销账时只把余量清零，不再动台账。任何一边的条件更新没命中，整个事务回滚，账面不会留下只改了半边的中间态。

### 5.1 两条现状缺口

- 过期封存（批次 `status=3`）在默认流程下不可达。`ChemInfoMapper.updateBatchStatus` 没有调用点，效期调度（`HazmatScheduler.scanExpiry`）只推站内信提醒，不迁移状态；要把批次置为 3 只能人工改库。因此依赖 `status==3` 的 type=3 废弃处置单（`WasteService` 硬校验，不满足返回 4502）在默认流程下打不通。补入口时要一并定「封存即扣台账」的口径，当前代码里没有这个扣减动作。
- 这条不变量是设计约定加守卫，不是可检测的断言。`sumBatchLeft` 只在两处用到：改类别前的存量判断与删除前的存量判断（`ChemInfoService.update` 与 `remove`）。全仓没有地方把 Σ 批次余量与 `stock_qty` 做比较，也没有周期性校验点。某条路径漏写不会报错，只会账实不符；要核对得手工跑 SQL 或另加只读校验接口。

### 5.2 盘点分两段

`finish` 只把当时的批次余量快照成账面数、记录实盘数与差值，一行库存都不动；`adjust` 才按差值做增量修正（正加负减，绝不直接设成实盘数），且每条差异只能调一次。调账前还要确认批次仍在库：结单到调账之间批次可能已被封存，此时对不在 Σ 里的批次做加减会破坏不变量，这类差异行会被标注为需要重盘并拒绝调账。

## 6. 令牌生命周期

- 签名有效不等于账号可用。`LoginInterceptor` 每次请求都回查 `sys_user.status`，停用或删除的账号即使持未过期令牌也拿 401。
- `TokenService` 另记每个用户的撤销时间点与逐条登出表：停用、改密、管理员重置口令、登出立即生效；重新启用后旧令牌仍 401，因为撤销时间点已记录。
- 撤销表只放内存、按过期时间清理。未配置 `APP_TOKEN_SECRET` 时密钥本来每次运行现生成，重启即全部失效。
- 令牌默认 12 小时有效（`app.token-hours`）。

## 7. 客户端 IP 与限流

默认只认 TCP 对端（`getRemoteAddr()`），伪造 `X-Forwarded-For` 不会改变登录锁定键（`IP|用户名`）与限流键。

部署在反向代理之后才开启 `app.security.trust-proxy`，并把反代地址列进 `app.security.trusted-proxies`（精确 IP、IPv4 CIDR、`10.0.0.*`）。清单为空时即使开启也不读 XFF。受信场景下从 XFF 最右侧往左取第一个非受信地址，客户端在左侧塞的伪造值会被跳过。

登录防爆破按 5 次失败锁 10 分钟（`app.security.login-attempt`），接口限流对写接口与登录生效，GET 不计入。

## 8. 部署配置

演示口令与开发默认值都是可覆盖的配置项，不写死在代码里。

| 环境变量 | 作用 | 未设置时的行为 |
| --- | --- | --- |
| `APP_SEED_ADMIN_PASSWORD` | 种子管理员 `admin` 的初始口令 | 回落到 `admin/admin123`，启动日志打印一行警告 |
| `APP_SEED_DEMO_PASSWORD` | 其余 6 个演示账号的初始口令 | 回落到 `123456` |
| `APP_TOKEN_SECRET` | 登录令牌的签名密钥 | 生成本次运行的临时随机密钥，重启后已签发令牌失效 |
| `APP_SECURITY_TRUST_PROXY` | 是否信任反向代理写入的 `X-Forwarded-For` | `false`：只认 TCP 对端地址 |
| `APP_SECURITY_TRUSTED_PROXIES` | 受信反代地址清单（精确 IP、IPv4 CIDR、`10.0.0.*`） | 空：即使开启 `trust-proxy` 也不读 XFF |
| `DEEPSEEK_API_KEY` | AI 问答的模型凭据 | `/health` 报 `ai=degraded(no-key)`，问答走规则检索 |

口令与密钥都不落盘、不回显：启动日志只说明取值来源，不打印本体。前两项建议在任何对外可达的部署里显式设置；`DEEPSEEK_API_KEY` 可选，AI 是增强路径，未配置时业务主流程不受影响。

### 8.1 容器

`Dockerfile` 是多阶段自包含构建：Node 阶段 `npm ci && npm run build`，Maven 阶段把 `dist` 拷进 `classpath:/static` 后打出单 jar，运行时镜像只带 JRE 与 jar。干净 clone 直接构建即可，不需要本机预装依赖：

```bash
docker build -t hazmat-management:1.0.0 .
docker run -d --name hazmat -p 8080:8080 -v hazmat-data:/app/data hazmat-management:1.0.0
```

`GET /health` 已配置为镜像健康检查。改用 MySQL 时传 `-e SPRING_PROFILES_ACTIVE=mysql` 与 `MYSQL_*`，见下一节。

### 8.2 MySQL 8

MySQL 8 不认 `CREATE INDEX IF NOT EXISTS`，schema 需要先转换。四条命令都在仓库根目录执行：

```bash
# 1) 建库
mysql -uroot -p --default-character-set=utf8mb4 \
  -e "CREATE DATABASE hazmat DEFAULT CHARACTER SET utf8mb4"

# 2) 生成 MySQL 版建表脚本（产出仓库根目录的 schema-mysql.sql）
python tools/convert_schema.py

# 3) 导入
mysql -uroot -p --default-character-set=utf8mb4 hazmat < schema-mysql.sql

# 4) 启动，建表已手工完成，初始化模式设为 never
export MYSQL_HOST=127.0.0.1 MYSQL_DATABASE=hazmat \
       MYSQL_USERNAME=hazmat MYSQL_PASSWORD='***' MYSQL_INIT_MODE=never
cd server && mvn -q spring-boot:run -Dspring-boot.run.profiles=mysql
```

跳过第 2 步直接导入，第一条 `CREATE UNIQUE INDEX IF NOT EXISTS` 就报 `ERROR 1064 (42000)`，客户端随即中止，22 张业务表里只建成 1 张。`MYSQL_INIT_MODE` 留在默认 `always` 时，应用启动会在同一条语句上失败，所以必须显式设 `never`。

转换只动索引语句，`CREATE TABLE IF NOT EXISTS` 与数据类型原样保留；脚本用 UTF-8 无 BOM 写出。

验证范围：单实例 MySQL 8.0.x（独立 datadir、独立端口）。5.7、DBA 托管实例、主从复制、跨时区部署不在验证范围内，接入前先在自有环境跑一遍上述四步与 `/health` 自检。

## 9. 实现约定

- SQL 只用 H2 与 MySQL 都支持的函数。效期预警与「已批超期未入库」用 `TIMESTAMPDIFF(DAY, a, b)`，两库语义一致；需要按自然日比较时再套 `CAST(audit_time AS DATE)`。`DATEDIFF` 的三参数形式、`FORMATDATETIME` 这类 H2 专有写法不进 SQL。
- 关键流转走条件更新（`WHERE 状态=期望`），以受影响行数判断成功与否：批次扣减与回加、单据状态推进、销账属于这一类。基础信息修改走无条件更新，涉及多表的写路径把事务边界声明在 Service 方法上。
- 前端导航不另抄一份。顶栏菜单由 `/auth/me` 返回的 `menus` 树渲染，后端按账号角色过滤；按钮级权限用同一份 `perms`。路由页面一律懒加载，重型库由 `manualChunks` 拆成独立 chunk，登录页不加载图表与 3D。

## 10. 测试与验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 后端单测 | `cd server && mvn -B test` | 216 条通过，22 个测试类，`-o` 离线可跑 |
| 前端构建 | `cd web && npm ci && npm run build` | vite 6 构建通过，入口约 48 kB（gzip 19 kB），图表、3D、表格库各自成 chunk 按需加载 |
| E2E | `cd e2e && npx playwright test` | 29 条通过，单 worker 串行 |
| CI | `.github/workflows/ci.yml` | 后端单测、前端构建、Playwright E2E 三个 job |

单测集中在规则本身：禁配的成对命中与自反拒绝、FEFO 跨批拆分与扣空换批、盘点两段分离、改派的条件更新幂等、超量归还拦截、令牌签名与登录锁定。E2E 覆盖登录、申购审批链、领用双签出库、盘点调账等主链路。

E2E 的前置是三件事：后端在 8080 上同时提供前端页面与 API；`web/dist/` 已构建；Playwright 浏览器已安装。任缺一件，失败信息都容易误读成业务失败。

E2E 固定单 worker 串行：29 条用例共用同一份演示库，accept 的 A09/A10 用「复核出库前快照、出库后比对批次余量」的动态基数，review 的 R07 也会在氯化钠上走出库与归还。并发跑时两次扣减与回补交错会污染快照，断言随机差 1；串行让每个文件独占演示数据，结果才是确定的。

H2 是单文件库，同一个库不能被两个进程同时打开。做 E2E 或并行验证时用独立库路径（如 `./data/e2e-hazmat`），否则第二个实例会在启动时锁失败。

## 11. 界面与截图

界面截图由 `e2e/scripts/screenshots.js` 生成（Playwright，1440×900）。后端起在 8080 后执行：

```bash
cd e2e && BASE_URL=http://localhost:8080 node scripts/screenshots.js
```

产物落在 `docs/screenshots/`，README 直接引用这 6 张。

## 12. 许可

MIT，见 [LICENSE](../LICENSE)。
