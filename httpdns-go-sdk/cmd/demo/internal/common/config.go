package common

import (
	"os"
	"path/filepath"
	"strings"

	"gopkg.in/yaml.v3"
)

type AppConfig struct {
	Service struct {
		Host string `yaml:"host"`
		Port int    `yaml:"port"`
	} `yaml:"service"`
	HTTPDNS struct {
		AccountID    string `yaml:"account_id"`
		AESKey       string `yaml:"aes_key"`
		SignKey      string `yaml:"sign_key"`
		SignAlg      string `yaml:"sign_algorithm"`
		SignParam    string `yaml:"sign_param_name"`
		DispatchHost string `yaml:"dispatch_host"`
		ResolveHost  string `yaml:"resolve_host"`
		DefaultOS    string `yaml:"default_sdns_os"`
	} `yaml:"httpdns"`
}

func LoadConfigOrDefault(defaultFile string) (AppConfig, error) {
	path := strings.TrimSpace(os.Getenv("APP_CONFIG_PATH"))
	if path == "" {
		path = defaultFile
	}
	b, err := os.ReadFile(filepath.Clean(path))
	if err != nil {
		return AppConfig{}, err
	}
	var cfg AppConfig
	if err := yaml.Unmarshal(b, &cfg); err != nil {
		return AppConfig{}, err
	}
	if cfg.Service.Host == "" {
		cfg.Service.Host = "0.0.0.0"
	}
	if cfg.Service.Port <= 0 {
		cfg.Service.Port = 38088
	}
	if cfg.HTTPDNS.SignAlg == "" {
		cfg.HTTPDNS.SignAlg = "hmac-sha1"
	}
	if cfg.HTTPDNS.SignParam == "" {
		cfg.HTTPDNS.SignParam = "sign"
	}
	if cfg.HTTPDNS.DefaultOS == "" {
		cfg.HTTPDNS.DefaultOS = "ios"
	}
	return cfg, nil
}
