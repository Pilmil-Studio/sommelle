# Guide utilisateur — Sommelle

Sommelle est un programme d'accompagnement à la thérapie comportementale et cognitive de l'insomnie (TCC-I), incluant un agenda de sommeil, le calcul de la fenêtre de sommeil, un mode nuit non éblouissant et des exercices audio hors-ligne.

---

## 1. Premier lancement et dépistage (`Onboarding`)

Un parcours en **5 étapes** sécurise la prise en charge avant de démarrer :
1. **Accueil** : présentation de la méthode TCC-I et du fonctionnement 100 % local.
2. **Tranche d'âge** (`AgeBand`) : adaptation des repères selon l'âge.
3. **Questionnaire de dépistage** (`ScreeningFlow` — 11 questions binaires) : identifie d'éventuelles contre-indications à la restriction du temps passé au lit (épilepsie, trouble bipolaire, apnée non traitée, travail posté, etc.) et affiche un renvoi vers le **3114** (numéro national de prévention du suicide) en cas de détresse déclarée.
4. **Orientation** : place l'utilisateur en protocole complet (`FULL`), sans restriction de sommeil (`WITHOUT_RESTRICTION`), ou hors champ (`OUT_OF_SCOPE`) avec conseil de consulter un professionnel.
5. **Rappels** : programmation des notifications locales matin/soir.

---

## 2. Navigation principale (4 onglets dans `SommelleApp`)

### 1. Accueil (`home`)
- Badge de la **semaine de programme** en cours.
- **Impact diurne** (`DaytimeImpactCard`) : suivi de la fatigue et de la somnolence en journée.
- **Fenêtre de sommeil** (`WindowCard`) : affiche les heures cibles de coucher et de lever (ou indique les **7 nuits d'observation** initiales avant le calcul de la première fenêtre).
- **Dernières nuits** (`RecentNightsStrip`) : aperçu rapide des saisies récentes.
- Actions directes : **« Noter ma nuit »** (`DiaryScreen`) et **« Je ne dors pas »** (`NightScreen`).

### 2. Ma semaine (`week`)
- Bilan hebdomadaire d'efficacité du sommeil :
  - Efficacité **>= 85 %** : élargissement progressif de la fenêtre (**+15 min**).
  - Efficacité **< 80 %** : resserrement de la fenêtre (**-15 min**), avec plancher de sécurité (**5h30** ou **6h00** selon le profil).
- **Tableau comparatif** (`ComparisonTable`) et **graphique en barres horizontal** accessible.

### 3. Exercices (`cognitive`)
- **5 modules psycho-éducatifs et cognitifs** débloqués semaine après semaine.
- **Guidage audio hors-ligne** : lecture vocale locale ralentie (**0,86x**) et gong sonore à **432 Hz**.
- **Carnet de soucis du soir** : espace de décharge mentale avant le coucher pour ne pas ruminer au lit.

### 4. Réglages (`settings`)
- Configuration des **rappels** et de la plage horaire du **thème nuit**.
- Accès au **Bilan clinicien** (`ExportScreen`) : export **PDF A4** (`PdfReport`) et **CSV** (`sommelle-agenda.csv`), ainsi qu'**import CSV**.
- **Effacement complet** des données locales, raccourci d'aide **3114**, présentation de la suite Pilmil et page **À propos**.

---

## 3. Écrans dédiés

- **Agenda de sommeil (`DiaryScreen`)** : basé sur le *Consensus Sleep Diary (Core)*, avec deux modes au choix (**Résumé** rapide ou **Guidé** pas à pas) et validation de cohérence (`DiaryValidator`).
- **Mode Nuit / Contrôle du stimulus (`NightScreen` & `WakeTimeScreen`)** : activable par le bouton « Je ne dors pas » ; applique un thème ambre sur fond noir OLED (`0.05` de luminosité), masque l'horloge pour éviter l'anxiété liée à l'heure, et propose un rappel doux après **20 minutes** (cloche à 432 Hz) invitant à quitter le lit si le sommeil ne vient pas.
