--liquibase formatted sql

--changeset cev:1.0.0-start
--comment: Création du schéma initial CEV — tables principales

SET NAMES utf8mb4;

-- =====================================================
-- TABLE : templates de documents
-- =====================================================
CREATE TABLE document_template (
    id              CHAR(36)     NOT NULL DEFAULT (UUID()),
    code            VARCHAR(50)  NOT NULL,
    libelle         VARCHAR(200) NOT NULL,
    type_document   ENUM('CERTIFICAT','DIPLOME','ATTESTATION','FACTURE','AUTRE')
                                 NOT NULL DEFAULT 'CERTIFICAT',
    description     TEXT,
    contenu_qute    TEXT         NOT NULL,
    champs_json     JSON         NOT NULL,
    actif           TINYINT(1)   NOT NULL DEFAULT 1,
    cree_le         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modifie_le      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- TABLE : documents émis
-- =====================================================
CREATE TABLE document_emis (
    id                  CHAR(36)     NOT NULL DEFAULT (UUID()),
    reference           VARCHAR(100) NOT NULL,
    template_id         CHAR(36)     NOT NULL,
    type_document       ENUM('CERTIFICAT','DIPLOME','ATTESTATION','FACTURE','AUTRE') NOT NULL,
    source_donnees      ENUM('FORMULAIRE','BASE_DONNEES','API_EXTERNE') NOT NULL,

    beneficiaire_nom    VARCHAR(200) NOT NULL,
    beneficiaire_prenom VARCHAR(200),
    beneficiaire_email  VARCHAR(200),
    beneficiaire_meta   JSON,

    donnees_json        JSON         NOT NULL,

    statut              ENUM('BROUILLON','EN_ATTENTE_SIGNATURE','SIGNE','ENVOYE','ARCHIVE','REVOQUE')
                                     NOT NULL DEFAULT 'BROUILLON',

    hash_signature      VARCHAR(512),
    datamatrix_payload  TEXT,
    signe_le            DATETIME,
    signe_par           VARCHAR(100) DEFAULT 'CEV-AUTO',

    pdf_path            VARCHAR(500),
    pdf_nom_fichier     VARCHAR(200),

    date_emission       DATE         NOT NULL DEFAULT (CURRENT_DATE),
    date_expiration     DATE,
    cree_le             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modifie_le          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    cree_par            VARCHAR(100),

    PRIMARY KEY (id),
    UNIQUE KEY uk_document_reference (reference),
    CONSTRAINT fk_doc_template
        FOREIGN KEY (template_id) REFERENCES document_template(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- TABLE : historique des actions
-- =====================================================
CREATE TABLE document_historique (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    document_id     CHAR(36)     NOT NULL,
    action          VARCHAR(100) NOT NULL,
    detail          TEXT,
    effectue_par    VARCHAR(100),
    effectue_le     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    meta_json       JSON,
    PRIMARY KEY (id),
    CONSTRAINT fk_hist_document
        FOREIGN KEY (document_id) REFERENCES document_emis(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- TABLE : envois email
-- =====================================================
CREATE TABLE document_envoi (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    document_id     CHAR(36)     NOT NULL,
    destinataire    VARCHAR(200) NOT NULL,
    objet           VARCHAR(300),
    statut          VARCHAR(50)  NOT NULL DEFAULT 'EN_ATTENTE',
    envoye_le       DATETIME,
    erreur          TEXT,
    PRIMARY KEY (id),
    CONSTRAINT fk_envoi_document
        FOREIGN KEY (document_id) REFERENCES document_emis(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- TABLE : vérifications DataMatrix
-- =====================================================
CREATE TABLE document_verification (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    reference       VARCHAR(100),
    hash_soumis     VARCHAR(512),
    resultat        TINYINT(1)   NOT NULL,
    detail          TEXT,
    verifie_le      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_source       VARCHAR(50),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- INDEX
-- =====================================================
CREATE INDEX idx_document_emis_statut    ON document_emis(statut);
CREATE INDEX idx_document_emis_template  ON document_emis(template_id);
CREATE INDEX idx_document_emis_email     ON document_emis(beneficiaire_email);
CREATE INDEX idx_historique_document     ON document_historique(document_id);
CREATE INDEX idx_verification_reference  ON document_verification(reference);

--rollback DROP TABLE IF EXISTS document_verification;
--rollback DROP TABLE IF EXISTS document_envoi;
--rollback DROP TABLE IF EXISTS document_historique;
--rollback DROP TABLE IF EXISTS document_emis;
--rollback DROP TABLE IF EXISTS document_template;
