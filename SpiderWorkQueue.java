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
		while (!(waitQueue.isEmpty() && taskQueue.isEmpty())) {
			Thread t = waitQueue.poll();
			if (t != null) {
				taskQueue.put(t);
				t.start();
			} else {
				Thread.sleep(1);
			}
		}
	}

	public void submit(final Runnable spiderRunner) {
		waitQueue.offer(new WorkThread(spiderRunner));
	}

	private void remove(Thread finished) {
		taskQueue.remove(finished);
	}

	private class WorkThread extends Thread {
		public WorkThread(Runnable spiderThread) {
			super(spiderThread);
		}

		@Override
		public void run() {
			super.run();
			remove(this);
		}
	}

}
