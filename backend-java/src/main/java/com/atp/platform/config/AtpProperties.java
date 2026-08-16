package com.atp.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "atp")
public class AtpProperties {

    private Jwt jwt = new Jwt();
    private Minio minio = new Minio();
    private StorageConfig storage = new StorageConfig();
    private Scheduler scheduler = new Scheduler();
    private Executor executor = new Executor();
    private Governance governance = new Governance();
    private Backup backup = new Backup();
    private Seed seed = new Seed();
    private Security security = new Security();
    private Sso sso = new Sso();
    private IosWda iosWda = new IosWda();
    private Device device = new Device();
    /** 录屏 v2 功能开关与性能阈值 */
    private Recording recording = new Recording();
    /** 访客启动器等静态下载目录 */
    private Downloads downloads = new Downloads();
    /** AI 用例生成（TestBrain / LLM 外挂，零底层侵入） */
    private AiCase aiCase = new AiCase();
    /** 全局变量，执行时注入变量链最低优先级层 */
    private java.util.Map<String, String> variables = new java.util.LinkedHashMap<>();

    @Data
    public static class StorageConfig {
        private String type = "minio";
        private String localPath = "./data/recordings";
        private String reportsPath = "./data/reports";
        private String appsPath = "./data/apps";
        /** 录屏/报告留存天数，超出自动清理 */
        private int retainDays = 90;
        private boolean autoCleanupEnabled = true;
        private String cleanupCron = "0 0 3 * * *";
        /** 存储目录总容量告警阈值（字节），0 表示不检测 */
        private long warnBytesThreshold = 10L * 1024 * 1024 * 1024;
    }

    @Data
    public static class Jwt {
        private String secret;
        private int expireHours = 24;
    }

    @Data
    public static class Minio {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
    }

    @Data
    public static class Scheduler {
        private int maxParallelDevices = 20;
        private int lockTtlSeconds = 300;
        private int queueMaxSize = 100;
        private int taskTimeoutSeconds = 3600;
    }

    @Data
    public static class Executor {
        private String url = "http://localhost:9002";
        /** 备用执行器节点，主节点不可用时自动 failover */
        private java.util.List<String> fallbackUrls = new java.util.ArrayList<>();
        /** 健康检查间隔（毫秒） */
        private long healthCheckIntervalMs = 60000;
        /** 调用 /execute 的 HTTP 读超时（秒），需不小于最长脚本执行时间 */
        private int executeTimeoutSeconds = 3700;
    }

    @Data
    public static class Governance {
        /** 通过率治理阈值（%），低于此值触发重试告警 */
        private double minPassRate = 99.0;
        /** 环境类错误码，不计入业务失败统计 */
        private java.util.List<String> envErrorCodes = java.util.List.of("E1002", "E1003", "E2003");
        /** 禁止用于自动化执行的安装包渠道 */
        private java.util.List<String> blockedPackageChannels = java.util.List.of("production");
    }

    @Data
    public static class Backup {
        /** 是否启用每日自动备份 */
        private boolean autoEnabled = true;
        /** 自动备份 Cron（默认每天 02:00） */
        private String cron = "0 0 2 * * *";
        /** 保留天数，超出自动清理 */
        private int retainDays = 30;
    }

    @Data
    public static class Seed {
        /** 空库（无控件且无用例）时自动导入便携种子 */
        private boolean importOnEmpty = true;
        /** 外部种子 zip 路径（优先于 classpath / data/seed） */
        private String externalZip = "";
    }

    @Data
    public static class Security {
        /** 是否启用内网 IP 白名单 */
        private boolean ipWhitelistEnabled = false;
        /** 允许的 IP 或 CIDR，如 192.168.1.0/24 */
        private java.util.List<String> ipWhitelist = new java.util.ArrayList<>();
    }

    @Data
    public static class Sso {
        private boolean enabled = false;
        private String providerName = "企业 SSO";
        /** 演示/本地模式：sso_token 格式 mockSecret:username */
        private boolean mockMode = true;
        private String mockSecret = "testflow-sso-demo";
        private String authorizeUrl = "";
        private String clientId = "";
    }

    @Data
    public static class IosWda {
        /** iOS 设备注册/心跳时 WDA 不可达则自动部署 */
        private boolean autoDeployEnabled = true;
        private String wdaIpaPath = "./data/wda/WebDriverAgentRunner.ipa";
        private String bundleId = "com.facebook.WebDriverAgentRunner.xctrunner";
        private String teamId = "";
        /** P12 证书路径（macOS 重签用，Windows 使用预签名 IPA） */
        private String p12Path = "";
        private String p12Password = "";
        private String provisioningProfilePath = "";
        private int wdaPort = 8100;
    }

    @Data
    public static class Device {
        /** local 模式：定时扫描 adb devices，USB 插入自动入库（免 Agent） */
        private boolean usbAutoDiscoverEnabled = false;
        /** 扫描间隔（毫秒） */
        private long usbScanIntervalMs = 10000;
        /** 自动白名单备注前缀，用于识别 USB 自动发现设备 */
        private String usbAutoWhitelistRemark = "USB自动发现";
    }

    @Data
    public static class Recording {
        /** 是否启用录屏 v2（FAB、状态栏、三模式裁剪、性能采样等） */
        private boolean v2Enabled = true;
        /** 识别率审计最低阈值（%） */
        private double minRecognitionRate = 95.0;
        /** 定位命中率审计最低阈值（%） */
        private double minLocatorHitRate = 98.0;
        /** 录制 CPU 性能采样：长任务每分钟上限（good 等级） */
        private int maxLongTasksPerMin = 2;
    }

    @Data
    public static class Downloads {
        /** 相对后端工作目录，默认 data/downloads */
        private String dir = "./data/downloads";
    }

    @Data
    public static class AiCase {
        /** 总开关：关闭后前端隐藏入口、接口直接拒绝 */
        private boolean enabled = true;
        /** llm | testbrain | offline（无 Key 时的规则草案，便于联调） */
        private String provider = "llm";
        /** TestBrain 服务根地址，如 http://10.0.98.20:8000 */
        private String testbrainUrl = "http://10.0.98.20:8000";
        /** OpenAI 兼容网关（DeepSeek/Qwen/自建） */
        private String llmBaseUrl = "https://api.deepseek.com";
        private String llmApiKey = "";
        private String llmModel = "deepseek-chat";
        private int timeoutSeconds = 120;
        private int maxCases = 8;
        /** Confluence 站点根，如 https://xxx.atlassian.net/wiki 或 https://confluence.company.com */
        private String confluenceBaseUrl = "";
        /** 默认 Token（也可由前端单次请求传入，不落库） */
        private String confluenceToken = "";
        /** cloud=Atlassian Cloud(Bearer)；server=Data Center(Bearer 或 Basic) */
        private String confluenceAuthType = "cloud";
        private String confluenceEmail = "";
        private int maxDocChars = 80000;
    }
}
