# 再见周慕云 · Android

> 九个人，九段故事，属于我们的世界。

---

## 阶段里程碑

| 阶段 | 状态 | 内容 |
|------|------|------|
| Phase 1 | ✅ | 设计系统基础层 |
| Phase 2 | ✅ | WorldScreen 公馆首页 |
| Phase 3 | ✅ | CharacterScreen 书架 + 连接逻辑 |
| Phase 4 | ✅ | ChatScreen ✅ · CharacterDetailScreen ✅ |
| Phase 5 | ✅ | TaskCenterScreen ✅ · ProfileScreen ✅ · 全局动画 ✅ |
| Phase 6 | ✅ | SplashScreen 启动画面 |
| **Phase 7** | 🚧 **进行中** | Room 数据库 · LLM Provider 层 · ChatViewModel · 真实 AI 对话 · Event Engine 基础 |

---

## Phase 7 新增内容

### 数据层
```
data/
├── db/
│   ├── AppDatabase.kt            # Room 数据库（v1，含全部 v3 字段）
│   ├── entity/
│   │   ├── MessageEntity.kt      # 聊天记录表
│   │   ├── WorldEventEntity.kt   # Event Engine 核心表（含 projectId/importance）
│   │   └── CharacterIdentityEntity.kt  # 角色 Identity 持久化
│   └── dao/
│       ├── MessageDao.kt
│       ├── WorldEventDao.kt
│       └── CharacterIdentityDao.kt
├── model/
│   └── CharacterConfig.kt        # ★ 新增 identityConfig / goals 字段
├── provider/
│   ├── LLMProvider.kt            # 统一接口 + LLMMessage / LLMConfig
│   ├── OpenAICompatProvider.kt   # DeepSeek/火山方舟/阿里云/自定义 实现
│   └── ProviderManager.kt        # API Key 管理（EncryptedSharedPreferences）
├── prompt/
│   └── PromptOrchestrator.kt     # Prompt 组装（Phase 7：Identity Layer + Output）
└── repository/
    └── EventRepository.kt        # Event Engine Repository
```

### ViewModel
```
ui/viewmodel/
├── ChatViewModel.kt    # ★ 新增：消息持久化 + Event 记录 + 流式 LLM 调用
└── PresenceViewModel.kt
```

### 入口
```
ZaijianApp.kt           # Application，初始化 DB 和 ProviderManager
```

---

## 快速接入 AI（开发者）

1. 打开 App → Tab「我」→ AI 配置
2. 选择提供商（推荐 DeepSeek）
3. 填入 API Key → 测试连接
4. 进入任意角色聊天

目前支持的提供商：
| 提供商 | ID | Base URL |
|--------|-----|----------|
| DeepSeek | `deepseek` | `https://api.deepseek.com` |
| 火山方舟 | `volcengine` | `https://ark.cn-beijing.volces.com/api` |
| 阿里云百炼 | `aliyun` | `https://dashscope.aliyuncs.com/compatible-mode` |
| 自定义 | `custom` | 用户填写 |

---

## 文件结构

```
app/src/main/java/com/zaijian/zhoumuyun/
│
├── ZaijianApp.kt               # Application 入口（Phase 7 新增）
├── MainActivity.kt
│
├── data/
│   ├── db/                     # Room 数据库（Phase 7 新增）
│   ├── model/                  # 数据模型
│   ├── provider/               # LLM 提供商（Phase 7 新增）
│   ├── prompt/                 # Prompt 组装（Phase 7 新增）
│   └── repository/             # 数据仓库（Phase 7 新增）
│
└── ui/
    ├── design/
    │   └── DesignSystemShowcase.kt
    ├── component/              # 所有 UI 组件（Phase 1-6）
    ├── screen/                 # 所有页面（Phase 2-6）
    │   ├── ChatScreen.kt       # ★ Phase 7 升级：接入真实 AI
    │   └── ...
    ├── viewmodel/
    │   ├── ChatViewModel.kt    # ★ Phase 7 新增
    │   └── PresenceViewModel.kt
    └── theme/                  # 设计 Token（Phase 1）
```

---

## Phase 8 待办

- [ ] Memory Pipeline（Message → Event → MemoryCandidate → Memory）
- [ ] memories_fts FTS4 虚拟表
- [ ] Presence Engine V2（删除随机文案池，状态来自 Goal + Event）
- [ ] ProfileScreen AI 配置页接入真实逻辑（Key 输入 + 测试连接）
- [ ] 角色详情页记忆 Tab

---

## 设计 Token 速查

### 颜色 `ZaijianTheme.colors.*`

| Token | Light | Dark |
|-------|-------|------|
| `bgBase` | `#F7F8FA` | `#12131A` |
| `bgCard` | `#FFFFFF` | `#1C1E2A` |
| `accent` | `#8A9FB5` | `#8A9FB5` |
| `textPrimary` | `#1E2430` | `#E8EAF0` |
| `textSecondary` | `#7B8494` | `#7A7F96` |

### 间距 `Spacing.*`
```
xs=4  sm=8  md=16  lg=24  xl=32  xxl=48  (dp)
screenHorizontal=20  cardPadding=16
```

### 圆角 `Radius.*`
```
xs=6  sm=12  md=20  lg=28  circle=999  (dp)
```

### 动画时长 `AnimDuration.*`
```
instant=80  fast=150  bottomSheet=220  pageSwitch=250
bookOpen=300  breath=4000  (ms)
```

---

## 快速上手

```bash
# 1. 用 Android Studio 打开根目录
# 2. 等待 Gradle Sync（会下载 Room / KSP / Security 依赖）
# 3. Run 'app'
# 4. 在「我」→ AI 配置中填入 API Key
```

最低 SDK：API 26（Android 8.0）  
编译 SDK：34  
Kotlin：2.0.21  
KSP：2.0.21-1.0.27  
Room：2.6.1  

---

*v0.7.0 · Phase 7 进行中 · Room + LLM Provider + Event Engine 基础 · 真实 AI 对话*
