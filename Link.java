import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Link {
	private String pageUrl;
	private String linkTo;
	private String noAnchorLinkTo;
	private int line;

	private static Pattern NOANCHOR_REGEX = Pattern.compile("^(.*?)#");

	public Link(final String pageUrl, final String linkTo, final int line) {
		this.pageUrl = pageUrl;
		this.linkTo = linkTo;
		this.line = line;
	}

	public String getPageUrl() {
		return pageUrl;
	}

	public String getLinkTo() {
		return linkTo;
	}

	public String getNoAnchorLinkTo() {
		if (this.noAnchorLinkTo == null) {
			Matcher matcher = NOANCHOR_REGEX.matcher(this.linkTo);
			this.noAnchorLinkTo = matcher.find() ? matcher.group(1) : this.linkTo;
		}

		return this.noAnchorLinkTo;
	}

	public int getLine() {
		return line;
	}
}