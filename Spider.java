import java.io.BufferedReader;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Spider {
	// Regex constants
	private static final Pattern HREF_REGEX = Pattern.compile(
			"href=\"([^'\" <>]*)\"", Pattern.CASE_INSENSITIVE);
	private static final Pattern HOST_REGEX = Pattern.compile(
			"^http://([0-9a-z.\\-]+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern PATH_REGEX = Pattern.compile(
			"^http://.+?(/.*)$", Pattern.CASE_INSENSITIVE);

	// Other constants
	private static final List<Link> NO_LINKS = Collections.emptyList();

	private final String baseAddress;
	private final String baseHost;
	private final List<InvalidLink> invalids = Collections.synchronizedList(new ArrayList<InvalidLink>());
	private final Set<String> viewed = Collections.synchronizedSet(new HashSet<String>());

	private final SpiderWorkQueue workQueue;

	// Construtor
	public Spider(String baseAddress) {
		baseAddress = baseAddress.trim();
		if (!isValidArg(baseAddress))
			throw new IllegalArgumentException("O argumento dever ser um " +
					"endereço http válido, finalizado por /");

		this.baseAddress = baseAddress;
		this.baseHost = getHost(baseAddress);

		// Calcula a quantidade máxima de tarefas possíveis dividindo a memória
		// livre por 1MB para cada thread, que já é um valor bem generoso, já
		// que quase não existem páginas com esse tamanho todo e, normalmente,
		// a maioria das requisições são HEAD.
		final int tasksByMemory = (int) (Runtime.getRuntime().freeMemory()/1048576);

		// Obtém a quantidade de núcleos e define que deve haver no máximo dez
		// tarefas para cada núcleo.
		final int tasksByCpus = Runtime.getRuntime().availableProcessors()*10;

		// Máximo de tarefas que o computador pode executar
		final int maxTasks = Math.min(tasksByMemory, tasksByCpus);

		this.workQueue = new SpiderWorkQueue(maxTasks);
	}

	private boolean isValidArg(final String address) {
		return Pattern.matches("^http://[^'\" ]+/$", address);
	}

	private String getHost(String address) {
		Matcher matcher = HOST_REGEX.matcher(address);

		if (matcher.find())
			return matcher.group(1);

		return null;
	}

	private String getAddressPath(String address) {
		Matcher matcher = PATH_REGEX.matcher(address);

		if (matcher.find())
			return matcher.group(1);

		return "/";
	}

	private class NormalizationException extends Exception {
		public NormalizationException(String message) {
			super(message);
		}
	}

	/**
	 * Normaliza o link, removendo todos os "./" (diretório corrente) e
	 * substituindo todos os "../" (diretório acima).
	 * @param link String contendo o link a ser normalizado
	 * @return String com o link normalizado
	 */
	private String normalizedLink(String link) throws NormalizationException {
		// Se o link for apenas a barra indicando a raiz, não faz nada
		if (link.equals("/"))
			return link;

		// Link quebrado em pedaços. Ex: "http://link/to/x" => [http,link,to,x]
		LinkedList<String> pieces = new LinkedList<>(Arrays.asList(link.split("/+")));

		// Varre os pedaços ao contrário, do fim para o início
		for (Iterator<String> it = pieces.descendingIterator(); it.hasNext();) {
			String pie = it.next();
			// Em relativo para o próprio diretório, basta remover esse
			if (pie.equals("."))
				it.remove();
			// Já no relativo subindo ao pai, precisa remover esse e o próximo
			else if (pie.equals("..")) {
				it.remove();

				// Se não houver próximo, então o link não está correto
				if (!it.hasNext())
					throw new NormalizationException("Link mal formado");

				// Removendo o diretório pai
				it.next();
				it.remove();
			}
		}

		/* Detectando links mal formados */
		// (link sem host ou protocolo)
		if (link.startsWith("http:")) {
			if (!(pieces.getFirst().equals("http:") && pieces.get(1).equals(getHost(link))))
				throw new NormalizationException("Link mal formado");
		}
		// (link deixou de ser raiz)
		else if (link.startsWith("/") && !pieces.getFirst().equals(""))
			throw new NormalizationException("Link mal formado");

		/* Juntando os pedaços do link, com os ajustes necessários */
		StringBuffer path = new StringBuffer();
		Iterator<String> it = pieces.iterator();

		String piece = it.next();
		path.append(piece);
		if (piece.equals("http:"))
			path.append('/');

		while (it.hasNext()) {
			piece = it.next();
			path.append('/').append(piece);
		}

		if (link.endsWith("/"))
			path.append('/');

		return path.toString();
	}

	private String absoluteLink(String link) {
		try {
			// Link já é absoluto (somente http)
			if (link.startsWith("http:"))
				return normalizedLink(link);

			// Outros protocolos não são suportados
			if (link.contains(":"))
				return null;

			// Link relativo à raiz deve se tornar absoluto
			if (link.startsWith("/"))
				return "http://" + this.baseHost + normalizedLink(link);

			// Links relativos
			return normalizedLink(this.baseAddress + link);
		} catch (NormalizationException e) {}

		// Links mal formados
		return null;
	}

	/**
	 * Obtém todos os links em uma página HTML, passada como argumento através
	 * de um {@link BufferedReader} conectado a um {@link Socket}.
	 */
	private class FindLinks implements Iterator<Link>, Iterable<Link> {
		private BufferedReader input;
		private String address;

		private boolean looked;
		private boolean hasElem;
		private int lineNumber;
		private String line;
		private Matcher matcher;
		private String linkTo;

		public FindLinks(BufferedReader input, String address) {
			this.input = input;
			this.address = address;
			this.looked = false;
			this.hasElem = false;
			this.lineNumber = 0;
		}

		@Override
		public boolean hasNext() {
			if (looked)
				return hasElem;

			while (true) {
				// Se não houver linha, tenta ler
				if (line == null) {
					try {
						line = input.readLine();
						lineNumber++;
						matcher = HREF_REGEX.matcher(line);
					} catch (IOException | NullPointerException e) {
						looked = true;
						try {
							input.close();
						} catch (IOException e1) {
						}
						return (hasElem=false);
					}
				}

				// Se encontrou um link, retorna
				if (matcher.find()) {
					linkTo = absoluteLink(matcher.group(1));
					hasElem = linkTo != null;
					if (hasElem) {
						looked = true;
						return true;
					}
				} else {
					line = null;
				}
			}
		}

		@Override
		public Link next() {
			if (!hasNext())
				throw new NoSuchElementException("Não há mais links");

			// Indica que um possível próximo link bem formado ainda não foi olhado
			looked = false;

			// Retorna link bem formado
			return new Link(address, linkTo, lineNumber);
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException("Dados para somente leitura");
		}

		@Override
		public Iterator<Link> iterator() {
			return this;
		}
	}

	private SpiderSocket getSpiderSocket(String host) throws IOException {
		return new SpiderSocket(new Socket(host, 80));
	}

	private Header httpHead(String address) throws IOException {
		// Conexão
		String host = getHost(address);
		address = getAddressPath(address);
		SpiderSocket sock = getSpiderSocket(host);

		// Requisição
		String requisition = String.format("HEAD %s HTTP/1.0\r\n" +
				"Host:%s\r\n" +
				"Connection: close\r\n" +
				"\r\n", address, host);
		sock.getOutput().write(requisition.getBytes());

		Header header = new Header(sock.getInput());
		sock.getRealSock().close();

		return header;
	}

	private Page httpGet(String address) throws IOException {
		// Conexão
		String addressPath = getAddressPath(address);
		SpiderSocket sock = getSpiderSocket(this.baseHost);

		// Requisição
		String requisition = String.format("GET %s HTTP/1.0\r\n" +
				"Host:%s\r\n" +
				"Connection: close\r\n" +
				"\r\n", addressPath, this.baseHost);
		sock.getOutput().write(requisition.getBytes());

		Header header = new Header(sock.getInput());

		// Se o código de status não é 200 ou o content-type não é HTML, despreza a conexão
		if (header.getStatusCode() != 200 || !header.getContentType().equals("text/html")) {
			sock.getRealSock().close();
			return new Page(header, NO_LINKS);
		}

		// Obtém a lista de links desse endereço
		Iterable<Link> links = new FindLinks(sock.getInput(), address);

		return new Page(header, links);
	}

	private class HeadRequestRunner implements Runnable {
		private Link found;

		public HeadRequestRunner(Link found) {
			this.found = found;
		}

		@Override
		public void run() {
			doHead();
		}

		private void doHead() {
			try {
				int code = httpHead(found.getLinkTo()).getStatusCode();
				if (code != 200) {
					synchronized (invalids) {
						invalids.add(new InvalidLink(found, code));
					}
				}
			} catch (IOException e) {
				// Erro de rede (DNS, etc)
				synchronized (invalids) {
					invalids.add(new InvalidLink(found, 0));
				}
			}

		}
	}

	private class GetRequestRunner implements Runnable {
		private Link link;

		public GetRequestRunner(Link link) {
			this.link = link;
		}

		@Override
		public void run() {
			doGet();
		}

		private void doGet() {
			Page page;
			try {
				page = httpGet(link.getLinkTo());

				// Se retorna algo diferente de 200, nem mesmo verifica o content-type
				if (page.getStatusCode() != 200) {
					synchronized (invalids) {
						invalids.add(new InvalidLink(link, page.getStatusCode()));
					}
					return;
				}
			} catch (IOException e) {
				// Erro de DNS
				synchronized (invalids) {
					invalids.add(new InvalidLink(link, 0));
				}
				return;
			}

			for (final Link found : page.getLinks()) {
				final String linkTo = found.getLinkTo();
				synchronized (viewed) {
					if (viewed.contains(linkTo))
						continue;
					else
						viewed.add(linkTo);
				}

				if (linkTo.startsWith(baseAddress)) {
					Runnable getRunner = new GetRequestRunner(found);
					workQueue.submit(getRunner);
				} else {
					Runnable headRunner = new HeadRequestRunner(found);
					workQueue.submit(headRunner);
				}
			}
		}
	}

	private List<InvalidLink> invalidLinks(Link link) {
		Runnable getRunner = new GetRequestRunner(link);
		try {
			this.workQueue.submit(getRunner);
			this.workQueue.executeAndWait();
		} catch (InterruptedException e) {
		}

		return this.invalids;
	}

	public List<InvalidLink> invalidLinks() {
		this.viewed.add(this.baseAddress);
		return invalidLinks(new Link("sitebase", this.baseAddress, 0));
	}

	public static void main(String[] args) throws IOException {
		String url;
		if (args.length < 1) {
			System.out.print("Digite um endereço: ");
			Scanner scanner = new Scanner(System.in);
			url = scanner.nextLine();
		} else {
			url = args[0];
		}

		// Inicia contagem só após obter o argumento
		long startTime = System.currentTimeMillis();

		// Instancia o Agente Spider
		Spider spider = null;
		try {
			spider = new Spider(url);
		} catch (IllegalArgumentException e) {
			System.out.println(e.getMessage());
			System.exit(1);
		}

		// Coleta todos os links inválidos e exibe no stdout
		for (InvalidLink invalid : spider.invalidLinks()) {
			Link link = invalid.getLink();
			System.out.println(String.format("%s %03d %s %d",
					link.getLinkTo(), invalid.getStatusCode(), link.getPageUrl(), link.getLine()));
		}

		// Exibe tempo de processamento em milissegundos
		System.out.println("TIME " + (System.currentTimeMillis() - startTime));
	}

}
