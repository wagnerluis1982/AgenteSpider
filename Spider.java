import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Spider {
	protected static final Pattern HREF_REGEX = Pattern.compile(
			"href=['\"]?([^'\" <>]*)['\"]?", Pattern.CASE_INSENSITIVE);

	protected final String address;
	protected final String host;

	public Spider(String address) {
		if (address == null)
			throw new NullPointerException();

		address = address.trim();
		if (!isValidArg(address))
			throw new IllegalArgumentException("O argumento dever ser um " +
					"endereço http válido, finalizado por /");

		this.address = address;
		this.host = getHost(address);
	}

	protected String getHost(String address) {
		return address.substring(7, address.indexOf("/", 7));
	}

	protected String getPath(String address) {
		return address.substring(address.indexOf("/", 7));
	}

	protected boolean isValidArg(final String address) {
		if (Pattern.matches("^http://[^'\" ]+/$", address))
			return true;

		return false;
	}

	/**
	 * Obtém todos os links em uma página HTML, passada como argumento através
	 * de um {@link InputStream}.
	 *
	 * @param content Página HTML
	 * @return Lista de links encontrados
	 * @throws IOException se ocorrer um erro de E/S
	 */
	protected List<Link> findLinks(final String content) throws IOException {
		final BufferedReader reader = new BufferedReader(new StringReader(content));
		final List<Link> foundLinks = new ArrayList<>();

		String line;
		for (int no = 1; (line=reader.readLine()) != null; no++) {
			final Matcher matcher = HREF_REGEX.matcher(line);
			while (matcher.find()) {
				String linkTo = this.absoluteLink(matcher.group(1));
				if (linkTo != null)
					foundLinks.add(new Link(this.address, linkTo, no));
			}
		}

		// Liberando recursos
		reader.close();

		return foundLinks;
	}

	protected String absoluteLink(String link) {
		try {
			// Link já é absoluto (somente http)
			if (link.startsWith("http:"))
				return normalizedLink(link);

			// Link relativo à raiz deve se tornar absoluto
			if (link.startsWith("/"))
				return "http://" + this.host + normalizedLink(link);

			// Links relativos
			return normalizedLink(this.address + link);
		} catch (NormalizationException e) {}

		// Outros protocolos e links mal formados não são suportados
		return null;
	}

	/**
	 * Normaliza o link, removendo todos os "./" (diretório corrente) e
	 * substituindo todos os "../" (diretório acima).
	 * @param link String contendo o link a ser normalizado
	 * @return String com o link normalizado
	 */
	protected String normalizedLink(String link) throws NormalizationException {
		List<String> pieces = new LinkedList<>(Arrays.asList(link.split("/+")));
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

	protected Page httpGet(String address) throws IOException {
		// Conexão
		String host = getHost(address);
		Socket socket = new Socket(host, 80);
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
			buffer.close();
			return new Page(code, null);
		}

		// Posicionando o buffer na posição do conteúdo
		while (buffer.readLine().length() != 0);

		// Obtendo o conteúdo
		StringBuilder content = new StringBuilder();
		String line;
		while ((line=buffer.readLine()) != null)
			content.append(line);

		// Libera recurso
		buffer.close();

		return new Page(code, content.toString());
	}

	protected List<InvalidLink> invalidLinks(Link link) {
		List<InvalidLink> invalids = new ArrayList<>();

		try {
			Page page = httpGet(link.linkTo);
			if (page.code != 200) {
				invalids.add(new InvalidLink(link, page.code));
				return invalids;
			}
//			for (Link l : findLinks(page.content))
//				;
		} catch (IOException e) {
			e.printStackTrace();
		}

		return invalids;
	}

	public List<InvalidLink> invalidLinks() {
		return this.invalidLinks(new Link("<base>", this.address, 0));
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
			System.out.println(String.format("%s %d %s %d",
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
	final String content;

	public Page(int code, String content) {
		this.code = code;
		this.content = content;
	}
}

@SuppressWarnings("serial")
class NormalizationException extends Exception {
	public NormalizationException(String message) {
		super(message);
	}
}
