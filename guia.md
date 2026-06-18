# Hermes Control Center — Guía de uso diario

> **Para qué es esta guía.** El Control Center es una app Java desktop (Windows) que pone una cara visible al stack Hermes: ves el estado, arrancas cosas con un clic, abres terminales reales, y escribes comandos en una consola con whitelist. Esta guía es para que la uses sin pelearte con la infraestructura.

---

## 1. ¿Qué es y para qué te sirve?

**Hermes Control Center** es una ventana de Windows (hecha en Java 21 + FlatLaf, tema Arc Dark) que:

- Te dice **en qué estado está el stack real** (WSL, Ubuntu, tmux, Hermes, Engram, OpenCode, plugins) sin tener que abrir 4 terminales.
- Tiene **un único botón principal contextual** que siempre hace lo correcto según el estado (entrar si existe sesión, crearla si no, iniciar el stack si todo está caído).
- Te lanza **terminales reales de WSL** (vía `wt.exe` si tienes Windows Terminal, o `cmd /c start` como fallback) — no procesos sin tty.
- Te da una **consola interactiva con whitelist** de comandos para no ejecutar lo que no toca.
- Muestra **diagnóstico técnico** en una pantalla secundaria.
- Guarda tu **configuración local** (`config.json`) sin tener que editar archivos a mano.

**En lenguaje humano:** es el "panel de control" de tu stack Hermes. Tú lo abres, ves qué pasa, le das al botón, y se hace lo que tenga que hacerse.

**Lo que NO hace:**

- No reemplaza a Hermes, a tmux, a Engram ni a opencode. Solo los orquesta visualmente.
- No agrega comandos nuevos. Solo dispara los scripts que ya existen en `E:\Dev\Hermes\scripts\`.
- No edita tu código. No es un IDE.

---

## 2. Requisitos

Antes de abrir la app, asegúrate de tener:

| Requisito           | Cómo verificar                                       |
| ------------------- | ---------------------------------------------------- |
| Windows 10/11 + WSL2 | `wsl --status` en PowerShell                       |
| Distro `Ubuntu-24.04` | `wsl -l -v` debe listarla                          |
| Usuario Linux `antony` | `wsl -d Ubuntu-24.04 -u antony whoami`           |
| Stack Hermes montado | `E:\Dev\Hermes` con `scripts\hermes-stack.sh` etc. |
| JDK 21              | `java -version` → debe decir `21.x.x`                |
| Windows Terminal (opcional) | `where.exe wt` — si no está, usa fallback `cmd`  |

Si todo está, la app corre. Si falta algo, **el splash te avisa con la fase exacta** que falló.

---

## 3. Cómo se construye y se ejecuta

### Compilar (solo si cambiaste código)

PowerShell:

```powershell
cd E:\Dev\Hermes\hermes-control-center
.\gradlew.bat build
```

Genera el fat JAR en:
```
E:\Dev\Hermes\hermes-control-center\build\libs\hermes-control-center-1.0.0.jar
```

### Ejecutar (lo que harás el 99% del tiempo)

**Opción A — desde JAR:**

```powershell
java -jar E:\Dev\Hermes\hermes-control-center\build\libs\hermes-control-center-1.0.0.jar
```

**Opción B — doble clic** sobre el `.jar` si tienes `javaw` asociado a `.jar`.

**Opción C — desde Gradle (modo dev):**

```powershell
cd E:\Dev\Hermes\hermes-control-center
.\gradlew.bat run
```

### Configuración de atajo rápido (opcional)

Crea un acceso directo de Windows que apunte a:
```
C:\Program Files\Eclipse Adoptium\jdk-21.x.x.x-hotspot\bin\javaw.exe -jar "E:\Dev\Hermes\hermes-control-center\build\libs\hermes-control-center-1.0.0.jar"
```

Ponlo en tu Startup de Windows y cada vez que inicies sesión lo tendrás arriba.

---

## 4. El Splash de arranque (las 10 fases)

Cuando abres la app, primero ves un splash con barra de progreso. Detrás, el `StartupController` corre este pipeline:

| Fase       | %   | Qué hace internamente                                                  |
| ---------- | --- | ----------------------------------------------------------------------- |
| **BOOT**      | 5%  | Carga `config.json` y construye el `ApplicationContext` (services, cache, state). |
| **CONFIG**    | 10% | Carga tu `config.json` o usa defaults si no existe.                     |
| **CACHE**     | 15% | Calienta `StartupCache`, `HealthSnapshotCache` (TTL 45s), `StackStateBuffer` (last-known-good). |
| **WSL**       | 30% | Prueba `wsl -d Ubuntu-24.04 -u antony -- echo ok`.                      |
| **TMUX**      | 40% | Prueba `tmux -V` dentro de WSL.                                         |
| **HERMES**    | 60% | Verifica que el binario de Hermes está accesible y los scripts existen. |
| **ENGRAM**    | 80% | Hace `curl http://127.0.0.1:7437/health` o chequea si el puerto está bound. |
| **PLUGINS**   | 90% | Sondea cada plugin del shell (`git`, `eza`, `btop`, `lazygit`, `zoxide`, `oh-my-posh`, etc.). |
| **SNAPSHOT**  | 95% | Construye el `StackSnapshot` inmutable que verá la Home.                |
| **READY**     | 100% | Stack inicializado. El splash se cierra y aparece `MainFrame`.          |

**Si una fase falla:**

- El splash se queda colgado en esa fase con mensaje rojo.
- Mira el log: `E:\Dev\Hermes\hermes-control-center\logs\hermes-control-center.log`
- Lo más común: WSL no responde, o algún plugin no está instalado. El log dice cuál.

---

## 5. La Home — tu pantalla principal

Cuando arranca la app ves tres zonas:

```
┌─────────────────────────────────────────────────┐
│ HERMES CONTROL CENTER                            │
│ Proyecto activo: SIGHA · X / 12 servicios …     │
├─────────────────────────────────────────────────┤
│ [12 tarjetas de plugin en grid 3x4]             │
│                                                  │
│        ┌──────────────────────────┐              │
│        │   BOTÓN PRINCIPAL        │  ← contextual
│        └──────────────────────────┘              │
├─────────────────────────────────────────────────┤
│ Consola interactiva  [input]  [▶ Ejecutar]      │
└─────────────────────────────────────────────────┘
```

### Las 12 tarjetas de plugin

Cada tarjeta es un componente del stack con su **botón de acción** (que dispara el mismo verbo de la consola):

| Tarjeta        | Acción que dispara   | Qué hace                                       |
| -------------- | -------------------- | ---------------------------------------------- |
| **Ubuntu 24.04** | `uname -a`         | Te muestra info del kernel.                    |
| **WSL2**         | `status`          | Reprobea el stack.                              |
| **Git**          | `open lazygit`    | Abre Lazygit en una terminal real.             |
| **Bat**          | `tail engram`     | Te taila el log de Engram.                      |
| **Eza**          | `open eza`        | Tree de `/mnt/e/Dev/Hermes`.                    |
| **Btop**         | `open btop`       | Abre Btop (monitor de sistema) en una terminal. |
| **Lazygit**      | `open lazygit`    | Igual que la tarjeta Git, abre Lazygit.         |
| **Zoxide**       | `open zoxide`     | Salta a `/mnt/e/Dev/Hermes` en una terminal.   |
| **Oh My Posh**   | `diagnose`        | Te muestra info de diagnóstico.                |
| **Engram**       | `start engram`    | Inicia el servicio de memoria (puerto 7437).   |
| **Hermes**       | `start stack`     | Arranca todo el stack (Hermes + Engram).       |
| **tmux**         | `attach hermes`   | Adjunta a la sesión tmux en una terminal real. |

**El color de la tarjeta te dice el estado en tiempo real:**

- 🟢 Verde = ONLINE / healthy
- 🟡 Amarillo = degradado / arrancando
- 🔴 Rojo = OFFLINE / caído
- ⚪ Gris = sin sondear todavía

### El botón principal contextual

El botón grande de la Home **cambia su texto según el estado**. Siempre hace lo correcto:

| Estado detectado                                  | Texto del botón       | Qué hace al pulsarlo                                                                                  |
| ------------------------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------ |
| La sesión tmux `hermes` ya existe                | **Entrar a Hermes**   | Abre una terminal real con `tmux attach -t hermes`.                                                   |
| Stack listo pero **no** hay sesión tmux           | **Crear sesión Hermes**| Ejecuta `hermes-tmux.sh` (los 4 paneles) y luego abre terminal con attach.                            |
| Engram o tmux caídos                              | **Iniciar stack**     | Lanza `hermes-start.sh` + `engram-start.sh`, reintenta y si todo va bien, queda listo para crear sesión. |

**En la práctica:** pulsas UNA vez al día ese botón y entras a trabajar. No tienes que pensar qué hay que arrancar.

---

## 6. La consola interactiva

Justo debajo de las tarjetas. Tiene un input field, un botón ▶ Ejecutar, historial con flechas y autocompletado con TAB.

### Verbos disponibles (whitelist)

Escribe uno de estos y dale Enter (o TAB para autocompletar):

| Verbo                | Tipo              | Qué hace                                                                   |
| -------------------- | ----------------- | -------------------------------------------------------------------------- |
| `status`             | Diagnóstico       | Muestra el snapshot actual del stack.                                      |
| `refresh`            | Diagnóstico       | Reprobea todos los servicios y refresca las tarjetas.                      |
| `start stack`        | Acción            | Inicia el stack completo (Hermes + Engram).                                |
| `start engram`       | Acción            | Inicia solo Engram.                                                        |
| `create session`     | Acción            | Crea la sesión tmux `hermes` con 4 paneles.                                |
| `attach hermes`      | Terminal real     | Abre `wt.exe` con `tmux attach -t hermes`.                                 |
| `open terminal`      | Terminal real     | Abre una terminal WSL limpia.                                              |
| `open lazygit`       | Terminal real     | Abre Lazygit en una terminal.                                              |
| `open btop`          | Terminal real     | Abre Btop.                                                                 |
| `open eza`           | Terminal real     | Tree del proyecto.                                                         |
| `open zoxide`        | Terminal real     | Zoxide (jump) al root.                                                     |
| `go to hermes`       | Terminal real     | `cd /mnt/e/Dev/Hermes` en una terminal.                                    |
| `diagnose`           | Diagnóstico       | `uname -a` del kernel Ubuntu.                                              |
| `tmux ls`            | Diagnóstico       | Lista sesiones tmux activas.                                               |
| `tmux -V`            | Diagnóstico       | Versión de tmux.                                                           |
| `uname -a`           | Diagnóstico       | Info de kernel.                                                            |
| `uptime`             | Diagnóstico       | Tiempo activo de Ubuntu.                                                   |
| `tail engram`        | Diagnóstico       | Tail del log de Engram.                                                    |
| `help`               | Diagnóstico       | Muestra la lista de verbos.                                                 |

### Atajos de la consola

- `↑` / `↓` — navegan por el historial.
- `TAB` — autocompleta el verbo.
- `Enter` — ejecuta.
- Si escribes algo fuera de la whitelist → mensaje de rechazo con sugerencia.

### Salida

Cada comando deja una línea en la consola con timestamp. Si la acción abre una terminal, también te dice "Terminal lanzada: ..." o "Console (cmd) — ...".

---

## 7. Pantalla de Diagnóstico

(Navega desde el menú lateral o la pestaña **Diagnóstico** del MainFrame.)

Muestra las mismas 12 tarjetas de la Home (sin botones de acción) más un **reporte textual** abajo con todo el detalle técnico.

Úsala cuando:

- Algo se ve raro en la Home.
- Necesitas ver el output crudo de un probe (`tmux ls`, `uname -a`, etc.).
- Vas a reportar un bug y necesitas datos.

Tiene un botón **"Refrescar diagnóstico"** abajo a la derecha que reprocesa todo.

---

## 8. Pantalla de Configuración

Aquí ves y editas el `config.json` que se persiste junto al JAR.

### Valores por defecto (los que aplica la primera vez)

```json
{
  "linuxUser": "antony",
  "wslDistro": "Ubuntu-24.04",
  "hermesRoot": "E:\\Dev\\Hermes",
  "tmuxScript": "/mnt/e/Dev/Hermes/scripts/hermes-tmux.sh",
  "hermesStartScript": "/mnt/e/Dev/Hermes/scripts/hermes-start.sh",
  "engramStartScript": "/mnt/e/Dev/Hermes/scripts/engram-start.sh",
  "terminalCommand": "wt",
  "engramPort": 7437
}
```

**Cuándo tocar:**

- `linuxUser` — si tu usuario Linux cambia.
- `wslDistro` — si renombras la distro.
- `hermesRoot` — si mueves la carpeta del proyecto.
- `*Script` — si renombras los `.sh` (no debería hacer falta).
- `terminalCommand` — si no tienes `wt.exe` y quieres forzar otro lanzador (p.ej. `"powershell"`).
- `engramPort` — si cambias el puerto de Engram (en `config/engram.env` también).

**No toques** estos valores a lo loco — la app asume que el stack está donde dice ahí.

---

## 9. Cómo te ayuda con Hermes (relación con el stack)

Piensa en el Control Center como un **mando a distancia** del stack:

```
┌─────────────────────┐
│  Hermes Control     │  ← Tú pulsas aquí
│  Center (Windows)   │
└──────────┬──────────┘
           │  dispara wsl.exe + scripts
           ▼
┌─────────────────────┐
│  WSL Ubuntu-24.04   │
│  ├─ tmux            │  ← vive aquí
│  │   └─ sesión      │
│  │      "hermes"    │
│  ├─ Hermes Agent    │
│  ├─ Engram :7437    │
│  └─ plugins, skills │
└─────────────────────┘
```

**Lo que ganas al usar la app en vez de la terminal pelada:**

| Sin Control Center                          | Con Control Center                                |
| ------------------------------------------- | ------------------------------------------------- |
| Abrir 4 terminales para ver el estado.     | Una sola ventana con 12 tarjetas de estado.       |
| Adivinar si Engram está vivo (`curl` manual). | Tarjeta "Engram" verde/rojo en tiempo real.    |
| Escribir `bash scripts/hermes-tmux.sh` a mano.| Pulsar el botón contextual "Crear sesión".       |
| Saber qué hace cada plugin requiere grep.  | Las 12 tarjetas con su acción explícita.          |
| Escribir comandos en bash sin protección.   | Whitelist de verbos. Si escribes basura, sugiere. |
| Recordar rutas de scripts.                 | Todo resuelto por la app.                          |
| Tocar `config.json` a ciegas.              | Pantalla de Config con campos editables.          |
| Perder 5 min en diagnóstico si algo falla. | Splash te dice exactamente la fase que falló.     |

---

## 10. Casos de uso reales (flujos típicos)

### Caso 1 — "Acabo de encender el PC y quiero trabajar"

1. Abro el Control Center (atajo o `.jar`).
2. Espero el splash. Si todo va bien, llego a la Home en unos segundos.
3. La tarjeta "tmux" está gris, "Engram" está gris/rojo. Es normal.
4. Pulso el botón grande **"Iniciar stack"**.
5. Espero 2-3 segundos. Las tarjetas se ponen verdes.
6. El botón cambia a **"Crear sesión Hermes"**. Lo pulso.
7. Se abre Windows Terminal con los 4 paneles de tmux.
8. Trabajo dentro del panel P1 (donde corre `hermes`).

### Caso 2 — "Quiero ver si algo se cayó"

1. Abro la consola de la Home.
2. Escribo `status` + Enter.
3. Veo el output de `tmux ls` y el estado de los probes.
4. Si algo está rojo, voy a la pestaña **Diagnóstico** y le doy a "Refrescar".

### Caso 3 — "Quiero abrir lazygit rápido"

1. Click en la tarjeta **Lazygit** (o **Git**, ambas disparan lo mismo).
2. Se abre Windows Terminal con `lazygit` corriendo dentro de WSL.

### Caso 4 — "Quiero ver el log de Engram en vivo"

1. Click en la tarjeta **Bat** (que dispara `tail engram`).
2. Aparece el output en la consola de la app.

O, si prefieres seguirlo en streaming:

1. Consola → `open terminal`.
2. En la terminal que se abre: `tail -F /mnt/e/Dev/Hermes/logs/engram.log`.

### Caso 5 — "Cambié de usuario Linux / moví la carpeta / roto algo"

1. Pestaña **Configuración** → ajusto `linuxUser` o `hermesRoot`.
2. Cierro y reabro el Control Center.
3. Splash vuelve a probar todo desde cero.

### Caso 6 — "Quiero reiniciar el stack desde cero"

1. Consola → `start stack`.
2. Si la sesión tmux estaba viva, primero la mato: desde la terminal que abro con `attach hermes`, hago `Ctrl+B` `D` para salir, luego `tmux kill-session -t hermes` desde otra terminal. O directamente cierro y reabro la app y uso el botón contextual.
3. Pulso **"Iniciar stack"** en la Home.

---

## 11. Logs y dónde mirar si algo se rompe

| Qué te pasa                                  | Dónde mirar                                                            |
| -------------------------------------------- | ----------------------------------------------------------------------- |
| El splash se queda colgado en una fase.      | `E:\Dev\Hermes\hermes-control-center\logs\hermes-control-center.log`   |
| Un comando de la consola no hace nada.       | Mismo log de arriba.                                                    |
| La terminal no se abre al pulsar una tarjeta.| Mira si tienes Windows Terminal (`where.exe wt`). Si no, instala WT o fuerza otro `terminalCommand` en Config. |
| Las tarjetas se quedan grises para siempre.  | El bootstrap falló. Mira el log y verifica WSL/distro/usuario.         |
| La app no abre (no levanta).                 | `java -version` debe ser 21.x. Si tienes varias JDK, fuerza la ruta.   |
| Output raro en la consola.                   | El log. Si es stacktrace, cópialo tal cual.                            |

**Truco para Windows Terminal:** si no lo tienes instalado:

```powershell
winget install --id Microsoft.WindowsTerminal
```

Después de eso, los `attach hermes`, `open btop`, etc. se abren en WT con su pestaña nativa.

---

## 12. Resumen de un vistazo

| Quiero…                                | Hago…                                                                  |
| -------------------------------------- | ---------------------------------------------------------------------- |
| Arrancar el Control Center             | Doble clic en el `.jar` o `java -jar ...hermes-control-center-1.0.0.jar` |
| Ver el estado de todo                  | Miro las 12 tarjetas de la Home.                                        |
| Entrar a la sesión tmux de Hermes      | Pulso el botón principal **"Entrar a Hermes"** (si ya existe sesión).   |
| Crear la sesión tmux                   | Pulso **"Crear sesión Hermes"** (si el stack está listo pero no hay sesión). |
| Arrancar todo el stack                 | Pulso **"Iniciar stack"** (si Engram o tmux están caídos).              |
| Abrir una herramienta (btop, lazygit, eza) | Click en la tarjeta correspondiente.                                |
| Ejecutar un comando seguro             | Escribo el verbo en la consola + Enter.                                |
| Ver ayuda de la consola                | Escribo `help`.                                                         |
| Editar la configuración                | Pestaña **Configuración** o edito `config.json` junto al JAR.          |
| Diagnosticar problema                  | Pestaña **Diagnóstico** + "Refrescar diagnóstico".                      |
| Ver el log de la app                   | `E:\Dev\Hermes\hermes-control-center\logs\hermes-control-center.log`    |
| Compilar si cambié el código           | `cd E:\Dev\Hermes\hermes-control-center && .\gradlew.bat build`         |

---

## 13. Lo que NO necesitas tocar

- `src/main/java/com/hermes/controlcenter/` — código fuente. Solo para debug.
- `build/` — artefactos de compilación.
- `gradle/`, `.gradle/`, `.idea/` — tooling.
- `config.json` — solo si cambias distro/usuario/ruta.

## 14. El modelo mental de 30 segundos

> "El Control Center es un panel de control de Windows que sabe si tu stack Hermes en WSL está vivo. Un botón contextual hace lo correcto siempre. Las 12 tarjetas son tus herramientas. La consola es un shell seguro. El splash te dice qué falla si algo está roto."

Si entiendes eso, ya lo sabes usar.
