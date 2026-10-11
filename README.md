# 修补花瓣测速，使其支持 Android 15+

## 现象
Android 15+ 设备上测速时：
- 实时速率绝大多数时间显示 **0**
- 但"使用流量"是**准确**的

## 修补方法
[修补方法.md](修补方法.md)

## 使用修补后的 APK
### 前提条件
由于花瓣测速有**签名检测**, 你需要使用安装**核心破解**的 Android 15+ 设备

- 前往[Releases](https://github.com/xjyzs/PetalSpeed/releases)下载修补后的 APK

## 问题原因: Android 15 的 `TrafficStats` 1 秒强制缓存
### 1 平台侧变化

Android 15 在 Mainline Connectivity 模块（`packages/modules/Connectivity`）中引入了：

- 类：`com.android.server.net.TrafficStatsRateLimitCache`
- 关联：`NetworkStatsService`
- **缓存有效期：1000 ms**
- 由 Compat Flag `ENABLE_TRAFFICSTATS_RATE_LIMIT_CACHE` 控制
- 且被强制开启：

```java
mAlwaysUseTrafficStatsRateLimitCache =
        SdkLevel.isAtLeastV()
     && DeviceConfigUtils.isTetheringFeatureNotChickenedOut(...);
```

因为走的是 `SdkLevel.isAtLeastV()`，所以**无视应用 targetSdk**，对全部 UID 生效。本 APK 的 targetSdk 是 29，一样受影响。

**Android 14 及以前**：`TrafficStats.getUidRxBytes/TxBytes` 直接读内核 eBPF map，每次都返回实时值，几十毫秒的差分能拿到真实微增量。

**Android 15**：同一 UID 的查询结果在 1000 ms 内**被缓存复用**，无论你调用多频繁，都只会每秒跳变一次。

### 2 与应用采样逻辑的乘法效应

SDK 原有的速率计算（见第 3 节）是**单轮差分**，采样周期实际为 **50 ms**：

```smali
const-wide/16 v4, 0x32      # 0x32 = 50
sub-long/2addr v4, v2
invoke-virtual {v0, p0, v4, v5}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z
```

且 `currentByte` / `currentTime` **每一轮都推进**。

于是每一秒的 20 轮里：

| 轮次 | Δbytes | Δt | 算出的速率 |
|---|---|---|---|
| 19 轮（缓存未刷新） | `0` | ~50 ms | **0** |
| 1 轮（缓存刚刷新） | ~1000 ms 的累积量 | ~50 ms | **约 20 倍虚高** |

- 19/20 轮写 `speedData[index] = 0` → 曲线被拉平为 0
- 1/20 轮写一个 ≈20 倍的值 → 出现规律性脉冲

分子（1000 ms 的量）与分母（50 ms）**时间跨度不匹配**，是脉冲的直接来源。

> 注意：这也是为什么「使用流量」仍然正确 —— 它由 `totalByte += (bytes - currentByte)` 累加得到，`currentByte` 每轮推进，跨轮累加后时间跨度自然吻合（telescoping），与速率计算的分母无关。

---