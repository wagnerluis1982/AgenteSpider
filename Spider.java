import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Spider {
	private static final int BUF_SIZE = 10240;

	// Regex constants
	private static final Pattern HREF_REGEX = Pattern.compile(
			"href=\"([^'\" <>]*)\"", Pattern.CASE_INSENSITIVE);
	private static final Pattern HOST_REGEX = Pattern.compile(
			"^http://(?:(.+?)/|(.+)$)", Pattern.CASE_INSENSITIVE);
	private static final Pattern PATH_REGEX = Pattern.compile(
			"^http://.+?(/.*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern NOHTML_REGEX = Pattern.compile(
			"^.*\\.(pdf|png|jpg|jpeg|exe|txt|gif|js|css|dll|xml|rss|svg|swf|" +
					"avi|bmp|bin|zip|rar|gz|bz|bz2|flv|mov|docx?|xlsx?|pptx?|" +
					"ppsx?|odt|ods|odp|tex|mid|wav|mp3|mp4|mpg|tif|ico)$",
			Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
	private static final Pattern HEADER_REGEX = Pattern.compile(
			"^HTTP/(?<version>[.0-9]+) (?<code>[0-9]+)|" +
			"^content-type:\\s*(?<ctype>[a-z\\-/]+)|" +
			"^content-length:\\s*(?<clength>[0-9]+)$|" +
			"^connection:\\s*(?<connection>.+)$|" +
			"^transfer-encoding:\\s*(?<tencoding>.+)$",
			Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

	// Other constants
	private static final String EMPTY_CONTENT = "";
	private static final Header EMPTY_HEADER = new Header(null);

	private final String baseAddress;
	private final String baseHost;
	private final Map<String, SpiderSocket> openSockets;
	private final List<InvalidLink> invalids = Collections.synchronizedList(new ArrayList<InvalidLink>());
	private Set<String> viewed = Collections.synchronizedSet(new HashSet<String>());

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

	private boolean isValidArg(final String address) {
		if (Pattern.matches("^http://[^'\" ]+/$", address))
			return true;

		return false;
	}

	private String getHost(String address) {
		Matcher matcher = HOST_REGEX.matcher(address);

		if (matcher.find()) {
			String host = matcher.group(1);
			if (host != null)
				return host;
			else
				return matcher.group(2);
		}

		return null;
	}

	private String getAddressPath(String address) {
		Matcher matcher = PATH_REGEX.matcher(address);

		if (matcher.find())
			return matcher.group(1);

		return "/";
	}

	/**
	 * Normaliza o link, removendo todos os "./" (diretório corrente) e
	 * substituindo todos os "../" (diretório acima).
	 * @param link String contendo o link a ser normalizado
	 * @return String com o link normalizado
	 */
	private String normalizedLink(String link) throws NormalizationException {
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

	private String absoluteLink(String link) {
		try {
			// Link já é absoluto (somente http)
			if (link.startsWith("http:"))
				return normalizedLink(link);

			// Outros protocolos não são suportados
			if (link.contains(":"))
				return null;

			// Link relativo à raiz deve se tornar absoluto
			if (link.startsWith("/"))
				return "http://" + this.baseHost + normalizedLink(link);

			// Links relativos
			return normalizedLink(this.baseAddress + link);
		} catch (NormalizationException e) {}

		// Links mal formados
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
	private List<Link> findLinks(final CharSequence content) {
		final List<Link> foundLinks = new ArrayList<>();

		BufferedReader buffer = new BufferedReader(new StringReader(content.toString()));
		String line;
		try {
			for (int lineno = 1; (line=buffer.readLine()) != null; lineno++) {
				final Matcher matcher = HREF_REGEX.matcher(line);
				while (matcher.find()) {
					String linkTo = absoluteLink(matcher.group(1));
					if (linkTo != null)
						foundLinks.add(new Link(this.baseAddress, linkTo, lineno));
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return foundLinks;
	}

	private SpiderSocket getSpiderSocket(String host) throws IOException {
		SpiderSocket sock = this.openSockets.get(host);
		if (sock == null || sock.getRealSock().isClosed()) {
			sock = new SpiderSocket(new Socket(host, 80));
			this.openSockets.put(host, sock);
		}

		return sock;
	}

	private Header httpHead(String address) throws IOException {
		System.out.println("HEAD " + address);
		// Conexão
		String host = getHost(address);
		address = getAddressPath(address);
		SpiderSocket sock = getSpiderSocket(host);

		// Faz duas tentativas de obter o cabeçalho
		for (int i = 0; i < 2; i++) {
			try {
				// Requisição
				String requisition = String.format("HEAD %s HTTP/1.1\r\n" +
						"Host:%s\r\n\r\n", address, host);
				sock.getOutput().write(requisition.getBytes());

				return readHeaderHttp(sock);
			} catch (SocketException e) {
				// Se houve erros na tentativa de comunicação, força a recriação
				// do socket.
				sock.getRealSock().close();
				sock = getSpiderSocket(host);
			}
		}

		return EMPTY_HEADER;
	}

	private Page httpGet(String address) throws IOException {
		System.out.println("GET " + address);
		// Conexão
		address = getAddressPath(address);
		SpiderSocket sock = getSpiderSocket(this.baseHost);

		Header header = null;
		// Faz duas tentativas de obter o cabeçalho
		for (int i = 0; i < 2; i++) {
			try {
				// Requisição
				String requisition = String.format("GET %s HTTP/1.1\r\n" +
						"Host:%s\r\n\r\n", address, this.baseHost);
				sock.getOutput().write(requisition.getBytes());

				header = readHeaderHttp(sock);
				break;
			} catch (SocketException e) {
				// Se houve erros na tentativa de comunicação, força a recriação
				// do socket.
				sock.getRealSock().close();
				sock = getSpiderSocket(this.baseHost);
			}
		}

		// Tentativas falharam
		if (header == null)
			return new Page(EMPTY_HEADER, EMPTY_CONTENT);

		if (header.getHttpVersion().equals("1.1"))
			return http11GetContent(sock, header);

		else // assume que seja HTTP/1.0
			return http10GetContent(sock, header);
	}

	private Header readHeaderHttp(SpiderSocket sock) throws IOException {
		InputStream inputStream = sock.getInput();
		final StringBuilder sbHeader = new StringBuilder(500);

		// Obtém inicialmente o suficiente para o início do cabeçalho http
		int c = inputStream.read();
		if (c == -1)
			throw new SocketException("Problema nesse socket");
		sbHeader.append((char) c);
		for (int i = 0; i < 20; i++)
			sbHeader.append((char) inputStream.read());

		// Obtém o resto do cabeçalho
		while (!sbHeader.substring(sbHeader.length()-4).equals("\r\n\r\n"))
			sbHeader.append((char) inputStream.read());

		Map<String, Object> headerFields = new Hashtable<>();

		// Obtém cabeçalho
		Matcher matcher = HEADER_REGEX.matcher(sbHeader);
		String matched;
		while (matcher.find()) {
			if ((matched=matcher.group(Header.HTTP_VERSION)) != null)
				headerFields.put(Header.HTTP_VERSION, matched);
			if ((matched=matcher.group(Header.STATUS_CODE)) != null)
				headerFields.put(Header.STATUS_CODE, Integer.parseInt(matched));
			if ((matched=matcher.group(Header.CONTENT_TYPE)) != null)
				headerFields.put(Header.CONTENT_TYPE, matched);
			if ((matched=matcher.group(Header.CONTENT_LENGTH)) != null)
				headerFields.put(Header.CONTENT_LENGTH, Integer.parseInt(matched));
			if ((matched=matcher.group(Header.CONNECTION)) != null)
				headerFields.put(Header.CONNECTION, matched);
			if ((matched=matcher.group(Header.TRANSFER_ENCODING)) != null)
				headerFields.put(Header.TRANSFER_ENCODING, matched);
		}

		return new Header(headerFields);
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
		// Se o header Transfer-Encoding: chunked foi encontrado
		if (header.isChunked())
			return http11GetContentChunked(sock, header);

		// Se o header Content-Length foi encontrado
		if (header.getContentLength() != -1)
			return http11GetContentByLength(sock, header);

		/* Se não foi encontrado nada que indique onde parar, tenta obter com
		 * o algoritmo de HTTP/1.0, habilitando o timeout para não bloquear.
		 */
		return http10GetContent(sock, header, 2000);
	}

	private Page http11GetContentChunked(SpiderSocket sock, Header header) throws IOException {
		InputStream inputStream = sock.getInput();
		final StringBuilder sb = new StringBuilder();

		// Pega o primeiro tamanho do chunk
		char c;
		while ((c=(char) inputStream.read()) != '\n')
			sb.append(c);

		int chunkSize = Integer.parseInt(sb.toString().trim(), 16);

		OutputStream content = null;
		boolean isHtml = false;
		if (header.getContentType().startsWith("text/html")) {
			content = new ByteArrayOutputStream(BUF_SIZE);
			isHtml = true;
		} else
			content = NullOutputStream.NULL_STREAM;

		if (isHtml)
			while (chunkSize > 0) {
				final byte[] buf = new byte[chunkSize];
				do {
					final int bytesRead = inputStream.read(buf, 0, chunkSize);
					content.write(buf, 0, bytesRead);
					chunkSize -= bytesRead;
				} while (chunkSize > 0);
				inputStream.skip(2);
				// Pega tamanho do próximo chunk
				sb.setLength(0);
				while ((c=(char) inputStream.read()) != '\n')
					sb.append(c);
				chunkSize = Integer.parseInt(sb.toString().trim(), 16);
			}
		else
			while (chunkSize > 0) {
				inputStream.skip(chunkSize + 2);
				// Pega tamanho do próximo chunk
				sb.setLength(0);
				while ((c=(char) inputStream.read()) != '\n')
					sb.append(c);
				chunkSize = Integer.parseInt(sb.toString().trim(), 16);
			}
		inputStream.skip(2);

		return new Page(header, content.toString());
	}

	private Page http11GetContentByLength(SpiderSocket sock, Header header)
			throws IOException {
		InputStream inputStream = sock.getInput();

		int clength = header.getContentLength();
		if (clength == 0) {
			inputStream.skip(2);
			return new Page(header, EMPTY_CONTENT);
		}
		else if (!header.getContentType().startsWith("text/html")) {
			inputStream.skip(clength);
			return new Page(header, EMPTY_CONTENT);
		}

		final byte[] buf = new byte[(clength < BUF_SIZE) ? clength : BUF_SIZE];
		ByteArrayOutputStream content = new ByteArrayOutputStream(buf.length);

		do {
			final int bytesRead = inputStream.read(buf, 0, (clength < BUF_SIZE) ? clength : BUF_SIZE);
			clength -= bytesRead;
			content.write(buf, 0, bytesRead);
		} while (clength > 0);

		return new Page(header, content.toString());
	}

	private Page http10GetContent(SpiderSocket sock, Header header) throws IOException {
		return http10GetContent(sock, header, 0);
	}

	private Page http10GetContent(SpiderSocket sock, Header header, int timeout) throws IOException {
		sock.getRealSock().setSoTimeout(timeout);
		if (header.getStatusCode() != 200 || !header.getContentType().startsWith("text/html"))
			return new Page(header, EMPTY_CONTENT);

		final byte[] buf = new byte[BUF_SIZE];
		ByteArrayOutputStream content = new ByteArrayOutputStream(BUF_SIZE);

		InputStream inputStream = sock.getInput();
		int bytesRead;
		while ((bytesRead=inputStream.read(buf)) != -1);
			content.write(buf, 0, bytesRead);

		return new Page(header, content.toString());
	}

	private List<InvalidLink> invalidLinks(Link link) {
		Page page;
		synchronized (this.invalids) {
			try {
				String noAnchorLinkTo = link.getNoAnchorLinkTo();

				// Se o link for para um provável html, então faz um GET
				if (!NOHTML_REGEX.matcher(noAnchorLinkTo).lookingAt()) {
					page = httpGet(noAnchorLinkTo);
					if (page.getStatusCode() != 200) {
						this.invalids.add(new InvalidLink(link, page.getStatusCode()));
						return this.invalids;
					}
				}

				// Senão, se o link provavelmente não é um html, então primeiro
				// faz um HEAD para verificar isso.
				else {
					Header header = httpHead(noAnchorLinkTo);
					// Se retorna algo diferente de 200, nem mesmo verifica o content-type
					if (header.getStatusCode() != 200) {
						this.invalids.add(new InvalidLink(link, header.getStatusCode()));
						return this.invalids;
					}
					// Se o era um text/html, perdeu-se tempo consultando o head,
					// mas ainda assim, vale a pena.
					if (header.getContentType().equals("text/html"))
						page = httpGet(noAnchorLinkTo);
					else
						page = new Page(header, EMPTY_CONTENT);
				}
			} catch (IOException e) {
				// Erro de DNS
				this.invalids.add(new InvalidLink(link, 0));
				return this.invalids;
			}
		}

		for (Link found : findLinks(page.getContent())) {
			synchronized (this.viewed) {
				if (this.viewed.contains(found.getNoAnchorLinkTo()))
					continue;
				else
					this.viewed.add(found.getNoAnchorLinkTo());
			}

			try {
				if (found.getLinkTo().startsWith(this.baseAddress))
					invalidLinks(found);  // chamada recursiva
				else {
					int code = httpHead(found.getNoAnchorLinkTo()).getStatusCode();
					if (code != 200)
						synchronized (this.invalids) {
							this.invalids.add(new InvalidLink(found, code));
						}
				}
			} catch (IOException e) {
				// Erro de rede (DNS, etc)
				synchronized (this.invalids) {
					this.invalids.add(new InvalidLink(found, 0));
				}
			}
		}

		return this.invalids;
	}

	public List<InvalidLink> invalidLinks() {
		this.viewed.add(this.baseAddress);
		return this.invalidLinks(new Link("<base>", this.baseAddress, 0));
	}

	public static void main(String[] args) throws IOException {
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
			Link link = invalid.getLink();
			System.out.println(String.format("%s %03d %s %d",
					link.getLinkTo(), invalid.getStatusCode(), link.getPageUrl(), link.getLine()));
		}

		System.out.println("TIME " + (System.currentTimeMillis() - startTime));
	}

}
