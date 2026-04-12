package edu.pucmm.eict.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.pucmm.eict.models.Formulario;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

public final class RestClientMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new RestClientFrame().setVisible(true);
        });
    }

    private static final class RestClientFrame extends JFrame {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
        private final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        private final JTextField baseUrlField = new JTextField("http://localhost:7000", 28);

        private final JTextField emailField = new JTextField("admin@encuestas.local", 22);
        private final JPasswordField passwordField = new JPasswordField("admin123", 22);
        private final JLabel sessionLabel = new JLabel("No autenticado");
        private final JTextArea tokenArea = new JTextArea(3, 60);

        private final JTextField listarUsuarioField = new JTextField("", 22);
        private final DefaultTableModel listTableModel = new DefaultTableModel(
                new String[]{"id", "nombre", "sector", "nivelEscolar", "usuarioRegistro", "latitud", "longitud", "fechaRegistro"},
                0
        );

        private final JTextField nombreField = new JTextField("", 18);
        private final JTextField sectorField = new JTextField("", 18);
        private final JComboBox<String> nivelField = new JComboBox<>(new String[]{"BASICO", "MEDIO", "GRADO", "POSTGRADO", "DOCTORADO"});
        private final JTextField latField = new JTextField("19.4", 10);
        private final JTextField lonField = new JTextField("-70.6", 10);
        private final JTextField fotoPathField = new JTextField("", 26);
        private String fotoBase64;

        private final JTextArea outputArea = new JTextArea(8, 60);

        private String token;
        private String usuarioEmail;
        private String usuarioRol;

        private RestClientFrame() {
            super("Cliente REST (JWT) - Encuestas");
            setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            setMinimumSize(new Dimension(980, 720));
            setLayout(new BorderLayout());

            tokenArea.setLineWrap(true);
            tokenArea.setWrapStyleWord(true);
            tokenArea.setEditable(false);
            tokenArea.setBorder(BorderFactory.createEtchedBorder());

            fotoPathField.setEditable(false);
            outputArea.setEditable(false);
            outputArea.setLineWrap(true);
            outputArea.setWrapStyleWord(true);

            add(buildTopPanel(), BorderLayout.NORTH);
            add(buildTabs(), BorderLayout.CENTER);
        }

        private JPanel buildTopPanel() {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4, 4, 4, 4);
            c.anchor = GridBagConstraints.WEST;

            int row = 0;
            c.gridx = 0;
            c.gridy = row;
            panel.add(new JLabel("Base URL:"), c);
            c.gridx = 1;
            panel.add(baseUrlField, c);

            JButton healthButton = new JButton("Health");
            healthButton.addActionListener(e -> runHealth());
            c.gridx = 2;
            panel.add(healthButton, c);

            row++;
            c.gridx = 0;
            c.gridy = row;
            panel.add(new JLabel("Email:"), c);
            c.gridx = 1;
            panel.add(emailField, c);

            row++;
            c.gridx = 0;
            c.gridy = row;
            panel.add(new JLabel("Password:"), c);
            c.gridx = 1;
            panel.add(passwordField, c);

            JButton loginButton = new JButton("Login");
            loginButton.addActionListener(e -> runLogin());
            c.gridx = 2;
            panel.add(loginButton, c);

            JButton logoutButton = new JButton("Logout");
            logoutButton.addActionListener(e -> logout());
            c.gridx = 3;
            panel.add(logoutButton, c);

            row++;
            c.gridx = 0;
            c.gridy = row;
            panel.add(new JLabel("Sesion:"), c);
            c.gridx = 1;
            panel.add(sessionLabel, c);

            row++;
            c.gridx = 0;
            c.gridy = row;
            c.gridwidth = 4;
            panel.add(new JScrollPane(tokenArea), c);

            return panel;
        }

        private JTabbedPane buildTabs() {
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Listar", buildListPanel());
            tabs.addTab("Crear", buildCreatePanel());
            tabs.addTab("Salida", buildOutputPanel());
            return tabs;
        }

        private JPanel buildListPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel top = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4, 4, 4, 4);
            c.anchor = GridBagConstraints.WEST;

            c.gridx = 0;
            c.gridy = 0;
            top.add(new JLabel("Usuario (email o id, opcional):"), c);

            c.gridx = 1;
            top.add(listarUsuarioField, c);

            JButton listarButton = new JButton("Listar");
            listarButton.addActionListener(e -> runList());
            c.gridx = 2;
            top.add(listarButton, c);

            panel.add(top, BorderLayout.NORTH);

            JTable table = new JTable(listTableModel);
            panel.add(new JScrollPane(table), BorderLayout.CENTER);

            return panel;
        }

        private JPanel buildCreatePanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel form = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4, 4, 4, 4);
            c.anchor = GridBagConstraints.WEST;

            int row = 0;
            c.gridx = 0;
            c.gridy = row;
            form.add(new JLabel("Nombre:"), c);
            c.gridx = 1;
            form.add(nombreField, c);

            row++;
            c.gridx = 0;
            c.gridy = row;
            form.add(new JLabel("Sector:"), c);
            c.gridx = 1;
            form.add(sectorField, c);

            row++;
            c.gridx = 0;
            c.gridy = row;
            form.add(new JLabel("Nivel escolar:"), c);
            c.gridx = 1;
            form.add(nivelField, c);

            row++;
            c.gridx = 0;
            c.gridy = row;
            form.add(new JLabel("Latitud:"), c);
            c.gridx = 1;
            form.add(latField, c);

            row++;
            c.gridx = 0;
            c.gridy = row;
            form.add(new JLabel("Longitud:"), c);
            c.gridx = 1;
            form.add(lonField, c);

            row++;
            c.gridx = 0;
            c.gridy = row;
            form.add(new JLabel("Foto (base64):"), c);
            c.gridx = 1;
            form.add(fotoPathField, c);

            JButton chooseButton = new JButton("Elegir...");
            chooseButton.addActionListener(e -> choosePhoto());
            c.gridx = 2;
            form.add(chooseButton, c);

            JButton clearButton = new JButton("Quitar");
            clearButton.addActionListener(e -> clearPhoto());
            c.gridx = 3;
            form.add(clearButton, c);

            JButton crearButton = new JButton("Crear (REST)");
            crearButton.addActionListener(e -> runCreate());
            c.gridx = 1;
            c.gridy = row + 1;
            c.gridwidth = 2;
            form.add(crearButton, c);

            panel.add(form, BorderLayout.NORTH);

            JTextArea note = new JTextArea(
                    "Crea el formulario usando `POST /api/formularios/mine` con JWT.\n" +
                            "La imagen se envia como string base64 (Data URL)."
            );
            note.setEditable(false);
            note.setOpaque(false);
            panel.add(note, BorderLayout.CENTER);

            return panel;
        }

        private JPanel buildOutputPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            panel.add(new JScrollPane(outputArea), BorderLayout.CENTER);
            return panel;
        }

        private void runHealth() {
            runAsync(() -> {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl() + "/api/health"))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return response.statusCode() + " " + response.body();
            }, this::appendOutput);
        }

        private void runLogin() {
            String email = emailField.getText().trim();
            String password = new String(passwordField.getPassword());

            runAsync(() -> {
                String payload = OBJECT_MAPPER.writeValueAsString(Map.of("email", email, "password", password));
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl() + "/api/login"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
                }

                JsonNode json = OBJECT_MAPPER.readTree(response.body());
                String token = json.path("token").asText(null);
                if (token == null || token.isBlank()) {
                    throw new IllegalStateException("Respuesta sin token");
                }

                String userEmail = json.path("usuario").path("email").asText("");
                String userRol = json.path("usuario").path("rol").asText("");

                return new Session(token, userEmail, userRol);
            }, session -> {
                this.token = session.token();
                this.usuarioEmail = session.email();
                this.usuarioRol = session.rol();
                tokenArea.setText(session.token());
                sessionLabel.setText("Autenticado: " + session.email() + " (" + session.rol() + ")");
                appendOutput("Login OK: " + session.email() + " (" + session.rol() + ")");
            });
        }

        private void logout() {
            token = null;
            usuarioEmail = null;
            usuarioRol = null;
            tokenArea.setText("");
            sessionLabel.setText("No autenticado");
            appendOutput("Logout");
        }

        private void runList() {
            requireToken();

            String usuario = listarUsuarioField.getText().trim();

            runAsync(() -> {
                String path = (usuario.isBlank())
                        ? "/api/formularios/mine"
                        : "/api/formularios?usuario=" + URLEncoder.encode(usuario, StandardCharsets.UTF_8);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl() + path))
                        .timeout(Duration.ofSeconds(20))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
                }

                return OBJECT_MAPPER.readValue(response.body(), Formulario[].class);
            }, formularios -> {
                listTableModel.setRowCount(0);
                for (Formulario f : formularios) {
                    listTableModel.addRow(new Object[]{
                            f.getId(),
                            f.getNombre(),
                            f.getSector(),
                            f.getNivelEscolar(),
                            f.getUsuarioRegistro(),
                            f.getLatitud(),
                            f.getLongitud(),
                            f.getFechaRegistro()
                    });
                }
                appendOutput("Listados: " + formularios.length);
            });
        }

        private void runCreate() {
            requireToken();

            String nombre = nombreField.getText().trim();
            String sector = sectorField.getText().trim();
            String nivel = (String) nivelField.getSelectedItem();

            if (nombre.isBlank() || sector.isBlank()) {
                JOptionPane.showMessageDialog(this, "Nombre y sector son requeridos", "Validacion", JOptionPane.WARNING_MESSAGE);
                return;
            }

            double lat;
            double lon;
            try {
                lat = Double.parseDouble(latField.getText().trim());
                lon = Double.parseDouble(lonField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Latitud/Longitud invalidas", "Validacion", JOptionPane.WARNING_MESSAGE);
                return;
            }

            Formulario form = new Formulario();
            form.setNombre(nombre);
            form.setSector(sector);
            form.setNivelEscolar(nivel);
            form.setLatitud(lat);
            form.setLongitud(lon);
            form.setFotografia(fotoBase64);

            runAsync(() -> {
                String payload = OBJECT_MAPPER.writeValueAsString(form);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl() + "/api/formularios/mine"))
                        .timeout(Duration.ofSeconds(20))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 201) {
                    throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
                }

                return response.body();
            }, body -> appendOutput("Creado OK: " + body));
        }

        private void choosePhoto() {
            JFileChooser chooser = new JFileChooser();
            int result = chooser.showOpenDialog(this);
            if (result != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File file = chooser.getSelectedFile();
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                String mime = Files.probeContentType(file.toPath());
                if (mime == null || mime.isBlank()) {
                    mime = "application/octet-stream";
                }
                fotoBase64 = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
                fotoPathField.setText(file.getAbsolutePath());
                appendOutput("Foto cargada: " + file.getName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "No se pudo leer la foto: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void clearPhoto() {
            fotoBase64 = null;
            fotoPathField.setText("");
            appendOutput("Foto removida");
        }

        private void requireToken() {
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("Debes hacer login primero");
            }
        }

        private String baseUrl() {
            String value = baseUrlField.getText().trim();
            if (value.endsWith("/")) {
                return value.substring(0, value.length() - 1);
            }
            return value;
        }

        private void appendOutput(String message) {
            outputArea.append(message + "\n");
        }

        private <T> void runAsync(Work<T> work, Result<T> onSuccess) {
            new SwingWorker<T, Void>() {
                @Override
                protected T doInBackground() throws Exception {
                    return work.run();
                }

                @Override
                protected void done() {
                    try {
                        onSuccess.accept(get());
                    } catch (Exception ex) {
                        String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        appendOutput("ERROR: " + msg);
                        JOptionPane.showMessageDialog(RestClientFrame.this, msg, "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }

        private void runAsync(Work<String> work, Result<String> onSuccess) {
            runAsync(work, onSuccess);
        }

        private void runAsync(Work<String> work, java.util.function.Consumer<String> onSuccess) {
            runAsync(work, onSuccess::accept);
        }

        private void runAsync(Work<String> work, java.util.function.Consumer<String> onSuccess, java.util.function.Consumer<Exception> onError) {
            runAsync(work, onSuccess::accept);
        }

        private record Session(String token, String email, String rol) {
        }

        private interface Work<T> {
            T run() throws Exception;
        }

        private interface Result<T> {
            void accept(T value);
        }
    }
}

