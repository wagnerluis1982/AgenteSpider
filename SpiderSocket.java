import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;


class SpiderSocket {
	private final Socket realSock;
	private final BufferedReader input;
	private final OutputStream output;

	public SpiderSocket(Socket sock) throws IOException {
		this.realSock = sock;
		this.input = new BufferedReader(new InputStreamReader(sock.getInputStream()));
		this.output = sock.getOutputStream();
	}

	public Socket getRealSock() {
		return realSock;
	}

	public BufferedReader getInput() {
		return input;
	}

	public OutputStream getOutput() {
		return output;
	}
}
