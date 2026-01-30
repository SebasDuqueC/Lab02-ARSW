# Snake Race — ARSW Lab #2 (Java 21, Virtual Threads)

**Escuela Colombiana de Ingeniería – Arquitecturas de Software**  
Laboratorio de programación concurrente: condiciones de carrera, sincronización y colecciones seguras.

---

## Requisitos

- **JDK 21** (Temurin recomendado)
- **Maven 3.9+**
- SO: Windows, macOS o Linux

---

## Cómo ejecutar

```bash
mvn clean verify
mvn -q -DskipTests exec:java -Dsnakes=4
```

- `-Dsnakes=N` → inicia el juego con **N** serpientes (por defecto 2).
- **Controles**:
  - **Flechas**: serpiente **0** (Jugador 1).
  - **WASD**: serpiente **1** (si existe).
  - **Espacio** o botón **Action**: Pausar / Reanudar.

---

## Reglas del juego (resumen)

- **N serpientes** corren de forma autónoma (cada una en su propio hilo).
- **Ratones**: al comer uno, la serpiente **crece** y aparece un **nuevo obstáculo**.
- **Obstáculos**: si la cabeza entra en un obstáculo hay **rebote**.
- **Teletransportadores** (flechas rojas): entrar por uno te **saca por su par**.
- **Rayos (Turbo)**: al pisarlos, la serpiente obtiene **velocidad aumentada** temporal.
- Movimiento con **wrap-around** (el tablero “se repite” en los bordes).

---

## Arquitectura (carpetas)

```
co.eci.snake
├─ app/                 # Bootstrap de la aplicación (Main)
├─ core/                # Dominio: Board, Snake, Direction, Position
├─ core/engine/         # GameClock (ticks, Pausa/Reanudar)
├─ concurrency/         # SnakeRunner (lógica por serpiente con virtual threads)
└─ ui/legacy/           # UI estilo legado (Swing) con grilla y botón Action
```

---

# Actividades del laboratorio

## Parte I — (Calentamiento) `wait/notify` en un programa multi-hilo

1. Toma el programa [**PrimeFinder**](https://github.com/ARSW-ECI/wait-notify-excercise).
2. Modifícalo para que **cada _t_ milisegundos**:
   - Se **pausen** todos los hilos trabajadores.
   - Se **muestre** cuántos números primos se han encontrado.
   - El programa **espere ENTER** para **reanudar**.
3. La sincronización debe usar **`synchronized`**, **`wait()`**, **`notify()` / `notifyAll()`** sobre el **mismo monitor** (sin _busy-waiting_).
4. Entrega en el reporte de laboratorio **las observaciones y/o comentarios** explicando tu diseño de sincronización (qué lock, qué condición, cómo evitas _lost wakeups_).

> Objetivo didáctico: practicar suspensión/continuación **sin** espera activa y consolidar el modelo de monitores en Java.

respuestas:
---

## Observaciones y comentarios

1. Se implementó un objeto lock compartido entre la clase Control y todos los hilos PrimeFinderThread. Este objeto actúa como monitor para coordinar la sincronización entre el hilo principal y los hilos trabajadores. El lock se crea en el constructor de Control y se pasa como parámetro a cada PrimeFinderThread durante su inicialización.

La condición que determina si un hilo debe pausarse es la variable booleana paused. Esta variable se declaró como volatile para garantizar visibilidad entre hilos y evitar que los cambios realizados por un hilo no sean vistos por otros debido a la caché de CPU.

Cada hilo trabajador verifica esta condición en cada iteración del ciclo principal:

![Definición del ciclo de vida de un hilo](img/Parte_1_img_1.png)

Para evitar lost wakeups se implementaron varias estrategias:

Uso de while en lugar de if: La condición paused se evalúa con un while y no con un if. Esto asegura que si un hilo despierta por cualquier razón (spurious wakeup), vuelva a verificar la condición antes de continuar.

Sincronización atómica en resume: El método resumeThreads() usa un bloque synchronized para cambiar el estado de paused a false y hacer notifyAll() de forma atómica:

![Definición del ciclo de vida de un hilo](img/Parte_1_img_1.png)

Uso de notifyAll() en lugar de notify(): Se utiliza notifyAll() para despertar a todos los hilos en espera, no solo a uno. Esto asegura que todos los hilos trabajadores reciban la señal de continuar.

Variable volatile: Aunque el acceso a paused dentro del wait se hace en un bloque sincronizado, declararla como volatile proporciona una capa adicional de seguridad para lecturas fuera del bloque sincronizado.

---

## Parte II — SnakeRace concurrente (núcleo del laboratorio)

### 1) Análisis de concurrencia

## 1. ¿Cómo funcionan las serpientes por su cuenta?

Al revisar el código, nos dimos cuenta de que cada serpiente se mueve sola porque tiene su propio "cerebro" (hilo). Esto no lo maneja la pantalla principal, sino que cada una va por su lado.

En el archivo `SnakeApp.java`, vimos esto:

1.  **El jefe de los hilos:** Usan una cosa llamada `newVirtualThreadPerTaskExecutor`. Es algo nuevo de Java 21 que crea hilos súper ligeros y rápidos.
2.  **Repartiendo trabajo:** Por cada serpiente que hay en el juego, crean un `SnakeRunner` y lo ponen a correr en uno de esos hilos.
    ```java
    // En SnakeApp.java
    var exec = Executors.newVirtualThreadPerTaskExecutor();
    snakes.forEach(s -> exec.submit(new SnakeRunner(s, board)));
    ```
3.  **Vida propia:** Cada `SnakeRunner` tiene un ciclo infinito (`while`) donde decide si gira, se mueve un paso y luego descansa un ratico (`Thread.sleep`). Así es como cada una cobra vida independientemente.

**En resumen:** Cada culebrita tiene su propio hilo pensando y moviéndose, sin importarle lo que hagan las demás o si la pantalla se está dibujando.

## 2. Problemas que encontramos

Mirando bien el código, vimos algunas cosas que pueden hacer que el juego falle o se comporte raro cuando hay muchas serpientes moviéndose a la vez.

### A. Condiciones de Carrera (El choque de trenes)

Hay un problema  en la clase `Snake`.

*   **¿Qué pasa?** Está la clase `Snake` que dos partes del programa intentan usar al tiempo.
    *   Uno es el hilo de la serpiente (`SnakeRunner`), que todo el tiempo le dice "crece", "muévete" (modifica la lista del cuerpo).
    *   El otro es la pantalla (`GamePanel`), que todo el tiempo pregunta "¿dónde estás?" para dibujarla.
*   **El problema:** Si la pantalla intenta leer la posición de la serpiente justo en el milisegundo en que la serpiente se está moviendo (borrando la cola o poniendo cabeza nueva), se va a encontrar con datos a medias o corruptos. Eso puede hacer que el juego rompa o se vea raro.

### B. Listas

El culpable del problema anterior es una lista que usan:

*   **La lista:** `java.util.ArrayDeque` en el cuerpo de la serpiente.
*   **Por qué falla:** Esa lista no está hecha para que varios hilos la toquen a la vez. Como no tiene "candado" ni nada de seguridad, si dos hilos entran al tiempo, se rompe (lanza una `ConcurrentModificationException`). Es como tratar de arreglar un motor con el carro andando.

### C. Bloqueos exagerados y pausas de mentiras

1.  **El mapa es un cuello de botella:**
    *   En la clase `Board` (el tablero), vimos que le pusieron `synchronized` a todo.
    *   Aunque eso evita errores, es como si en una autopista de 5 carriles obligaran a todos a pasar por una sola caseta de peaje. Si hay muchas serpientes, se van a quedar esperando su turno para moverse, y el juego se va a poner lento no por gráficos, sino por hacer fila.

2.  **La Pausa no pausa nada:**
    *   Cuando le das al botón de "Pausa", la pantalla deja de actualizarse, pero las serpientes siguen corriendo por debajo.
    *   Los hilos de las serpientes no tienen ni idea de que el juego está pausado, así que siguen calculando y moviéndose "a ciegas". Eso está mal porque gasta procesador sin necesidad y cuando reanudas, las serpientes ya están en otro lado.

3.  **Buscando sitio sin un sentido:**
    *   Cuando el juego busca dónde poner un ratón nuevo (`Board.randomEmpty`), lo hace probando al azar una y otra vez hasta que le pega a un sitio vacío.
    *   Si el tablero está casi lleno, se puede quedar ahí un buen rato probando y fallando, gastando CPU inútilmente (espera activa).

### 2) Correcciones mínimas y regiones críticas

- **Elimina** esperas activas reemplazándolas por **señales** / **estados** o mecanismos de la librería de concurrencia.
- Protege **solo** las **regiones críticas estrictamente necesarias** (evita bloqueos amplios).
- Justifica en **`el reporte de laboratorio`** cada cambio: cuál era el riesgo y cómo lo resuelves.

### 3) Control de ejecución seguro (UI)

- Implementa la **UI** con **Iniciar / Pausar / Reanudar** (ya existe el botón _Action_ y el reloj `GameClock`).
- Al **Pausar**, muestra de forma **consistente** (sin _tearing_):
  - La **serpiente viva más larga**.
  - La **peor serpiente** (la que **primero murió**).
- Considera que la suspensión **no es instantánea**; coordina para que el estado mostrado no quede “a medias”.

### 4) Robustez bajo carga

- Ejecuta con **N alto** (`-Dsnakes=20` o más) y/o aumenta la velocidad.
- El juego **no debe romperse**: sin `ConcurrentModificationException`, sin lecturas inconsistentes, sin _deadlocks_.
- Si habilitas **teleports** y **turbo**, verifica que las reglas no introduzcan carreras.

> Entregables detallados más abajo.

---

## Entregables

1. **Código fuente** funcionando en **Java 21**.
2. Todo de manera clara en **`**el reporte de laboratorio**`** con:
   - Data races encontradas y su solución.
   - Colecciones mal usadas y cómo se protegieron (o sustituyeron).
   - Esperas activas eliminadas y mecanismo utilizado.
   - Regiones críticas definidas y justificación de su **alcance mínimo**.
3. UI con **Iniciar / Pausar / Reanudar** y estadísticas solicitadas al pausar.

---

## Criterios de evaluación (10)

- (3) **Concurrencia correcta**: sin data races; sincronización bien localizada.
- (2) **Pausa/Reanudar**: consistencia visual y de estado.
- (2) **Robustez**: corre **con N alto** y sin excepciones de concurrencia.
- (1.5) **Calidad**: estructura clara, nombres, comentarios; sin _code smells_ obvios.
- (1.5) **Documentación**: **`reporte de laboratorio`** claro, reproducible;

---

## Tips y configuración útil

- **Número de serpientes**: `-Dsnakes=N` al ejecutar.
- **Tamaño del tablero**: cambiar el constructor `new Board(width, height)`.
- **Teleports / Turbo**: editar `Board.java` (métodos de inicialización y reglas en `step(...)`).
- **Velocidad**: ajustar `GameClock` (tick) o el `sleep` del `SnakeRunner` (incluye modo turbo).

---

## Cómo correr pruebas

```bash
mvn clean verify
```

Incluye compilación y ejecución de pruebas JUnit. Si tienes análisis estático, ejecútalo en `verify` o `site` según tu `pom.xml`.

---

## Créditos

Este laboratorio es una adaptación modernizada del ejercicio **SnakeRace** de ARSW. El enunciado de actividades se conserva para mantener los objetivos pedagógicos del curso.

**Base construida por el Ing. Javier Toquica.**

---





