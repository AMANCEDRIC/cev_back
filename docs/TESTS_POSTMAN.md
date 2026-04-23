# Documentation de test API (Quarkus + MySQL)

## 1) Prérequis

- Java 17+ et Maven installés.
- MySQL démarré et accessible.
- Backend démarré avec tes variables:
  - `DB_HOST=localhost`
  - `DB_PORT=3306`
  - `DB_NAME=cev_db`
  - `DB_USER=root`
  - `DB_PASSWORD=` (vide dans ton cas)

Commande recommandée:

```powershell
mvn quarkus:dev -Dquarkus.http.port=8082 -DDB_HOST=localhost -DDB_PORT=3306 -DDB_NAME=cev_db -DDB_USER=root "-DDB_PASSWORD="
```

Base URL de test:

- `http://localhost:8082`

Swagger:

- `http://localhost:8082/swagger-ui`

## 2) Ce que couvre la collection Postman

La collection couvre:

1. Vérification de santé (`/q/health`)
2. CRUD Templates
3. Émission de document
4. Récupération détail/document PDF
5. Vérification GET/POST
6. DataMatrix PNG
7. Révocation
8. Dashboard stats

## 3) Import Postman

1. Ouvre Postman.
2. Clique `Import`.
3. Sélectionne le fichier:
   - `postman/CEV-Backend.postman_collection.json`
4. Vérifie la variable de collection `baseUrl`:
   - `http://localhost:8082`

## 4) Variables de collection utilisées

- `baseUrl` : URL de l'API
- `xUserId` : identifiant métier pour l'en-tête `X-User-Id`
- `templateCode` : code du template de test
- `reference` : référence du document créé (auto-remplie par script Postman)
- `hash` : hash du document (auto-rempli par script Postman)

## 5) Ordre conseillé d'exécution

Exécute dans cet ordre pour un scénario complet:

1. `Health - GET /q/health`
2. `Templates - Create`
3. `Templates - List`
4. `Documents - Emit`
5. `Documents - Get By Reference`
6. `Documents - Download PDF`
7. `Verification - GET`
8. `Verification - POST`
9. `Verification - DataMatrix PNG`
10. `Dashboard - Stats`
11. `Documents - Revoke`
12. `Templates - Disable`

## 6) Valeurs valides importantes

### `typeDocument`

Valeurs possibles:

- `CERTIFICAT`
- `DIPLOME`
- `ATTESTATION`
- `FACTURE`
- `AUTRE`

### `sourceDonnees`

Valeurs possibles:

- `FORMULAIRE`
- `BASE_DONNEES`
- `API_EXTERNE`

## 7) Réponses attendues (résumé)

- Création template: `201`
- Émission document: `201`
- Vérification valide: `200`
- Vérification invalide: `422`
- Révocation: `200`
- Désactivation template: `204`

## 8) Dépannage rapide

### Erreur DB (Accès refusé `cev_user`)

Cause: mauvaises variables prises en compte.

Action:

- relancer avec `-DDB_USER=root "-DDB_PASSWORD="`
- vérifier que l'URL/port MySQL sont bons.

### Port déjà utilisé (8080/8081)

Action:

- forcer un port libre:
  - `-Dquarkus.http.port=8082`
- mettre à jour `baseUrl` dans Postman.

### Endpoint de vérification retourne 422

Cause: `hash` et `reference` ne correspondent pas.

Action:

- réutiliser `reference` + `hash` récupérés après `Documents - Emit`.

