# Hermes Control Center

Aplicación Java Desktop para Windows. Capa de UX sobre el stack Hermes ya operativo
(WSL Ubuntu 24.04, Hermes Agent, Engram, OpenCode, tmux).

## Qué hace

- Muestra el estado real del stack (WSL, Ubuntu, tmux, Hermes, Engram, OpenCode).
- Ofrece un único botón principal contextual:
  - **Entrar a Hermes** — si la sesión tmux ya existe.
  - **Crear sesión Hermes** — si el stack está listo pero no hay sesión.
  - **Iniciar stack** — si Hermes o Engram están caídos.
- Lanza el flujo real: `wsl -d Ubuntu-24.04 -u antony -- tmux attach -t hermes`.
- Crea la sesión si no existe con `hermes-tmux.sh` (4 paneles ya validados).
- Consola interactiva con input, historial, autocompletado y whitelist de comandos.
- Diagnóstico técnico en una pantalla secundaria.
- Configuración persistente en `config.json` local.
- Splash de arranque con fases, progreso y mensajes reales.

## Cambios recientes (refactor profundo)

- **`app/HermesControlCenterApp.main`**: 12 líneas. Solo Look & Feel, contexto, splash, bootstrap.
- **Paquete `domain/`** poblado con subpaquetes reales: `model`, `command`, `session`, `snapshot`, `state`.
- **`cache/`** con `StartupCache`, `HealthSnapshotCache` (TTL 45s) y `StackStateBuffer` (last-known-good).
- **`bootstrap/`** con `ApplicationContext`, `StackBootstrapService` y `StartupController` — orquesta
  BOOT → CONFIG → CACHE → WSL → TMUX → HERMES → ENGRAM → PLUGINS → SNAPSHOT → READY.
- **`controllers/`** con `HomeController`, `DiagnosticController`, `ConfigController`,
  `ConsoleController` y `PluginRegistry`. La UI solo habla con controllers.
- **`SplashFrame`** dedicada con barra de progreso, lista de fases y mensaje de fase.
- **`LiveConsolePanel` interactiva**: input field + botón Ejecutar + historial con ↑/↓ + autocompletado
  con TAB + sugerencia de comandos.
- **`TerminalLauncherService`** reescrito: usa `wt.exe` si está disponible, si no `cmd /c start`.
  `tmux attach` ya no se intenta ejecutar en un proceso no-tty; siempre se abre una terminal real.
- **`CommandCatalog`** ampliado con `CONSOLE_VERBS` (whitelist interactiva): `status`, `refresh`,
  `start stack`, `create session`, `attach hermes`, `open terminal`, `open lazygit`, `open btop`,
  `open eza`, `open zoxide`, `go to hermes`, `tmux ls`, `tmux -V`, `uname -a`, `uptime`,
  `tail engram`, `help`.
- **`HomePanel`** con plugins reales: cada tarjeta tiene un action verb contextual que se
  dispatcha por el mismo flujo que la consola.

## Requisitos

- Windows 10/11 con WSL2
- Distro `Ubuntu-24.04` instalada
- Usuario Linux: `antony`
- JDK 21 (Eclipse Adoptium 21.0.7.6 o equivalente)
- Stack Hermes en `/mnt/e/Dev/Hermes` (Windows: `E:\Dev\Hermes`)

## Estructura

```
hermes-control-center/
├── build.gradle
├── settings.gradle
├── gradlew / gradlew.bat
├── logs/
└── src/main/
    ├── java/com/hermes/controlcenter/
    │   ├── app/        HermesControlCenterApp (mínimo), MainFrame
    │   ├── ui/         HomePanel, DiagnosticPanel, ConfigPanel, SplashFrame,
    │   │               LiveConsolePanel, StatusCard
    │   ├── controllers/ HomeController, DiagnosticController, ConfigController,
    │   │               ConsoleController, PluginRegistry
    │   ├── domain/     (poblado)
    │   │   ├── model/      AppConfig, ServiceHealth, ServiceStatus,
    │   │   │               StackSnapshot, DiagnosticSnapshot, CommandSpec,
    │   │   │               LiveConsole, PrimaryAction
    │   │   ├── command/    CommandRequest, CommandResult, ConsoleEvent
    │   │   ├── session/    HermesSessionState, PluginDescriptor
    │   │   ├── snapshot/   StartupPhase, StartupProgress
    │   │   └── state/      AppState
    │   ├── bootstrap/  ApplicationContext, StackBootstrapService, StartupController
    │   ├── cache/      StartupCache, HealthSnapshotCache, StackStateBuffer
    │   ├── services/   WslService, TmuxService, HermesService, EngramService,
    │   │               OpenCodeService, TerminalLauncherService,
    │   │               HealthCheckService, PluginStatusService, StackFlowService
    │   ├── infrastructure/  CommandExecutor, CommandCatalog (con verb whitelist)
    │   ├── config/     ConfigStore
    │   └── utils/      StatusColors
    └── resources/
        └── logback.xml
```

## Construir

```powershell
cd E:\Dev\Hermes\hermes-control-center
.\gradlew.bat build
```

Genera: `build/libs/hermes-control-center-1.0.0.jar` (fat JAR, runnable).

## Ejecutar

```powershell
java -jar build\libs\hermes-control-center-1.0.0.jar
```

O doble clic sobre el JAR si el sistema tiene `javaw` asociado.

## Flujo de arranque

1. `HermesControlCenterApp.main` configura Look & Feel.
2. Construye `ApplicationContext` (services, cache, state).
3. Muestra `SplashFrame` con "Inicializando stack…".
4. `StartupController` corre el pipeline:
   - BOOT (5%) — Construyendo contexto
   - CONFIG (10%) — Cargando config.json
   - CACHE (15%) — Calentando cache de arranque
   - WSL (30%) — Probando WSL2 y Ubuntu
   - TMUX (40%) — Probando tmux server
   - HERMES (60%) — Probando Hermes Agent
   - ENGRAM (80%) — Probando Engram HTTP
   - PLUGINS (90%) — Probando plugins del shell
   - SNAPSHOT (95%) — Construyendo snapshot
   - READY (100%) — Stack inicializado
5. Splash se cierra y aparece `MainFrame` con Home + Consola + botón contextual.

## Consola interactiva

- Escribe `help` y Enter para ver la whitelist.
- Escribe `status` o `refresh` para ver el estado actual.
- Escribe `start stack` para iniciar Hermes + Engram.
- Escribe `attach hermes` para abrir la terminal real con tmux attach.
- `↑/↓` navegan por el historial.
- `TAB` autocompleta.
- Comandos no listados se rechazan con sugerencia.

## Logs

- Archivo: `logs/hermes-control-center.log`
- Consola: nivel INFO
- Cada fase del bootstrap deja traza con porcentaje y mensaje.

## Configuración

- Persistida en `config.json` (mismo directorio del JAR).
- Editable desde la pantalla **Configuración** o directamente en el JSON.
- Valores por defecto:
  - Usuario Linux: `antony`
  - Distro WSL: `Ubuntu-24.04`
  - Ruta Hermes: `E:\Dev\Hermes`
  - Script tmux: `/mnt/e/Dev/Hermes/scripts/hermes-tmux.sh`
  - Script hermes-start: `/mnt/e/Dev/Hermes/scripts/hermes-start.sh`
  - Script engram-start: `/mnt/e/Dev/Hermes/scripts/engram-start.sh`
  - Comando de terminal: `wt` (Windows Terminal). Si no está disponible cae a `cmd /c start`.

## Decisiones de diseño

- **Swing + FlatLaf (Arc Dark)**: ligero, nativo, portable, sin WebView.
- **Java 21**: estabilidad LTS, records, pattern matching.
- **MVC real**: Home/Diagnostic/Config/Console controllers desacoplan UI y servicios.
- **CommandExecutor único**: ningún botón, panel o servicio dispara procesos por su cuenta.
- **CommandCatalog**: whitelist explícita, ningún comando se construye fuera de aquí.
- **HealthSnapshotCache**: TTL 45s, evita parpadeos entre refrescos.
- **StackStateBuffer**: per-key last-known-good, render coherente durante cargas.
- **StartupController**: pipeline asíncrono con fases y progreso, UI nunca se bloquea.
- **TerminalLauncherService**: `wt.exe` preferido, `cmd /c start` fallback. `tmux attach`
  SIEMPRE abre terminal real, nunca `inheritIO` en proceso no-tty.
- **Snapshot inmutable**: `StackSnapshot` se renderiza igual en Home y Diagnóstico.

## Garantías

- No reemplaza Hermes, tmux, Engram ni OpenCode.
- No agrega comandos nuevos fuera del whitelist.
- No crea scripts nuevos.
- No usa JavaFX, Electron, React, WebView ni navegador embebido.
- La Home nunca aparece con tarjetas falsas "OFFLINE" durante el bootstrap.
- `tmux attach -t hermes` nunca se ejecuta como proceso sin tty.
# hermes-control-center
