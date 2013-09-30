import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;


class SpiderSocket {
	private final Socket realSock;
	private final InputStream input;
	private final OutputStream output;

	public SpiderSocket(Socket sock) throws IOException {
		this.realSock = sock;
		this.input = new BufferedInputStream(sock.getInputStream());
		this.output = sock.getOutputStream();
	}

	public Socket getRealSock() {
		return realSock;
	}

	public InputStream getInput() {
		return input;
	}

	public OutputStream getOutput() {
		return output;
	}
}