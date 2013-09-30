import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Link {
	private String pageUrl;
	private String linkTo;
	private int line;

	private static Pattern NOANCHOR_REGEX = Pattern.compile("^(.*?)#");

	public Link(final String pageUrl, final String linkTo, final int line) {
		this.pageUrl = pageUrl;
		setLinkTo(linkTo);
		this.line = line;
	}

	public String getPageUrl() {
		return pageUrl;
	}

	public String getLinkTo() {
		return linkTo;
	}

	private void setLinkTo(String linkTo) {
		Matcher matcher = NOANCHOR_REGEX.matcher(linkTo);
		this.linkTo = matcher.find() ? matcher.group(1) : linkTo;
	}

	public int getLine() {
		return line;
	}
}