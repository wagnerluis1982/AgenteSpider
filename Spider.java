import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Spider {
	protected static final Pattern HREF_REGEX = Pattern.compile(
			"href=\"([^'\" <>]*)\"", Pattern.CASE_INSENSITIVE);
	protected static final Pattern HOST_REGEX = Pattern.compile(
			"^http://([a-z.]+)");

	protected final String baseAddress;
	protected final String baseHost;
	protected final Map<String, Socket> openSockets;

	// Construtor
	public Spider(String baseAddress) {
		if (baseAddress == null)
			throw new NullPointerException();

		baseAddress = baseAddress.trim();
		if (!isValidArg(baseAddress))
			throw new IllegalArgumentException("O argumento dever ser um " +
					"endereço http válido, finalizado por /");

		this.baseAddress = baseAddress;
		this.baseHost = getHost(baseAddress);
		this.openSockets = new ConcurrentHashMap<>();
	}

	protected boolean isValidArg(final String address) {
		if (Pattern.matches("^http://[^'\" ]+/$", address))
			return true;

		return false;
	}

	protected String getHost(String address) {
		Matcher matcher = HOST_REGEX.matcher(address);
		matcher.find();
		return matcher.group(1);
	}

	/**
	 * Normaliza o link, removendo todos os "./" (diretório corrente) e
	 * substituindo todos os "../" (diretório acima).
	 * @param link String contendo o link a ser normalizado
	 * @return String com o link normalizado
	 */
	protected String normalizedLink(String link) throws NormalizationException {
		String[] split = link.split("/+");
		if (split.length == 0)
			return link;

		List<String> pieces = new LinkedList<>(Arrays.asList(split));
		for (int i = 0; i < pieces.size(); ) {
			if (pieces.get(i).equals("."))
				pieces.remove(i);
			else if (pieces.get(i).equals("..")) {
				pieces.remove(i);
				try {
					pieces.remove(i-1);
				} catch (IndexOutOfBoundsException e) {
					throw new NormalizationException("Link mal formado");
				}
				i--;
			} else
				i++;
		}

		// Detectando links mal formados (sem host ou protocolo)
		if (link.startsWith("http:")) {
			if (!pieces.get(0).equals("http:") || !pieces.get(1).equals(getHost(link)))
				throw new NormalizationException("Link mal formado");
		}
		// Detectando links mal formados (deixou de ser absoluto)
		else if (link.startsWith("/") && !pieces.get(0).equals(""))
			throw new NormalizationException("Link mal formado");

		// Juntando os pedaços do link
		StringBuffer path = new StringBuffer();
		Iterator<String> it = pieces.iterator();

		String piece = it.next();
		path.append(piece);
		if (piece.equals("http:"))
			path.append('/');

		while (it.hasNext()) {
			piece = it.next();
			path.append('/').append(piece);
		}

		if (link.endsWith("/"))
			path.append('/');

		return path.toString();
	}

	protected String absoluteLink(String link) {
		try {
			// Link já é absoluto (somente http)
			if (link.startsWith("http:"))
				return normalizedLink(link);

			// Link relativo à raiz deve se tornar absoluto
			if (link.startsWith("/"))
				return "http://" + this.baseHost + normalizedLink(link);

			// Links relativos
			return normalizedLink(this.baseAddress + link);
		} catch (NormalizationException e) {}

		// Outros protocolos e links mal formados não são suportados
		return null;
	}

	/**
	 * Obtém todos os links em uma página HTML, passada como argumento através
	 * de um {@link InputStream}.
	 *
	 * @param buffer Página HTML
	 * @return Lista de links encontrados
	 * @throws IOException se ocorrer um erro de E/S
	 */
	protected List<Link> findLinks(final BufferedReader buffer) {
		final List<Link> foundLinks = new ArrayList<>();

		String line;
		try {
			for (int no = 1; (line=buffer.readLine()) != null; no++) {
				final Matcher matcher = HREF_REGEX.matcher(line);
				while (matcher.find()) {
					String linkTo = this.absoluteLink(matcher.group(1));
					if (linkTo != null)
						foundLinks.add(new Link(this.baseAddress, linkTo, no));
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return foundLinks;
	}

	protected Socket getHttpSocket(String host) throws UnknownHostException, IOException {
		Socket sock = this.openSockets.get(host);
		if (sock == null || sock.isClosed()) {
			sock = new Socket(host, 80);
			this.openSockets.put(host, sock);
		}

		return sock;
	}

	protected Page httpGet(String address) throws IOException {
		// Conexão
		String host = getHost(address);
		Socket socket = getHttpSocket(host);
		OutputStream os = socket.getOutputStream();
		InputStream  is = socket.getInputStream();

		// Requisição
		String requisition = String.format("GET %s HTTP/1.1\r\n" +
				"Host:%s\r\n\r\n", address, host);
		os.write(requisition.getBytes());

		// Código de resposta
		BufferedReader buffer = new BufferedReader(new InputStreamReader(is));
		int code = Integer.parseInt(buffer.readLine().split(" ")[1]);

		// Só obtem conteúdo quando código for 200
		if (code != 200) {
			return new Page(code, null);
		}

		// Posicionando o buffer na posição do conteúdo
		while (buffer.readLine().length() != 0);

		return new Page(code, buffer);
	}

	protected int httpHead(String address) throws IOException {
		// Conexão
		String host = getHost(address);
		Socket socket = getHttpSocket(host);
		OutputStream os = socket.getOutputStream();
		InputStream  is = socket.getInputStream();

		// Requisição
		String requisition = String.format("HEAD %s HTTP/1.1\r\n" +
				"Host:%s\r\n\r\n", address, host);
		os.write(requisition.getBytes());

		// Código de resposta
		BufferedReader buffer = new BufferedReader(new InputStreamReader(is));
		int code = Integer.parseInt(buffer.readLine().split(" ")[1]);

		return code;
	}

	protected List<InvalidLink> invalidLinks(Link link) {
		List<InvalidLink> invalids = new ArrayList<>();

		Page page = null;
		try {
			page = httpGet(link.linkTo);
			if (page.code != 200) {
				invalids.add(new InvalidLink(link, page.code));
				return invalids;
			}
		} catch (IOException e) {
			// Erro de DNS
			invalids.add(new InvalidLink(link, 0));
			return invalids;
		}

		for (Link found : findLinks(page.buffer)) {
			try {
				int code = httpHead(found.linkTo);
				if (code != 200)
					invalids.add(new InvalidLink(found, code));
			} catch (IOException e) {
				// Erro de DNS
				invalids.add(new InvalidLink(found, 0));
			}
		}

		return invalids;
	}

	public List<InvalidLink> invalidLinks() {
		return this.invalidLinks(new Link("<base>", this.baseAddress, 0));
	}

	public static void main(String[] args) {
		long startTime = System.currentTimeMillis();

		String url;
		if (args.length < 1) {
			System.out.print("Digite um endereço: ");
			Scanner scanner = new Scanner(System.in);
			url = scanner.nextLine();
		} else {
			url = args[0];
		}

		Spider spider = null;
		try {
			spider = new Spider(url);
		} catch (IllegalArgumentException e) {
			System.out.println(e.getMessage());
			System.exit(1);
		}

		for (InvalidLink invalid : spider.invalidLinks()) {
			Link link = invalid.link;
			System.out.println(String.format("%s %03d %s %d",
					link.linkTo, invalid.code, link.pageUrl, link.line));
		}

		System.out.println("TIME " + (System.currentTimeMillis() - startTime));
	}

}

class Link {
	final String pageUrl;
	final String linkTo;
	final int line;

	public Link(final String pageUrl, final String linkTo, final int line) {
		this.pageUrl = pageUrl;
		this.linkTo = linkTo;
		this.line = line;
	}
}

class InvalidLink {
	final Link link;
	int code;

	public InvalidLink(Link link, int code) {
		this.link = link;
		this.code = code;
	}
}

class Page {
	final int code;
	final BufferedReader buffer;

	public Page(int code, BufferedReader buffer) {
		this.code = code;
		this.buffer = buffer;
	}
}

class SpiderSocket {
	final Socket sock;
	final BufferedReader in;
	final OutputStream out;

	public SpiderSocket(Socket sock, BufferedReader in, OutputStream out) {
		this.sock = sock;
		this.in = in;
		this.out = out;
	}
}

@SuppressWarnings("serial")
class NormalizationException extends Exception {
	public NormalizationException(String message) {
		super(message);
	}
}
