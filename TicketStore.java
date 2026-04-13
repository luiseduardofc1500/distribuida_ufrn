import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class TicketStore {
    private static final int TOTAL_TICKETS = 1000;
    private final Path path;

    public TicketStore(String fileName) {
        this.path = Path.of(fileName);
    }

    public String listAvailableTickets() throws IOException {
        try (FileChannel channel = open();
             FileLock lock = channel.lock(0, Long.MAX_VALUE, true)) {

            boolean[] tickets = read();

            StringBuilder sb = new StringBuilder("[");
            for (int i = 1; i <= TOTAL_TICKETS; i++) {
                if (tickets[i]) {
                    if (sb.length() > 1) sb.append(",");
                    sb.append(i);
                }
            }
            return sb.append("]").toString();
        }
    }

    public String buyTicket(int n) throws IOException {
        if (n < 1 || n > TOTAL_TICKETS) {
            return "ERRO: ingresso invalido";
        }

        try (FileChannel channel = open();
             FileLock lock = channel.lock()) {

            boolean[] tickets = read();

            if (!tickets[n]) {
                return "ERRO: ingresso indisponivel";
            }

            tickets[n] = false;
            write(tickets);

            return "SUCESSO: ingresso " + n + " comprado";
        }
    }

    private FileChannel open() throws IOException {
        return FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE
        );
    }

    private boolean[] read() throws IOException {
        if (!Files.exists(path) || Files.size(path) == 0) {
            boolean[] def = createDefault();
            write(def);
            return def;
        }

        String content = Files.readString(path, StandardCharsets.UTF_8);
        String[] parts = content.split(",");

        boolean[] tickets = new boolean[TOTAL_TICKETS + 1];

        for (int i = 1; i <= TOTAL_TICKETS; i++) {
            tickets[i] = "1".equals(parts[i - 1]);
        }

        return tickets;
    }

    private void write(boolean[] tickets) throws IOException {
        StringBuilder sb = new StringBuilder();

        for (int i = 1; i <= TOTAL_TICKETS; i++) {
            if (i > 1) sb.append(",");
            sb.append(tickets[i] ? "1" : "0");
        }

        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    private boolean[] createDefault() {
        boolean[] tickets = new boolean[TOTAL_TICKETS + 1];
        for (int i = 1; i <= TOTAL_TICKETS; i++) {
            tickets[i] = true;
        }
        return tickets;
    }
}