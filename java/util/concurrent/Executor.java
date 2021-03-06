/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166 Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

/**
 * An object that executes submitted Runnable tasks.
 *
 * 该类提供了一种在任务的提交和运行(线程的使用及调度)之间进行解耦的方式。
 * This interface provides a way of decoupling task submission from the mechanics of how each task will be run, including details of thread use,
 * scheduling, etc. An Executor is normally used instead of explicitly creating threads.
 * For example, rather than invoking new Thread(new(RunnableTask())).start() for each of a set of tasks, you might use:
 *
     * Executor executor = new Executor();
     * executor.execute(new RunnableTask1());
     * executor.execute(new RunnableTask2());
     * ...
 *
 * Executor 接口并没有严格地要求执行是异步的.简单地在调用者线程中运行任务也是允许的
 * However, the Executor interface does not strictly require that execution be asynchronous. In the simplest case, an executor can run the
 * submitted task immediately in the caller's thread:
 *
     * class DirectExecutor implements Executor {
     *       public void execute(Runnable r) {
     *           r.run(); //依然是在调用者的线程里执行了任务
     *       }
     * }
 *
 * 但是更典型的使用方式还是启动一个新的线程去运行提交的任务
 * More typically, tasks are executed in some thread other than the caller's thread.  The executor below spawns a new thread for each task.
 *
     * class ThreadPerTaskExecutor implements Executor {
     *       public void execute(Runnable r) {
     *           new Thread(r).start();
     *       }
     * }
 *
 * Many Executor implementations impose some sort of limitation on how and when tasks are scheduled.
 * The executor below serializes the submission of tasks to a second executor,illustrating a composite executor.
 *
     * class SerialExecutor implements Executor {
     *      final Queue<Runnable> tasks = new ArrayDeque<Runnable>();
     *      final Executor executor;
     *      Runnable active;
     *
     *      SerialExecutor(Executor executor) {
     *          this.executor = executor;
     *      }
     *
     *      public synchronized void execute(final Runnable r) {
     *          tasks.offer(new Runnable() {
     *              public void run() {
     *                  try { r.run(); }
     *                  finally { scheduleNext(); }
     *              }
     *          });
     *          if (active == null) {
     *              scheduleNext();
     *          }
     *      }
     *
     *      protected synchronized void scheduleNext() {
     *          if ((active = tasks.poll()) != null) {
     *              executor.execute(active);
     *          }
     *      }
     * }
 *
 * The Executor implementations provided in this package implement ExecutorService, which is a more extensive interface.
 * The ThreadPoolExecutor class provides an extensible thread pool implementation.
 * The Executors class provides convenient factory methods for these Executors.
 *
 * 内存一致性效果：线程中将Runnable对象提交到Executor之前的操作happen-before其执行开始。尽管Runnable对象代表的任务可能在另一个线程中执行。
 * Memory consistency effects: Actions in a thread prior to submitting a Runnable object to an Executor happen-before its execution begins,
 * perhaps in another thread.
 *
 * @since 1.5
 * @author Doug Lea
 */
public interface Executor {

    /**
     * Executes the given command at some time in the future. The command may execute in a new thread, in a pooled thread, or in the calling
     * thread, at the discretion of the Executor implementation.
     *
     * @param command the runnable task
     * @throws RejectedExecutionException if this task cannot be accepted for execution.
     * @throws NullPointerException if command is null
     */
    void execute(Runnable command);
}
