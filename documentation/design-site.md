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
9. Tableaux du dossier : `display:block; overflow-x:auto` rendent le défilement local ; `scrollWidth` du document = 360 à 360 px (aucun débordement de page). Cellules d'en-tête étroites (4 lignes pour « Durée d'observation avant toute restriction » à 360 px).
10. Pas de variables d'espacement, de typographie ni d'ombres : 12 tailles de police, 14 valeurs de padding/margin et 3 ombres écrites en dur ; styles en ligne de 12 px de marge répétés sur les cartes (`style="margin-top: 12px"`).
