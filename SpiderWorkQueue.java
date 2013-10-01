import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;


class SpiderWorkQueue {
	private BlockingQueue<Thread> queue;

	public SpiderWorkQueue(int capacity) {
		queue = new ArrayBlockingQueue<>(capacity, true);
	}

	public void awaitEnd() throws InterruptedException {
		do {
			Thread.sleep(1);
		} while (!queue.isEmpty());
	}

	public void submit(final Thread elem) {
		Thread putThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					queue.put(elem);
					elem.start();
				} catch (InterruptedException e) {
				}
			}
		});
		putThread.start();
	}

	public boolean remove(Thread finished) {
		return queue.remove(finished);
	}
}