package co.eci.snake.concurrency;

import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Snake;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public final class SnakeRunner implements Runnable {
  private final Snake snake;
  private final Board board;
  private final int baseSleepMs = 80;
  private final int turboSleepMs = 40;
  private int turboTicks = 0;
  
  private final Supplier<Boolean> isPaused;
  private final Object pauseLock;

  public SnakeRunner(Snake snake, Board board, Supplier<Boolean> isPaused, Object pauseLock) {
    this.snake = snake;
    this.board = board;
    this.isPaused = isPaused;
    this.pauseLock = pauseLock;
  }

  @Override
  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted() && snake.isAlive()) {
        checkPause();
        maybeTurn();
        var res = board.step(snake);
        if (res == Board.MoveResult.HIT_OBSTACLE) {
          snake.kill(); // Muere al chocar
        } else if (res == Board.MoveResult.ATE_TURBO) {
          turboTicks = 100;
        }
        int sleep = (turboTicks > 0) ? turboSleepMs : baseSleepMs;
        if (turboTicks > 0) turboTicks--;
        Thread.sleep(sleep);
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  private void checkPause() throws InterruptedException {
    if (isPaused.get()) {
      synchronized (pauseLock) {
        while (isPaused.get()) {
          pauseLock.wait();
        }
      }
    }
  }

  private void maybeTurn() {
    double p = (turboTicks > 0) ? 0.05 : 0.10;
    if (ThreadLocalRandom.current().nextDouble() < p) randomTurn();
  }

  private void randomTurn() {
    var dirs = Direction.values();
    snake.turn(dirs[ThreadLocalRandom.current().nextInt(dirs.length)]);
  }
}
