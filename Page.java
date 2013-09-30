
class Page {
	private final Header header;
	private final CharSequence content;

	public Page(Header header, CharSequence content) {
		this.header = header;
		this.content = content;
	}

	public int getStatusCode() {
		return header.getStatusCode();
	}

	public String getContentType() {
		return header.getContentType();
	}

	public CharSequence getContent() {
		return content;
	}
}