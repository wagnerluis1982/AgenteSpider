import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


class Header {
	public static String HTTP_VERSION = "version";
	public static String STATUS_CODE = "code";
	public static String CONTENT_TYPE = "ctype";
	public static String CONTENT_LENGTH = "clength";
	public static String CONNECTION = "connection";
	public static String TRANSFER_ENCODING = "tencoding";

	private static Map<String, Object> DEFAULT_FIELDS = new HashMap<>();
	static {
		DEFAULT_FIELDS.put(HTTP_VERSION, "");
		DEFAULT_FIELDS.put(STATUS_CODE, 0);
		DEFAULT_FIELDS.put(CONTENT_TYPE, "");
		DEFAULT_FIELDS.put(CONTENT_LENGTH, -1);
		DEFAULT_FIELDS.put(CONNECTION, "");
		DEFAULT_FIELDS.put(TRANSFER_ENCODING, "");
		DEFAULT_FIELDS = Collections.unmodifiableMap(DEFAULT_FIELDS);
	}

	private Map<String, Object> fields;

	public Header(Map<String, Object> headerFields) {
		if (headerFields == null)
			this.fields = DEFAULT_FIELDS;
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

	public int getContentLength() {
		Object clength = this.fields.get(CONTENT_LENGTH);
		if (clength == null) {
			clength = -1;
			this.fields.put(CONTENT_LENGTH, clength);
		}

		return (int) clength;
	}

	public boolean isChunked() {
		Object tencoding = this.fields.get(TRANSFER_ENCODING);
		if (tencoding == null)
			return false;

		return tencoding.equals("chunked");
	}
}
