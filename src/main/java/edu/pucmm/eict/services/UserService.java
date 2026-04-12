package edu.pucmm.eict.services;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import edu.pucmm.eict.auth.PasswordHasher;
import edu.pucmm.eict.models.Usuario;
import org.bson.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.mongodb.client.model.Filters.eq;

public class UserService {
    private static final Set<String> ROLES_VALIDOS = Set.of("ADMIN", "OPERADOR");

    private final MongoClient mongoClient;
    private final MongoCollection<Document> collection;
    private final Map<String, Usuario> inMemoryById;
    private final Map<String, Usuario> inMemoryByEmail;

    public Usuario authenticate(String email, String password) {
        if (email == null || password == null) {
            return null;
        }
        Usuario user = findByEmail(email);
        if (user == null) {
            return null;
        }
        if (!PasswordHasher.verify(password, user.getPasswordHash())) {
            return null;
        }
        return user;
    }

    public UserService() {
        String mongoUri = System.getenv("MONGO_URI");
        if (mongoUri != null && !mongoUri.isBlank()) {
            mongoClient = MongoClients.create(mongoUri);
            MongoDatabase database = mongoClient.getDatabase(System.getenv().getOrDefault("MONGO_DB", "encuestas"));
            collection = database.getCollection("usuarios");
            inMemoryById = null;
            inMemoryByEmail = null;
        } else {
            mongoClient = null;
            collection = null;
            inMemoryById = new LinkedHashMap<>();
            inMemoryByEmail = new LinkedHashMap<>();
        }

        seedDefaultsIfEmpty();
    }

    public List<Usuario> listUsers() {
        if (isMongoEnabled()) {
            List<Usuario> output = new ArrayList<>();
            for (Document doc : collection.find()) {
                output.add(fromDocument(doc));
            }
            return output;
        }
        return new ArrayList<>(inMemoryById.values());
    }

    public Usuario createUser(String nombre, String email, String password, String rol) {
        Usuario user = new Usuario();
        user.setId(UUID.randomUUID().toString());
        user.setNombre(nombre == null ? "" : nombre.trim());
        user.setEmail(normalizeEmail(email));
        user.setRol(normalizeRol(rol));
        user.setPasswordHash(PasswordHasher.hash(password));

        if (isMongoEnabled()) {
            if (collection.find(eq("email", user.getEmail())).first() != null) {
                return null;
            }
            collection.insertOne(toDocument(user));
            return user;
        }

        if (inMemoryByEmail.containsKey(user.getEmail())) {
            return null;
        }
        inMemoryById.put(user.getId(), user);
        inMemoryByEmail.put(user.getEmail(), user);
        return user;
    }

    public Usuario updateUser(String id, String nombre, String password, String rol) {
        if (id == null || id.isBlank()) {
            return null;
        }

        Usuario existing = findById(id);
        if (existing == null) {
            return null;
        }

        if (nombre != null && !nombre.isBlank()) {
            existing.setNombre(nombre.trim());
        }
        if (rol != null && !rol.isBlank()) {
            existing.setRol(normalizeRol(rol));
        }
        if (password != null && !password.isBlank()) {
            existing.setPasswordHash(PasswordHasher.hash(password));
        }

        if (isMongoEnabled()) {
            collection.replaceOne(eq("_id", id), toDocument(existing));
        } else {
            inMemoryById.put(existing.getId(), existing);
            inMemoryByEmail.put(existing.getEmail(), existing);
        }
        return existing;
    }

    public boolean deleteUser(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        if (isMongoEnabled()) {
            return collection.deleteOne(eq("_id", id)).getDeletedCount() > 0;
        }

        Usuario removed = inMemoryById.remove(id);
        if (removed != null) {
            inMemoryByEmail.remove(removed.getEmail());
        }
        return removed != null;
    }

    public Usuario findById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        if (isMongoEnabled()) {
            Document doc = collection.find(eq("_id", id)).first();
            return doc == null ? null : fromDocument(doc);
        }

        return inMemoryById.get(id);
    }

    public Usuario findByEmail(String email) {
        String normalized = normalizeEmail(email);
        if (normalized == null) {
            return null;
        }

        if (isMongoEnabled()) {
            Document doc = collection.find(eq("email", normalized)).first();
            return doc == null ? null : fromDocument(doc);
        }

        return inMemoryByEmail.get(normalized);
    }

    private void seedDefaultsIfEmpty() {
        if (isMongoEnabled()) {
            if (collection.countDocuments() > 0) {
                return;
            }
        } else {
            if (!inMemoryById.isEmpty()) {
                return;
            }
        }

        createUser("Administrador", "admin@encuestas.local", "admin123", "ADMIN");
        createUser("Digitador", "digitador@encuestas.local", "digitador123", "OPERADOR");
    }

    private boolean isMongoEnabled() {
        return collection != null;
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }

    private static String normalizeRol(String rol) {
        String normalized = (rol == null || rol.isBlank()) ? "OPERADOR" : rol.trim().toUpperCase();
        if (!ROLES_VALIDOS.contains(normalized)) {
            throw new IllegalArgumentException("Rol invalido");
        }
        return normalized;
    }

    private static Document toDocument(Usuario user) {
        return new Document("_id", user.getId())
                .append("nombre", user.getNombre())
                .append("email", user.getEmail())
                .append("rol", user.getRol())
                .append("passwordHash", user.getPasswordHash());
    }

    private static Usuario fromDocument(Document doc) {
        Usuario user = new Usuario();
        user.setId(doc.getString("_id"));
        user.setNombre(doc.getString("nombre"));
        user.setEmail(doc.getString("email"));
        user.setRol(doc.getString("rol"));
        user.setPasswordHash(doc.getString("passwordHash"));
        return user;
    }
}

