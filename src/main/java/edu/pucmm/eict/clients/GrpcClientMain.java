package edu.pucmm.eict.clients;

import edu.pucmm.eict.grpc.EncuestaServiceGrpc;
import edu.pucmm.eict.grpc.FormularioDTO;
import edu.pucmm.eict.grpc.FormularioRequest;
import edu.pucmm.eict.grpc.FormularioResponse;
import edu.pucmm.eict.grpc.FormulariosResponse;
import edu.pucmm.eict.grpc.UsuarioRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
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
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public final class GrpcClientMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new GrpcClientFrame().setVisible(true);
        });
    }

    private static final class GrpcClientFrame extends JFrame {
        private final JTextField hostField = new JTextField("localhost", 18);
        private final JTextField portField = new JTextField("50051", 8);
        private final JLabel statusLabel = new JLabel("Desconectado");

        private final JTextField listarUsuarioField = new JTextField("admin@encuestas.local", 22);
        private final DefaultTableModel listTableModel = new DefaultTableModel(
                new String[]{"id", "nombre", "sector", "nivelEscolar", "usuarioRegistro", "latitud", "longitud"},
                0
        );

        private final JTextField nombreField = new JTextField("", 18);
        private final JTextField sectorField = new JTextField("", 18);
        private final JComboBox<String> nivelField = new JComboBox<>(new String[]{"BASICO", "MEDIO", "GRADO", "POSTGRADO", "DOCTORADO"});
        private final JTextField usuarioRegistroField = new JTextField("admin@encuestas.local", 22);
        private final JTextField latField = new JTextField("19.4", 10);
        private final JTextField lonField = new JTextField("-70.6", 10);
        private final JTextField fotoPathField = new JTextField("", 26);
        private String fotoBase64;

        private final JTextArea outputArea = new JTextArea(10, 60);

        private ManagedChannel channel;
        private EncuestaServiceGrpc.EncuestaServiceBlockingStub stub;
        private String connectedHost;
        private int connectedPort;

        private GrpcClientFrame() {
            super("Cliente gRPC (no consola) - Encuestas");
            setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            setMinimumSize(new Dimension(980, 720));
            setLayout(new BorderLayout());

            fotoPathField.setEditable(false);
            outputArea.setEditable(false);
            outputArea.setLineWrap(true);
            outputArea.setWrapStyleWord(true);

            add(buildTopPanel(), BorderLayout.NORTH);
            add(buildTabs(), BorderLayout.CENTER);

            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e) {
                    shutdownChannel();
                }
            });
        }

        private JPanel buildTopPanel() {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4, 4, 4, 4);
            c.anchor = GridBagConstraints.WEST;

            c.gridx = 0;
            c.gridy = 0;
            panel.add(new JLabel("Host:"), c);
            c.gridx = 1;
            panel.add(hostField, c);

            c.gridx = 2;
            panel.add(new JLabel("Port:"), c);
            c.gridx = 3;
            panel.add(portField, c);

            JButton connectButton = new JButton("Conectar");
            connectButton.addActionListener(e -> runConnect());
            c.gridx = 4;
            panel.add(connectButton, c);

            JButton disconnectButton = new JButton("Desconectar");
            disconnectButton.addActionListener(e -> {
                shutdownChannel();
                statusLabel.setText("Desconectado");
                appendOutput("Canal cerrado");
            });
            c.gridx = 5;
            panel.add(disconnectButton, c);

            c.gridx = 0;
            c.gridy = 1;
            panel.add(new JLabel("Estado:"), c);
            c.gridx = 1;
            c.gridwidth = 4;
            panel.add(statusLabel, c);

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
            top.add(new JLabel("usuario_id (email o id):"), c);
            c.gridx = 1;
            top.add(listarUsuarioField, c);

            JButton listarButton = new JButton("Listar (gRPC)");
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
            form.add(new JLabel("Usuario registro (email):"), c);
            c.gridx = 1;
            form.add(usuarioRegistroField, c);

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

            JButton crearButton = new JButton("Crear (gRPC)");
            crearButton.addActionListener(e -> runCreate());
            c.gridx = 1;
            c.gridy = row + 1;
            c.gridwidth = 2;
            form.add(crearButton, c);

            panel.add(form, BorderLayout.NORTH);

            JTextArea note = new JTextArea(
                    "Crea el formulario usando `EncuestaService/CrearFormulario`.\n" +
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

        private void runConnect() {
            runAsync(() -> {
                ensureChannel();
                return "Conectado a " + connectedHost + ":" + connectedPort;
            }, msg -> {
                statusLabel.setText(msg);
                appendOutput(msg);
            });
        }

        private void runList() {
            String usuarioId = listarUsuarioField.getText().trim();
            if (usuarioId.isBlank()) {
                JOptionPane.showMessageDialog(this, "usuario_id requerido", "Validacion", JOptionPane.WARNING_MESSAGE);
                return;
            }

            runAsync(() -> {
                ensureChannel();
                FormulariosResponse response = stub.listarFormularios(UsuarioRequest.newBuilder().setUsuarioId(usuarioId).build());
                return response;
            }, response -> {
                listTableModel.setRowCount(0);
                for (FormularioDTO f : response.getItemsList()) {
                    listTableModel.addRow(new Object[]{
                            f.getId(),
                            f.getNombre(),
                            f.getSector(),
                            f.getNivelEscolar(),
                            f.getUsuarioRegistro(),
                            f.getLatitud(),
                            f.getLongitud()
                    });
                }
                appendOutput("Listados (gRPC): " + response.getItemsCount());
            });
        }

        private void runCreate() {
            String nombre = nombreField.getText().trim();
            String sector = sectorField.getText().trim();
            String nivel = (String) nivelField.getSelectedItem();
            String usuarioRegistro = usuarioRegistroField.getText().trim();

            if (nombre.isBlank() || sector.isBlank() || usuarioRegistro.isBlank()) {
                JOptionPane.showMessageDialog(this, "Nombre, sector y usuarioRegistro son requeridos", "Validacion", JOptionPane.WARNING_MESSAGE);
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

            runAsync(() -> {
                ensureChannel();
                FormularioResponse response = stub.crearFormulario(FormularioRequest.newBuilder()
                        .setNombre(nombre)
                        .setSector(sector)
                        .setNivelEscolar(nivel == null ? "" : nivel)
                        .setUsuarioRegistro(usuarioRegistro)
                        .setLatitud(lat)
                        .setLongitud(lon)
                        .setFotografia(fotoBase64 == null ? "" : fotoBase64)
                        .build());
                return response;
            }, response -> appendOutput("Creado (gRPC) ok=" + response.getOk() + " id=" + response.getId()));
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

        private void ensureChannel() {
            String host = hostField.getText().trim();
            int port = parsePort(portField.getText().trim());

            if (stub != null && host.equals(connectedHost) && port == connectedPort) {
                return;
            }

            shutdownChannel();
            channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .idleTimeout(2, TimeUnit.MINUTES)
                    .build();
            stub = EncuestaServiceGrpc.newBlockingStub(channel);
            connectedHost = host;
            connectedPort = port;
        }

        private void shutdownChannel() {
            if (channel != null) {
                channel.shutdownNow();
                channel = null;
                stub = null;
            }
        }

        private int parsePort(String value) {
            try {
                int port = Integer.parseInt(value);
                if (port <= 0 || port > 65535) {
                    throw new NumberFormatException("rango");
                }
                return port;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Puerto invalido");
            }
        }

        private void appendOutput(String message) {
            outputArea.append(message + "\n");
        }

        private <T> void runAsync(Work<T> work, java.util.function.Consumer<T> onSuccess) {
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
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        String msg = cause.getMessage() != null ? cause.getMessage() : cause.toString();
                        if (cause instanceof StatusRuntimeException statusEx) {
                            msg = statusEx.getStatus().toString();
                        }
                        appendOutput("ERROR: " + msg);
                        JOptionPane.showMessageDialog(GrpcClientFrame.this, msg, "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }

        private interface Work<T> {
            T run() throws Exception;
        }
    }
}
