import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;


class SpiderWorkQueue {
	private BlockingQueue<Thread> taskQueue;
	private Queue<Thread> waitQueue;

	public SpiderWorkQueue(int capacity) {
		taskQueue = new ArrayBlockingQueue<>(capacity, true);
		waitQueue = new ArrayDeque<>();
	}

	public void executeAndWait() throws InterruptedException {
		while (!waitQueue.isEmpty() || !taskQueue.isEmpty()) {
			Thread t = waitQueue.poll();
			if (t != null) {
				taskQueue.put(t);
				t.start();
			} else {
				Thread.sleep(1);
			}
		}
	}

	public void submit(final Thread spiderThread) {
		waitQueue.offer(spiderThread);
	}

	public boolean remove(Thread finished) {
		return taskQueue.remove(finished);
	}
}
