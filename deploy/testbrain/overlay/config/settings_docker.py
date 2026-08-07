"""
Docker / ATP 外挂专用 settings。
通过 DJANGO_SETTINGS_MODULE=config.settings_docker 启用。
"""
import os
import sys
from pathlib import Path

os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")
os.environ["TOKENIZERS_PARALLELISM"] = "false"

TIME_ZONE = "Asia/Shanghai"
USE_TZ = False

BASE_DIR = Path(__file__).resolve().parent.parent
if str(BASE_DIR) not in sys.path:
    sys.path.insert(0, str(BASE_DIR))

SECRET_KEY = os.getenv("DJANGO_SECRET_KEY", "django-insecure-atp-testbrain-docker")
DEBUG = os.getenv("DJANGO_DEBUG", "true").lower() in ("1", "true", "yes")
ALLOWED_HOSTS = ["*"]
ENABLE_MILVUS = True

# 经 Vite :3000 同域反代嵌入时，浏览器 Origin 与后端 Host 不一致，需显式信任
# 额外在中间件中信任任意 *:3000 / *:8001（见 embed_middleware）
CSRF_TRUSTED_ORIGINS = [
    o.strip()
    for o in os.getenv(
        "CSRF_TRUSTED_ORIGINS",
        "http://localhost:3000,http://127.0.0.1:3000,http://localhost:8001,http://127.0.0.1:8001",
    ).split(",")
    if o.strip()
]
CSRF_COOKIE_SAMESITE = "Lax"
CSRF_COOKIE_HTTPONLY = False

# 同域反代嵌入：允许 ATP iframe；绝对路径由 embed_middleware 加 /testbrain 前缀
# 不设 FORCE_SCRIPT_NAME：避免干扰 runserver 静态资源路径
X_FRAME_OPTIONS = "SAMEORIGIN"
EMBED_URL_PREFIX = os.getenv("EMBED_URL_PREFIX", "/testbrain")

MEDIA_ROOT = os.path.join(BASE_DIR, "uploads")
MEDIA_URL = "/uploads/"
CORS_ORIGIN_ALLOW_ALL = True

INSTALLED_APPS = [
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    "apps.core",
    "apps.llm",
    "apps.knowledge.apps.KnowledgeConfig",
    "apps.ai_agents.iface_case_generator.apps.IfaceCaseGeneratorConfig",
    "apps.ai_agents.java_code_analyzer.apps.JavaCodeAnalyzerConfig",
    "apps.ai_agents.prd_analyzer.apps.PrdAnalyzerConfig",
    "apps.ai_agents.test_case_generator.apps.TestCaseGeneratorConfig",
    "apps.ai_agents.test_case_reviewer.apps.TestCaseReviewerConfig",
    "apps.atp_bridge.apps.AtpBridgeConfig",
]

MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
    "config.embed_middleware.TestBrainEmbedMiddleware",
]

ROOT_URLCONF = "config.urls_docker"
WSGI_APPLICATION = "config.wsgi.application"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [os.path.join(BASE_DIR, "templates")],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.debug",
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ],
        },
    },
]

DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.mysql",
        "NAME": os.getenv("MYSQL_DB", "test_brain_db"),
        "USER": os.getenv("MYSQL_USER", "testbrain"),
        "PASSWORD": os.getenv("MYSQL_PASSWORD", "testbrain123"),
        "HOST": os.getenv("MYSQL_HOST", "testbrain-mysql"),
        "PORT": os.getenv("MYSQL_PORT", "3306"),
        "OPTIONS": {"charset": "utf8mb4"},
    }
}

AUTH_PASSWORD_VALIDATORS = []
LANGUAGE_CODE = "zh-hans"
USE_I18N = True
STATIC_URL = "/static/"
STATICFILES_DIRS = [os.path.join(BASE_DIR, "static")]
DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

_default_provider = os.getenv("LLM_DEFAULT_PROVIDER", "qwen")
LLM_PROVIDERS = {
    "default_provider": _default_provider,
    "deepseek": {
        "name": "DeepSeek",
        "model": os.getenv("DEEPSEEK_MODEL", "deepseek-chat"),
        "base_url": os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1"),
        "temperature": 0.2,
        "max_tokens": 8192,
    },
    "qwen": {
        "name": "通义千问",
        "model": os.getenv("QWEN_MODEL", "qwen-plus"),
        "base_url": os.getenv(
            "QWEN_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1"
        ),
        "temperature": 0.2,
        "max_tokens": 8192,
    },
}

AGENT_LLM_DEFAULTS = {
    "test_case_generator": {"provider": _default_provider},
    "test_case_reviewer": {"provider": _default_provider},
    "prd_analyzer": {"provider": _default_provider},
    "java_code_analyzer": {"provider": _default_provider},
    "iface_case_generator": {"provider": _default_provider},
}

VECTOR_DB_CONFIG = {
    "host": os.getenv("MILVUS_HOST", "milvus"),
    "port": os.getenv("MILVUS_PORT", "19530"),
    "db_name": os.getenv("MILVUS_DB_NAME", "default"),
    "collection_name": os.getenv("MILVUS_COLLECTION", "vv_knowledge_collection"),
}

JAVA_ANALYZER_SERVICE_URL = os.getenv("JAVA_ANALYZER_SERVICE_URL", "")
JAVA_PROJECTS_BASE_DIR = "../"
PROJECT_ID_REPO_MAPPING = {}
GIT_CREDENTIALS = {"username": "", "password": ""}

EMBEDDING_PROVIDER = os.getenv("EMBEDDING_PROVIDER", "aliyun")
ALIYUN_EMBEDDING_CONFIG = {
    "api_key": os.getenv("QWEN_API_KEY", ""),
    "base_url": os.getenv(
        "ALIYUN_EMBEDDING_URL",
        "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings",
    ),
    "model": os.getenv("ALIYUN_EMBEDDING_MODEL", "text-embedding-v4"),
    "batch_size": 10,
}
