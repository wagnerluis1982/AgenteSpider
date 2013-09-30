import java.io.IOException;
import java.io.OutputStream;


class NullOutputStream extends OutputStream {
	public static final OutputStream NULL_STREAM = new NullOutputStream();

	@Override
	public void write(int b) throws IOException {
		// Ignora quaisquer entradas
	}

	@Override
	public void write(byte[] b) throws IOException {
		// Ignora quaisquer entradas
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		// Ignora quaisquer entradas
	}

	@Override
	public String toString() {
		return "";
	}
}