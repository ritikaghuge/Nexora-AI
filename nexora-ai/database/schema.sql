-- NEXORA AI - MySQL 8 schema (optional: the app also auto-creates/updates tables via Hibernate).
-- Run in MySQL Workbench:  File > Open SQL Script > Execute.
CREATE DATABASE IF NOT EXISTS nexora_ai CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE nexora_ai;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(190) NOT NULL UNIQUE,
  display_name VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_settings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE,
  theme VARCHAR(20) NOT NULL DEFAULT 'dark',
  memory_enabled BIT NOT NULL DEFAULT 1,
  default_provider_id BIGINT NULL,
  default_model VARCHAR(190) NULL,
  temperature DOUBLE NOT NULL DEFAULT 0.7,
  custom_instructions VARCHAR(2000) NULL,
  CONSTRAINT fk_settings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ai_provider_configs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  type VARCHAR(30) NOT NULL,
  base_url VARCHAR(300) NOT NULL,
  api_key_enc TEXT NULL,
  default_model VARCHAR(190) NULL,
  vision_model VARCHAR(190) NULL,
  code_model VARCHAR(190) NULL,
  preferred BIT NOT NULL DEFAULT 0,
  INDEX idx_provider_user (user_id),
  CONSTRAINT fk_provider_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS conversations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  mode VARCHAR(30) NOT NULL DEFAULT 'SMART',
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  INDEX idx_conv_user (user_id, updated_at),
  CONSTRAINT fk_conv_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  role VARCHAR(20) NOT NULL,
  content LONGTEXT NOT NULL,
  sources_json LONGTEXT NULL,
  file_ids VARCHAR(500) NULL,
  model_used VARCHAR(190) NULL,
  mode_used VARCHAR(30) NULL,
  created_at DATETIME(6) NOT NULL,
  INDEX idx_msg_conv (conversation_id),
  CONSTRAINT fk_msg_conv FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS uploaded_files (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  conversation_id BIGINT NULL,
  original_name VARCHAR(255) NOT NULL,
  stored_name VARCHAR(100) NOT NULL,
  content_type VARCHAR(100) NULL,
  kind VARCHAR(20) NOT NULL,
  size_bytes BIGINT NOT NULL,
  extracted_text LONGTEXT NULL,
  created_at DATETIME(6) NOT NULL,
  INDEX idx_file_user (user_id),
  INDEX idx_file_conv (conversation_id),
  CONSTRAINT fk_file_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS memory_records (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  content VARCHAR(1000) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  INDEX idx_mem_user (user_id),
  CONSTRAINT fk_mem_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
