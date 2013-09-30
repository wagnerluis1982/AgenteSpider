import java.util.List;


class Page {
	private final Header header;
	private final List<Link> links;

	public Page(Header header, List<Link> links) {
		this.header = header;
		this.links = links;
	}

	public int getStatusCode() {
		return header.getStatusCode();
	}

	public String getContentType() {
		return header.getContentType();
	}

	public List<Link> getLinks() {
		return links;
	}
}