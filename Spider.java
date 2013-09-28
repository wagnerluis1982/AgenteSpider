import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.Socket;
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
	private static final int BUF_SIZE = 10240;
	// Regex constants
	protected static final Pattern HREF_REGEX = Pattern.compile(
			"href=\"([^'\" <>]*)\"", Pattern.CASE_INSENSITIVE);
	protected static final Pattern HOST_REGEX = Pattern.compile(
			"^http://(.+?)/", Pattern.CASE_INSENSITIVE);
	protected static final Pattern CLENGTH_REGEX = Pattern.compile(
			"^content-length:", Pattern.CASE_INSENSITIVE);
	protected static final Pattern CHUNKED_REGEX= Pattern.compile(
			"^transfer-encoding:\\s?chunked$", Pattern.CASE_INSENSITIVE);
	// Other constants
	protected static final StringBuilder EMPTY_CONTENT = new StringBuilder(0);

	protected final String baseAddress;
	protected final String baseHost;
	protected final Map<String, SpiderSocket> openSockets;

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
	 * @param content Página HTML
	 * @return Lista de links encontrados
	 * @throws IOException se ocorrer um erro de E/S
	 */
	protected List<Link> findLinks(final CharSequence content) {
		final List<Link> foundLinks = new ArrayList<>();
		BufferedReader buffer = new BufferedReader(new StringReader(content.toString()));

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

	protected SpiderSocket getSpiderSocket(String host) throws IOException {
		SpiderSocket sock = this.openSockets.get(host);
		if (sock == null || sock.realSock.isClosed()) {
			System.out.printf("Novo socket para '%s' aberto\n", host);
			sock = new SpiderSocket(new Socket(host, 80));
			this.openSockets.put(host, sock);
		}

		return sock;
	}

	protected Page httpGet(String address) throws IOException {
		// Conexão
		String host = getHost(address);
		SpiderSocket sock = getSpiderSocket(host);

		// Requisição
		String requisition = String.format("GET %s HTTP/1.1\r\n" +
				"Host:%s\r\n\r\n", address, host);
		sock.out.write(requisition.getBytes());
		// Obtém o cabeçalho http
		Header header = readHttpHeader(sock);

		if (header.getHttpVersion().equals("HTTP/1.0")) {
			return http10GetContent(sock, header);
		}

		else if (header.getHttpVersion().equals("HTTP/1.1"))
			return http11GetContent(sock, header);

		throw new RuntimeException(String.format(
				"O Agente Spider não suporta o protocolo '%s'",
				header.getHttpVersion()));
	}

	private Header readHttpHeader(SpiderSocket sock) throws IOException {
		final StringBuilder sbHeader = new StringBuilder();

		// Obtém inicialmente quatro bytes para não gerar exceção no while
		for (int i = 0; i < 4; i++)
			sbHeader.append((char) sock.in.read());

		// Obtém todo o header
		while (!sbHeader.substring(sbHeader.length()-4).equals("\r\n\r\n"))
			sbHeader.append((char) sock.in.read());

		final String[] headerLines = sbHeader.toString().split("\r\n");

		// Código de resposta e versão do protocolo
		final String[] firstLine = headerLines[0].split(" ");
		final String protocol = firstLine[0];
		final int code = Integer.parseInt(firstLine[1]);

		// Content-Length e Transfer-Encoding
		int clength = 0;
		boolean chunked = false;
		for (int i = 0; i < headerLines.length; i++) {
			final String line = headerLines[i];
			if (CLENGTH_REGEX.matcher(line).lookingAt()) {
				clength = Integer.parseInt(line.split(":")[1].trim());
				break;
			}
			else if (CHUNKED_REGEX.matcher(line).matches()) {
				chunked = true;
				break;
			}
		}

		return new Header(protocol, code, clength, chunked);
	}

	/**
	 * Algoritmos na tentativa de reusar a conexão com o servidor. Eles são
	 * necessários por dois motivos:
	 * - Melhor uso de memória e processador (pois menos sockets precisarão
	 *   ser abertos).
	 * - Impedir que o servidor bloqueie o processo. Alguns servidores como
	 *   os do Google fazem isso. Esse é o ponto mais importante, pois
	 *   inviabilizaria o Agente Spider.
	 */
	private Page http11GetContent(SpiderSocket sock, Header header) throws IOException {
		// Se o header Content-Length foi encontrado
		if (header.getContentLength() != -1)
			return http11GetContentByLength(sock, header);

		// Se o header Transfer-Encoding: chunked foi encontrado
		else if (header.isChunked())
			return http11GetContentChunked(sock, header);

		/* Se não foi encontrado nada que indique onde parar, tenta obter com
		 * o algoritmo de HTTP/1.0, habilitando o timeout para não bloquear.
		 */
		else
			return http10GetContent(sock, header, 2000);
	}

	private Page http11GetContentChunked(SpiderSocket sock, Header header) throws IOException {
		final StringBuilder sb = new StringBuilder();

		// Pega o primeiro tamanho do chunk
		do {
			sb.append((char) sock.in.read());
		} while (sb.charAt(sb.length()-1) != '\n');
		int chunkSize = Integer.parseInt(sb.toString(), 16);
		sb.setLength(0);

		ByteArrayOutputStream content = new ByteArrayOutputStream(BUF_SIZE);
		while (chunkSize > 0) {
			final byte[] buf = new byte[chunkSize];
			do {
				final int bytesRead = sock.in.read(buf, 0, chunkSize);
				content.write(buf, 0, bytesRead);
				chunkSize -= bytesRead;
			} while (chunkSize > 0);
			sock.in.skip(2);
			// Pega tamanho do próximo chunk
			do {
				sb.append((char) sock.in.read());
			} while (sb.charAt(sb.length()-1) != '\n');
			chunkSize = Integer.parseInt(sb.toString(), 16);
			sb.setLength(0);
		}
		sock.in.skip(2);

		return new Page(header.getCode(), content.toString());
	}

	private Page http11GetContentByLength(SpiderSocket sock, Header header)
			throws IOException {
		if (header.getContentLength() == 0) {
			sock.in.skip(2);
			return new Page(header.getCode(), EMPTY_CONTENT);
		}
		else {
			int clength = header.getContentLength();
			final byte[] buf = new byte[(clength < BUF_SIZE) ? clength : BUF_SIZE];
			ByteArrayOutputStream content = new ByteArrayOutputStream(BUF_SIZE);

			do {
				final int bytesRead = sock.in.read(buf, 0, (clength < BUF_SIZE) ? clength : BUF_SIZE);
				clength -= bytesRead;
				content.write(buf, 0, bytesRead);
			} while (clength > 0);
			sock.in.skip(2);

			return new Page(header.getCode(), content.toString());
		}
	}

	private Page http10GetContent(SpiderSocket sock, Header header) throws IOException {
		return http10GetContent(sock, header, 0);
	}

	private Page http10GetContent(SpiderSocket sock, Header header, int timeout) throws IOException {
		sock.realSock.setSoTimeout(timeout);
		if (header.getCode() != 200)
			return new Page(header.getCode(), null);

		final byte[] buf = new byte[BUF_SIZE];
		ByteArrayOutputStream content = new ByteArrayOutputStream(BUF_SIZE);

		int bytesRead;
		while ((bytesRead=sock.in.read(buf)) != -1);
			content.write(buf, 0, bytesRead);

		return new Page(header.getCode(), content.toString());
	}

	protected int httpHead(String address) throws IOException {
		// Conexão
		String host = getHost(address);
		SpiderSocket sock = getSpiderSocket(host);

		// Requisição
		String requisition = String.format("HEAD %s HTTP/1.1\r\n" +
				"Host:%s\r\n\r\n", address, host);
		sock.out.write(requisition.getBytes());

		return readHttpHeader(sock).getCode();
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

		for (Link found : findLinks(page.content)) {
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

class Header {
	private final String version;
	private final int code;
	private final int clength;
	private final boolean chunked;

	public Header(String version, int code, int clength, boolean chunked) {
		this.version = version;
		this.code = code;
		this.clength = clength;
		this.chunked = chunked;
	}

	public String getHttpVersion() {
		return this.version;
	}

	public int getCode() {
		return this.code;
	}

	public int getContentLength() {
		return this.clength;
	}

	public boolean isChunked() {
		return this.chunked;
	}
}

class Page {
	final int code;
	final CharSequence content;

	public Page(int code, CharSequence content) {
		this.code = code;
		this.content = content;
	}
}

class SpiderSocket {
	final Socket realSock;
	final BufferedInputStream in;
	final OutputStream out;

	public SpiderSocket(Socket sock) throws IOException {
		this.realSock = sock;
		this.in = new BufferedInputStream(sock.getInputStream());
		this.out = sock.getOutputStream();
	}
}

@SuppressWarnings("serial")
class NormalizationException extends Exception {
	public NormalizationException(String message) {
		super(message);
	}
}
