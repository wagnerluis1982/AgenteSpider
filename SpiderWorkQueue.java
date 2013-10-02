import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;


class SpiderWorkQueue {
	private BlockingQueue<WorkThread> jobQueue;
	private Queue<WorkThread> waitQueue;

	public SpiderWorkQueue(int capacity) {
		jobQueue = new ArrayBlockingQueue<>(capacity, true);
		waitQueue = new ArrayDeque<>();
	}

	public void executeAndWait() throws InterruptedException {
		while (!(waitQueue.isEmpty() && jobQueue.isEmpty())) {
			WorkThread t = waitQueue.poll();
			if (t != null) {
				jobQueue.put(t);
				t.start();
			} else {
				Thread.sleep(1);
			}
		}
	}

	public void submit(final Runnable spiderRunner) {
		waitQueue.offer(new WorkThread(spiderRunner));
	}

	private class WorkThread extends Thread {
		public WorkThread(Runnable spiderRunner) {
			super(spiderRunner);
		}

		@Override
		public void run() {
			super.run();
			jobQueue.remove(this);
		}
	}

}
