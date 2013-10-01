import java.util.HashMap;
import java.util.Map;


class Header {
	public static String HTTP_VERSION = "version";
	public static String STATUS_CODE = "code";
	public static String CONTENT_TYPE = "ctype";
	public static String TRANSFER_ENCODING = "tencoding";

	private Map<String, Object> fields;

	public Header(Map<String, Object> headerFields) {
		if (headerFields == null)
			this.fields = new HashMap<>();
		else
			this.fields = new HashMap<>(headerFields);
	}

	public String getHttpVersion() {
		Object version = this.fields.get(HTTP_VERSION);
		if (version == null) {
			version = "";
			this.fields.put(HTTP_VERSION, version);
		}

		return (String) version;
	}

	public int getStatusCode() {
		Object code = this.fields.get(STATUS_CODE);
		if (code == null) {
			code = 0;
			this.fields.put(STATUS_CODE, code);
		}

		return (int) code;
	}

	public String getContentType() {
		Object ctype = this.fields.get(CONTENT_TYPE);
		if (ctype == null) {
			ctype = "";
			this.fields.put(CONTENT_TYPE, ctype);
		}

		return (String) ctype;
	}

	public String getTransferEncoding() {
		Object tencoding = this.fields.get(TRANSFER_ENCODING);
		if (tencoding == null) {
			tencoding = "";
			this.fields.put(TRANSFER_ENCODING, tencoding);
		}

		return (String) tencoding;
	}
}
