import java.io.BufferedReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


class Header {
	private static String HTTP_VERSION = "version";
	private static String STATUS_CODE = "code";
	private static String CONTENT_TYPE = "ctype";

	private static final Pattern HEADER_REGEX = Pattern.compile(String.format(
			"^HTTP/(?<%s>[.0-9]+) (?<%s>[0-9]+)|" +
			"^content-type:\\s*(?<%s>[a-z\\-/]+)",
			HTTP_VERSION, STATUS_CODE, CONTENT_TYPE),
			Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

	private String httpVersion = "";
	private int statusCode = 0;
	private String contentType = "";

	public Header(final BufferedReader input) throws IOException {
		final StringBuilder sbHeader = new StringBuilder(500);

		String line;
		while ((line=input.readLine()) != null && line.length() > 0)
			sbHeader.append(line).append(System.lineSeparator());

		// Obtém cabeçalho
		Matcher matcher = HEADER_REGEX.matcher(sbHeader);
		String matched;
		while (matcher.find()) {
			if ((matched=matcher.group(Header.HTTP_VERSION)) != null)
				this.httpVersion = matched;
			if ((matched=matcher.group(Header.STATUS_CODE)) != null)
				this.statusCode = Integer.parseInt(matched);
			if ((matched=matcher.group(Header.CONTENT_TYPE)) != null)
				this.contentType = matched;
		}
	}

	public String getHttpVersion() {
		return this.httpVersion;
	}

	public int getStatusCode() {
		return this.statusCode;
	}

	public String getContentType() {
		return this.contentType;
	}
}
