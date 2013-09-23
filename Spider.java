import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Spider {
	protected static final Pattern HREF_REGEX = Pattern.compile(
			"href=['\"]?([^'\" <>]*)['\"]?",
			Pattern.CASE_INSENSITIVE);

	protected final String address;

	public Spider(String address) {
		if (address == null)
			throw new NullPointerException();

		address = address.trim();
		if (!this.isValidArg(address))
			throw new IllegalArgumentException("O argumento dever ser um " +
					"endereço http válido, finalizado por /");

		this.address = address;
	}

	protected boolean isValidArg(String address) {
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
				foundLinks.add(new Link(this.address, matcher.group(1), no));
			}
		}

		return foundLinks;
	}

	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println("Uso: java Spider <ENDERECO_HTTP>");
			System.exit(1);
		}

		Spider spider;
		try {
			spider = new Spider(args[0]);
		} catch (IllegalArgumentException e) {
			System.out.println(e.getMessage());
			System.exit(2);
		}
	}
}

class Link {
	final String pageUrl;
	final String linkTo;
	final int line;

	public Link(String pageUrl, String linkTo, int line) {
		this.pageUrl = pageUrl;
		this.linkTo = linkTo;
		this.line = line;
	}
}
