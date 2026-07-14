from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    host: str = "0.0.0.0"
    port: int = 8090
    platform_url: str = "http://localhost:8080"
    minio_endpoint: str = "localhost:9000"
    minio_access_key: str = "atp_minio"
    minio_secret_key: str = "atp_minio_pass"
    minio_bucket: str = "atp-recordings"
    minio_secure: bool = False
    work_dir: str = "/tmp/atp-executor"
    max_workers: int = 20

    class Config:
        env_prefix = "ATP_EXEC_"


settings = Settings()
