package co.eci.snake.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;

public final class Board {
  private final int width;
  private final int height;

  private final Set<Position> mice = ConcurrentHashMap.newKeySet();
  private final Set<Position> obstacles = ConcurrentHashMap.newKeySet();
  private final Set<Position> turbo = ConcurrentHashMap.newKeySet();
  private final Set<Position> occupied = ConcurrentHashMap.newKeySet();
  private final Map<Position, Position> teleports = new ConcurrentHashMap<>();

  public enum MoveResult { MOVED, ATE_MOUSE, HIT_OBSTACLE, ATE_TURBO, TELEPORTED }

  public Board(int width, int height) {
    if (width <= 0 || height <= 0) throw new IllegalArgumentException("Board dimensions must be positive");
    this.width = width;
    this.height = height;
    for (int i=0;i<6;i++) mice.add(randomEmpty());
    for (int i=0;i<4;i++) obstacles.add(randomEmpty());
    for (int i=0;i<3;i++) turbo.add(randomEmpty());
    createTeleportPairs(2);
  }

  public int width() { return width; }
  public int height() { return height; }

  public Set<Position> mice() { return new HashSet<>(mice); }
  public Set<Position> obstacles() { return new HashSet<>(obstacles); }
  public Set<Position> turbo() { return new HashSet<>(turbo); }
  public Map<Position, Position> teleports() { return new HashMap<>(teleports); }

  public synchronized MoveResult step(Snake snake) {
    Objects.requireNonNull(snake, "snake");
    var head = snake.head();
    var dir = snake.direction();
    Position next = new Position(head.x() + dir.dx, head.y() + dir.dy).wrap(width, height);

    if (obstacles.contains(next)) return MoveResult.HIT_OBSTACLE;

    boolean teleported = false;
    if (teleports.containsKey(next)) {
      next = teleports.get(next);
      teleported = true;
    }

    // Determina si al moverse a `next` la serpiente crecerá (ratón) o usará turbo.
    boolean willGrow = mice.contains(next);
    boolean willUseTurbo = turbo.contains(next);

    // Permitir moverse a la posición de la cola actual si la cola será removida
    // (es decir, cuando la serpiente no crece). Evitar movimientos hacia
    // posiciones ocupadas en caso contrario.
    java.util.Deque<Position> body = snake.snapshot();
    Position tail = body.peekLast();
    if (occupied.contains(next) && !(next.equals(tail) && !willGrow)) {
      return MoveResult.HIT_OBSTACLE;
    }

    // Reclamar recursos de forma atómica (estamos en un método synchronized)
    boolean ateMouse = mice.remove(next);
    boolean ateTurbo = turbo.remove(next);

    // Avanzar la serpiente y actualizar las posiciones ocupadas
    snake.advance(next, ateMouse);
    occupied.add(next);
    if (tail != null && !ateMouse) {
      occupied.remove(tail);
    }

    if (ateMouse) {
      mice.add(randomEmpty());
      obstacles.add(randomEmpty());
      if (ThreadLocalRandom.current().nextDouble() < 0.2) turbo.add(randomEmpty());
    }

    if (ateTurbo) return MoveResult.ATE_TURBO;
    if (ateMouse) return MoveResult.ATE_MOUSE;
    if (teleported) return MoveResult.TELEPORTED;
    return MoveResult.MOVED;
  }

  public synchronized void registerSnake(Snake s) {
    if (s == null) return;
    Position h = s.head();
    if (h != null) occupied.add(h);
  }

  public synchronized void unregisterSnake(Snake s) {
    if (s == null) return;
    for (Position p : s.snapshot()) occupied.remove(p);
  }

  private void createTeleportPairs(int pairs) {
    for (int i=0;i<pairs;i++) {
      Position a = randomEmpty();
      Position b = randomEmpty();
      teleports.put(a, b);
      teleports.put(b, a);
    }
  }

  private Position randomEmpty() {
    var rnd = ThreadLocalRandom.current();
    Position p;
    int guard = 0;
    do {
      p = new Position(rnd.nextInt(width), rnd.nextInt(height));
      guard++;
      if (guard > width*height*2) break;
    } while (mice.contains(p) || obstacles.contains(p) || turbo.contains(p) || teleports.containsKey(p));
    return p;
  }
}
