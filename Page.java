
class Page {
	private final Header header;
	private final SpiderSocket content;

	public Page(Header header, SpiderSocket sock) {
		this.header = header;
		this.content = sock;
	}

	public int getStatusCode() {
		return header.getStatusCode();
	}

	public String getContentType() {
		return header.getContentType();
	}

	public SpiderSocket getContent() {
		return content;
	}
}