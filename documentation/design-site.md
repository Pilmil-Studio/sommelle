# Diagnostic du site (novembre 2026)

Mesures faites sur `docs/style.css` et les dix pages (cinq FR, cinq EN), Chrome headless, 1280 px et 360 px, thèmes clair et sombre.

Constats avant reprise :
1. Thème sombre : l'en-tête, le bandeau d'accueil et le pied de page restent bleu nuit (#2F3A6B, #232C55) alors que le corps passe sur le brun de l'appli (#120D0A, #1E1611). Le changement de teinte se produit à la limite du bandeau d'accueil.
2. Boutons d'accueil : trois styles (plein ambré à 60 % d'opacité, deux contours crème), trois boutons de 233, 207 et 230 px de large sur trois lignes à 1280 px (tops 587, 587, 655) et à 360 px. Le bouton « bientôt » (non utilisable) est le seul plein ; son contraste texte/fond est de 3,98:1 à cause de l'opacité (7,31:1 sans).
3. Le logo Pilmil du pied de page porte `style="vertical-align:-3px;margin-right:6px"` sur les dix pages.
4. Captures du bandeau d'accueil : attributs 461 x 1000 (ratio 0,461), mais `border: 6px` avec `box-sizing: border-box` et `width:100%` déforme le ratio rendu (2 images concernées par page d'accueil à 360 px).
5. Focus clavier : contour ambre #E8A33D sur fond crème #F6F3EE = 1,95:1 en thème clair (3:1 attendu pour un contour).
6. Schémas du dossier : texte `.in` crème (#FFFDF9) sur le bleu clair du thème sombre (#8C9BD9) = 2,65:1 ; texte `.in2` sur la zone « s'allonge » = 2,22:1 (clair) et 1,60:1 (sombre) ; sur la zone « raccourcit » 3,16:1 (sombre).
7. Zones tactiles sous 44 px : liens de navigation 35 px de haut, liens du pied de page 26 px, liens « Découvrir … » 24 px, bouton « Mode nuit » du simulateur 20 px, onglets du simulateur 42 px, liens du sommaire du dossier 24 px.
8. Longueur de ligne : texte courant du dossier protocole limité à 72ch (environ 93 caractères par ligne à 1280 px d'après la largeur moyenne mesurée) ; titres h1 d'accueil sur 4 lignes à 1280 px et 5 lignes à 360 px ; aucun `text-wrap: balance`.
9. Tableaux du dossier : `display:block; overflow-x:auto` rendent le défilement local ; `scrollWidth` du document mesuré à 360 px dans un cadre de 360 px, sans barre de défilement : aucun débordement de page. La première colonne « Durée d'observation avant toute restriction » passe sur 4 lignes à 1280 px (largeur de colonne réduite par `display:block`).
10. Pas d'échelle : 15 valeurs de `font-size` distinctes, 18 déclarations `padding` distinctes, 7 rayons en pixels (3, 6, 18, 20, 26 et 28 via `--radius`), 1 seule ombre en dur ; seuls les couleurs de base et `--radius` sont des variables. Le style en ligne `margin-top: 12px` est répété sur 7 paragraphes de la page d'accueil.

Constat supplémentaire relevé pendant la reprise : la classe `.note` (couleur claire prévue pour le bandeau d'accueil, #D9DCEB) était aussi appliquée aux notes des pages `pourquoi` (fond crème), soit environ 1,3:1.

## Après reprise

1. Corrigé : variables `--band`, `--band-deep`, `--on-band` ; en thème sombre l'en-tête, le bandeau d'accueil et le pied de page prennent le brun de l'appli (#2A2019, #0B0806). Thème clair inchangé (bleu nuit).
2. Corrigé : un seul bouton plein (APK, ambre), « Google Play · bientôt » en contour pointillé sans plein, « Comment ça marche » en contour ; trois boutons de 48 px, sur une ligne à 1280 px, pleine largeur à 360 px (les classes des deux premiers sont échangées dans index.html et en/index.html, texte inchangé).
3. Corrigé : classe `.footer-logo` sur les dix pages.
4. Corrigé : cadre des captures en `box-shadow` ; mesure : 0 image dont le ratio rendu diffère de celui des attributs, sur les dix pages.
5. Corrigé : contour de focus `--focus` (bleu nuit 9,79:1 en clair, ambre 8,95:1 en sombre ; lune #F2C27B sur les bandeaux, 6,59:1).
6. Corrigé : texte `.in` en sombre #120D0A sur #8C9BD9 (7,18:1) ; `.in2` « s'allonge » crème en clair, sombre en thème sombre (5,47:1) ; « raccourcit » crème en sombre (6,87:1).
7. Corrigé pour les liens isolés (navigation, pied de page, liens « Découvrir », sommaire, sources, fichiers de code, bouton « Mode nuit » et onglets du simulateur) : 44 px minimum. Non corrigé : les liens à l'intérieur d'une phrase (22 à 24 px de haut).
8. Corrigé : colonne de texte à 37em (environ 70 caractères) ; h1 d'accueil sur 3 lignes à 1280 px ; `text-wrap: balance` sur les titres. Le h1 reste sur 4 lignes à 360 px.
9. Sans changement nécessaire : aucun défilement de page ; la première colonne des tableaux n'a pas été retouchée.
10. Corrigé : échelles `--step-*`, `--space-*`, rayons `--radius-s/m`, `--radius`, `--shadow-s/m` ; `.note` limité au bandeau d'accueil. Non corrigé : les `style="margin-top: 12px"` et les styles du simulateur restent en ligne.

Vérifications : `scrollWidth` = 360 sur les dix pages à 360 px (cadre de 360 px) ; tous les `href` et `src` locaux existent.
