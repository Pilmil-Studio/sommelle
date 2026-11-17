# Spécification technique — Analyse locale et explicable de l'agenda (`sommelle`)

> Statut : Spécification de référence (15 novembre 2026).
> Objectif : Fournir à l'utilisateur une lecture personnalisée et transparente de ses facteurs de sommeil et des ajustements de fenêtre TCC-I, **sans aucun cloud** et sans modèle opaque.

---

## 1. Principes de sobriété et de rigueur clinique

- **Explicabilité avant tout** : en TCC-I, un patient n'adhère à une consigne (ex. reculer l'heure de coucher ou stabiliser le lever) que s'il comprend *pourquoi* elle est proposée à partir de ses propres nuits.
- **Zéro réseau, zéro profilage** : tous les calculs s'exécutent localement sur les entrées de l'agenda stockées sur l'appareil et exportables en JSON/PDF.
- **Pas de réseau de neurones surdimensionné** : sur 14 à 90 nuits par personne, des méthodes statistiques robustes (régressions régularisées, arbres de décision peu profonds, estimation bayésienne séquentielle) sont plus fiables, plus stables et 100 % interprétables qu'un réseau profond.

---

## 2. Fonctionnalités cibles

### 2.1 Détection locale des facteurs corrélés aux nuits difficiles
À partir d'un minimum de **14 nuits saisies**, le moteur calcule des corrélations simples et pondérées sur les variables déjà présentes ou saisies en option dans l'agenda :
- Régularité de l'heure de lever (écart-type sur 7 jours glissants) ;
- Temps passé au lit éveillé (WASO / latence d'endormissement) ;
- Durée de sieste, heure de dernière caféine/écran/activité tardive (si renseignées).

Restitution sobre dans l'interface, uniquement lorsque le signal dépasse un seuil statistique minimal (pour éviter les fausses corrélations sur trop peu de points) :
> *« Sur vos 21 dernières nuits, la latence d'endormissement dépasse 40 min dans 4 cas sur 5 lorsque l'heure de lever varie de plus de 90 min par rapport à votre moyenne. »*

### 2.2 Aide explicable à l'ajustement de la fenêtre de restriction (TCC-I)
- Calcul hebdomadaire de l'**efficacité du sommeil** (`temps dormi / temps passé au lit`).
- Proposition argumentée d'extension (+15 min), de maintien ou de resserrement (-15 min, plancher de 6 h, identique à celui du protocole), en affichant les 7 dernières nuits ayant motivé la recommandation.
- **Garde-fou** : l'utilisateur valide toujours manuellement la nouvelle fenêtre ; l'application ne l'impose jamais silencieusement.

### 2.3 Prévention du biais de récence
- Lissage exponentiel ou fenêtre glissante pondérée pour éviter qu'une seule mauvaise nuit isolée ne fasse basculer les indicateurs.

---

## 3. Architecture technique proposée

- **Emplacement** : module Kotlin pur dans `:shared` (compatible Android et iOS, 100 % testable par tests unitaires déterministes).
- **Entrée** : liste des `SleepEntry` des `N` derniers jours.
- **Sortie** : objet `SleepInsightsReport` contenant :
  - `efficiencyTrend` (évolution hebdomadaire) ;
  - `topFactors` (liste bornée à 2–3 observations factuelles avec effectifs `k/n`, jamais de jargon opaque) ;
  - `windowSuggestion` (`Expand`, `Keep`, `Tighten`, avec justification chiffrée).
- **Tests** : jeux synthétiques de 14, 30 et 90 jours couvrant insomnie d'endormissement, réveils nocturnes, variabilité week-end et amélioration progressive sous restriction.
