
class Page {
	private final Header header;
	private final Iterable<Link> links;

	public Page(Header header, Iterable<Link> links) {
		this.header = header;
		this.links = links;
	}

	public int getStatusCode() {
		return header.getStatusCode();
	}

	public String getContentType() {
		return header.getContentType();
	}

	public Iterable<Link> getLinks() {
		return links;
	}
}
