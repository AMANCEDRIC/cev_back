--liquibase formatted sql

--changeset cev:1.0.1-template-import
--comment: Tables pour import de templates PDF + detection de champs

CREATE TABLE template_import (
    id                      CHAR(36)     NOT NULL DEFAULT (UUID()),
    nom                     VARCHAR(200),
    file_name               VARCHAR(300) NOT NULL,
    storage_path            VARCHAR(500) NOT NULL,
    status                  VARCHAR(50)  NOT NULL,
    detect_mode             VARCHAR(20)  NOT NULL,
    pages                   INT,
    error_message           TEXT,
    created_by              VARCHAR(100),
    published_template_code VARCHAR(100),
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE template_import_field (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    import_id     CHAR(36)     NOT NULL,
    original_name VARCHAR(120) NOT NULL,
    mapped_name   VARCHAR(120),
    required_flag TINYINT(1)   NOT NULL DEFAULT 0,
    source        VARCHAR(20)  NOT NULL,
    confidence    DECIMAL(5,4),
    PRIMARY KEY (id),
    CONSTRAINT fk_template_import_field_import
        FOREIGN KEY (import_id) REFERENCES template_import(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_template_import_status ON template_import(status);
CREATE INDEX idx_template_import_created_at ON template_import(created_at);
CREATE INDEX idx_template_import_field_import_id ON template_import_field(import_id);

--rollback DROP TABLE IF EXISTS template_import_field;
--rollback DROP TABLE IF EXISTS template_import;
