
class InvalidLink {
	private final Link link;
	private final int statusCode;

	public InvalidLink(Link link, int code) {
		this.link = link;
		this.statusCode = code;
	}

	public Link getLink() {
		return link;
	}

	public int getStatusCode() {
		return statusCode;
	}
}