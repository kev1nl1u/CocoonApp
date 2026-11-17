# Cocoon

App Android per limitare le app che distraggono.

- **Tempo massimo** per app, al giorno o per fascia oraria.
- **Sessioni**: a ogni apertura scegli quanto usarla, oppure fissa una durata e la sessione parte da sola. Il tempo scorre solo con l'app in primo piano.
- **Durate precise**: ogni durata si inserisce in ore e minuti con un tocco sul valore.
- **Pausa** obbligatoria a fine sessione.
- **Fascia oraria** opzionale: fuori fascia l'app è libera. Scegli se il tempo massimo conta solo in fascia o tutto il giorno.
- **Tempo rimasto** in una notifica durante la sessione, più **avviso** N minuti prima della fine.
- **Modalità**: *Standard* (sblocco di 5 min dopo un'attesa che raddoppia a ogni uso) o *Max Focus* (nessuno sblocco, regole non modificabili durante il blocco).

Nessun account, nessuna rete: tutto resta sul telefono.

## Installazione

1. Scarica `cocoon.apk` dalla release [latest](../../releases/latest).
2. Apri Cocoon → **Attiva il rilevamento app** → Accessibilità → Cocoon.
   Da Android 13, se la voce non è selezionabile: Info app di Cocoon → ⋮ → *Consenti impostazioni con limitazioni*.
3. Consenti le notifiche per ricevere l'avviso di fine sessione.

### Xiaomi HyperOS

Cocoon riconosce HyperOS e mostra questi passi in home.

1. **Avvio automatico** → attivo. È il passo indispensabile: la card *Attiva l'avvio automatico* resta
   finché è spento e apre direttamente la schermata (Sicurezza → Avvio automatico → Cocoon).
2. In Info app di Cocoon: *Altre autorizzazioni* → finestre pop-up durante l'esecuzione in background → *Consenti sempre*.
3. In Info app di Cocoon: *Risparmio energetico* → *Nessuna restrizione*.

Quando togli Cocoon dalle app recenti (swipe o pulisci tutto), HyperOS chiude l'intero processo, servizio di
accessibilità compreso; il lucchetto sulla card non lo evita. Con l'avvio automatico attivo il sistema riavvia il
servizio in circa un secondo. Senza, il servizio resta fermo e in Accessibilità compare
*Questo servizio non funziona correttamente*: attiva l'avvio automatico, poi disattiva e riattiva Cocoon in Accessibilità.

Dopo un aggiornamento HyperOS può spegnere Cocoon in Accessibilità: la card *Attiva il rilevamento app* lo segnala.

## Build

```
./gradlew testDebugUnitTest assembleRelease
```

Ogni push su `main` compila l'APK con GitHub Actions e lo pubblica nella release `latest`.

Per poter aggiornare l'app senza disinstallarla serve una chiave di firma stabile: aggiungi i secret
`KEYSTORE_BASE64` (keystore in base64, alias `cocoon`) e `KEYSTORE_PASSWORD` al repository. Senza, ogni build usa una chiave usa e getta.

## Struttura

- `core/Logic.kt` – regole (budget, sessioni, pause, fasce), pure e testate.
- `core/GuardService.kt` – servizio di accessibilità: rileva l'app in primo piano e conta il tempo.
- `ui/GateActivity.kt` – schermata mostrata davanti a un'app limitata.
- `ui/*Screen.kt` – elenco, scelta app, impostazioni della regola.
