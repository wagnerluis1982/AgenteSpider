import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
		if (!this.isValidArg(address))
			throw new IllegalArgumentException("O argumento dever ser um " +
					"endereço http válido, finalizado por /");

		this.address = address;
		this.host = this.getHost(address);
	}

	protected String getHost(String address) {
		return address.substring(0, address.indexOf("/", 7));
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
	 * @param in Página HTML
	 * @return Lista de links encontrados
	 * @throws IOException se ocorrer um erro de E/S
	 */
	protected List<Link> findLinks(final InputStream in) throws IOException {
		final BufferedReader reader = new BufferedReader(new InputStreamReader(in));
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

		return foundLinks;
	}

	protected String absoluteLink(String link) {
		// Link já é http
		if (link.startsWith("http://"))
			return link;

		// Link relativo à raiz deve se tornar absoluto
		if (link.startsWith("/"))
			return this.host + link;

		// Links relativos
		if (link.startsWith("./"))
			return this.address + link.substring(2);

		// Subindo na hierarquia de diretórios (uma vez ou mais)
		if (link.startsWith("../")) {
			List<String> paths = new ArrayList<>(
					Arrays.asList(this.address.split("/")));
			try {
				while (link.startsWith("../")) {
					paths.remove(paths.size()-1);
					link = link.substring(3);
				}
			} catch (IndexOutOfBoundsException e) {
				// Link subiu demais na hierarquia
				return null;
			}
			String basePath = this.pathJoin(paths);
			if (basePath.startsWith(this.host))
				return basePath + link;
		}

		// Outros protocolos e links mal formados não são suportados
		return null;
	}

	protected String pathJoin(List<String> pieces) {
		StringBuffer path = new StringBuffer();
		for (String piece : pieces)
			path.append(piece).append("/");

		return path.toString();
	}

	protected List<InvalidLink> invalidLinks(String address) {
		return null;
	}

	public List<InvalidLink> invalidLinks() {
		return this.invalidLinks(this.address);
	}

	public static void main(String[] args) {
		long startTime = System.currentTimeMillis();

		if (args.length < 1) {
			System.out.println("Uso: java Spider <ENDERECO_HTTP>");
			System.exit(1);
		}

		Spider spider = null;
		try {
			spider = new Spider(args[0]);
		} catch (IllegalArgumentException e) {
			System.out.println(e.getMessage());
			System.exit(2);
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
