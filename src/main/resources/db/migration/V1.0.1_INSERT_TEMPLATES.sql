--liquibase formatted sql

--changeset cev:1.0.1-insert-templates
--comment: Insertion des templates de documents par défaut

INSERT INTO document_template (id, code, libelle, type_document, description, contenu_qute, champs_json)
VALUES (
    UUID(),
    'CERT-REUSSITE',
    'Certificat de réussite',
    'CERTIFICAT',
    'Certificat standard délivré après validation d''une formation',
    '<!DOCTYPE html>
<html lang="fr">
<head><meta charset="UTF-8">
<style>
  body { font-family: Arial, sans-serif; margin: 40px; color: #1a1a1a; }
  .header { text-align: center; border-bottom: 3px solid #1a2744; padding-bottom: 20px; margin-bottom: 30px; }
  .org { font-size: 13px; letter-spacing: 2px; color: #666; text-transform: uppercase; }
  .titre { font-size: 28px; font-weight: bold; color: #1a2744; margin: 10px 0; }
  .corps { text-align: center; margin: 30px 0; font-size: 16px; line-height: 2; }
  .beneficiaire { font-size: 22px; font-weight: bold; color: #1a2744; }
  .formation { font-style: italic; font-size: 18px; color: #333; }
  .mention { display: inline-block; background: #e8f0fe; padding: 4px 16px; border-radius: 20px; color: #1a2744; }
  .footer { display: flex; justify-content: space-between; align-items: flex-end; margin-top: 50px; border-top: 1px solid #eee; padding-top: 20px; }
  .ref { font-size: 11px; color: #999; font-family: monospace; }
  .signature { text-align: center; }
  .datamatrix img { width: 100px; height: 100px; }
  .scan-label { font-size: 9px; color: #aaa; text-align: center; margin-top: 4px; }
</style>
</head>
<body>
  <div class="header">
    <div class="org">{organisation}</div>
    <div class="titre">Certificat de Réussite</div>
  </div>
  <div class="corps">
    <p>Nous certifions que</p>
    <p class="beneficiaire">{prenom} {nom}</p>
    <p>a obtenu avec succès la formation</p>
    <p class="formation">{intitule_formation}</p>
    <p>avec la mention <span class="mention">{mention}</span></p>
    <p>Le {date_emission}</p>
  </div>
  <div class="footer">
    <div class="ref">Réf : {reference}</div>
    <div class="signature">
      <div>Le Directeur</div>
      <br/><br/>
      <div style="border-top:1px solid #333; width:150px; margin:auto;"></div>
    </div>
    <div>
      <div class="datamatrix">{datamatrix_placeholder}</div>
      <div class="scan-label">Scannez pour vérifier</div>
    </div>
  </div>
</body>
</html>',
    '[{"cle":"prenom","libelle":"Prénom","type":"TEXT","requis":true},{"cle":"nom","libelle":"Nom","type":"TEXT","requis":true},{"cle":"intitule_formation","libelle":"Intitulé de la formation","type":"TEXT","requis":true},{"cle":"mention","libelle":"Mention","type":"TEXT","requis":false,"defaut":"Satisfaisant"}]'
);

INSERT INTO document_template (id, code, libelle, type_document, description, contenu_qute, champs_json)
VALUES (
    UUID(),
    'ATTEST-ADMIN',
    'Attestation administrative',
    'ATTESTATION',
    'Attestation générique pour usage administratif',
    '<!DOCTYPE html>
<html lang="fr">
<head><meta charset="UTF-8">
<style>
  body { font-family: "Times New Roman", serif; margin: 60px; color: #1a1a1a; }
  .header { text-align: center; margin-bottom: 40px; }
  .org { font-size: 14px; letter-spacing: 2px; color: #555; text-transform: uppercase; }
  .titre { font-size: 24px; font-weight: bold; text-transform: uppercase; letter-spacing: 4px; margin: 16px 0; border: 2px solid #333; display: inline-block; padding: 8px 24px; }
  .corps { margin: 40px 0; font-size: 16px; line-height: 2.2; text-align: justify; }
  .beneficiaire { font-weight: bold; text-decoration: underline; }
  .footer { display: flex; justify-content: space-between; align-items: flex-end; margin-top: 60px; }
  .lieu-date { font-size: 14px; }
  .datamatrix img { width: 90px; height: 90px; }
  .scan-label { font-size: 9px; color: #aaa; text-align: center; }
  .ref { font-size: 10px; color: #aaa; font-family: monospace; }
</style>
</head>
<body>
  <div class="header">
    <div class="org">{organisation}</div>
    <br/>
    <div class="titre">Attestation</div>
  </div>
  <div class="corps">
    <p>Nous soussignés, certifions par la présente que :</p>
    <p style="text-align:center; font-size:18px;" class="beneficiaire">{prenom} {nom}</p>
    <br/>
    <p>{objet_attestation}</p>
    <br/>
    <p>La présente attestation est délivrée pour servir et valoir ce que de droit.</p>
  </div>
  <div class="footer">
    <div>
      <div class="lieu-date">Fait à {lieu}, le {date_emission}</div>
      <br/><br/>
      <div style="border-top:1px solid #333; width:200px;"></div>
      <div style="font-size:12px; color:#555;">Signature et cachet</div>
    </div>
    <div style="text-align:right;">
      <div class="datamatrix">{datamatrix_placeholder}</div>
      <div class="scan-label">Scannez pour vérifier</div>
      <div class="ref">Réf : {reference}</div>
    </div>
  </div>
</body>
</html>',
    '[{"cle":"prenom","libelle":"Prénom","type":"TEXT","requis":true},{"cle":"nom","libelle":"Nom","type":"TEXT","requis":true},{"cle":"objet_attestation","libelle":"Objet de l attestation","type":"TEXTAREA","requis":true},{"cle":"lieu","libelle":"Lieu d emission","type":"TEXT","requis":true,"defaut":"Abidjan"},{"cle":"date_emission","libelle":"Date","type":"DATE","requis":true}]'
);

--rollback DELETE FROM document_template WHERE code IN ('CERT-REUSSITE', 'ATTEST-ADMIN');
