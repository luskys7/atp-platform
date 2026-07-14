package config

import (
	"github.com/spf13/viper"
)

type Config struct {
	Env         string `mapstructure:"env"`
	ServerPort  int    `mapstructure:"server_port"`
	MySQLDSN    string `mapstructure:"mysql_dsn"`
	RedisAddr   string `mapstructure:"redis_addr"`
	RedisPass   string `mapstructure:"redis_password"`
	JWTSecret   string `mapstructure:"jwt_secret"`
	JWTExpire   int    `mapstructure:"jwt_expire_hours"`
	MinIO       MinIOConfig
	Scheduler   SchedulerConfig
}

type MinIOConfig struct {
	Endpoint  string `mapstructure:"endpoint"`
	AccessKey string `mapstructure:"access_key"`
	SecretKey string `mapstructure:"secret_key"`
	Bucket    string `mapstructure:"bucket"`
	UseSSL    bool   `mapstructure:"use_ssl"`
}

type SchedulerConfig struct {
	MaxParallelDevices int `mapstructure:"max_parallel_devices"`
	LockTTLSeconds     int `mapstructure:"lock_ttl_seconds"`
	QueueMaxSize       int `mapstructure:"queue_max_size"`
	TaskTimeoutSeconds int `mapstructure:"task_timeout_seconds"`
}

func Load() (*Config, error) {
	viper.SetConfigName("config")
	viper.SetConfigType("yaml")
	viper.AddConfigPath(".")
	viper.AddConfigPath("./config")
	viper.AutomaticEnv()
	viper.SetEnvPrefix("ATP")

	viper.SetDefault("env", "development")
	viper.SetDefault("server_port", 8080)
	viper.SetDefault("mysql_dsn", "atp_user:atp_pass@tcp(localhost:3306)/atp_platform?charset=utf8mb4&parseTime=True&loc=Local")
	viper.SetDefault("redis_addr", "localhost:6379")
	viper.SetDefault("jwt_secret", "atp-dev-secret")
	viper.SetDefault("jwt_expire_hours", 24)
	viper.SetDefault("minio.endpoint", "localhost:9000")
	viper.SetDefault("minio.access_key", "atp_minio")
	viper.SetDefault("minio.secret_key", "atp_minio_pass")
	viper.SetDefault("minio.bucket", "atp-recordings")
	viper.SetDefault("minio.use_ssl", false)
	viper.SetDefault("scheduler.max_parallel_devices", 20)
	viper.SetDefault("scheduler.lock_ttl_seconds", 300)
	viper.SetDefault("scheduler.queue_max_size", 100)
	viper.SetDefault("scheduler.task_timeout_seconds", 3600)

	_ = viper.ReadInConfig()

	var cfg Config
	if err := viper.Unmarshal(&cfg); err != nil {
		return nil, err
	}

	if dsn := viper.GetString("MYSQL_DSN"); dsn != "" {
		cfg.MySQLDSN = dsn
	}
	if addr := viper.GetString("REDIS_ADDR"); addr != "" {
		cfg.RedisAddr = addr
	}
	if secret := viper.GetString("JWT_SECRET"); secret != "" {
		cfg.JWTSecret = secret
	}

	return &cfg, nil
}
