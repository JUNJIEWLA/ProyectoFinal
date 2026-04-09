package edu.pucmm.eict.services;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import edu.pucmm.eict.models.Formulario;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.mongodb.client.model.Filters.eq;

public class FormularioService {
    private final MongoClient mongoClient;
    private final MongoCollection<Document> collection;
    private final Map<String, Formulario> inMemoryStore;

    public FormularioService() {
        String mongoUri = System.getenv("MONGO_URI");
        if (mongoUri != null && !mongoUri.isBlank()) {
            mongoClient = MongoClients.create(mongoUri);
            MongoDatabase database = mongoClient.getDatabase(System.getenv().getOrDefault("MONGO_DB", "encuestas"));
            collection = database.getCollection("formularios");
            inMemoryStore = null;
        } else {
            mongoClient = null;
            collection = null;
            inMemoryStore = new LinkedHashMap<>();
        }
    }

    public List<Formulario> listAll() {
        if (isMongoEnabled()) {
            List<Formulario> output = new ArrayList<>();
            for (Document doc : collection.find()) {
                output.add(fromDocument(doc));
            }
            return output;
        }
        return new ArrayList<>(inMemoryStore.values());
    }

    public Formulario create(Formulario formulario) {
        normalize(formulario);
        if (isMongoEnabled()) {
            collection.insertOne(toDocument(formulario));
        } else {
            inMemoryStore.put(formulario.getId(), formulario);
        }
        return formulario;
    }

    public Formulario update(String id, Formulario updated) {
        if (id == null || id.isBlank()) {
            return null;
        }
        updated.setId(id);
        normalize(updated);

        if (isMongoEnabled()) {
            if (collection.find(eq("_id", id)).first() == null) {
                return null;
            }
            collection.replaceOne(eq("_id", id), toDocument(updated));
            return updated;
        }

        if (!inMemoryStore.containsKey(id)) {
            return null;
        }
        inMemoryStore.put(id, updated);
        return updated;
    }

    public boolean delete(String id) {
        if (isMongoEnabled()) {
            return collection.deleteOne(eq("_id", id)).getDeletedCount() > 0;
        }
        return inMemoryStore.remove(id) != null;
    }

    public List<Formulario> bulkUpsert(List<Formulario> formularios) {
        List<Formulario> synced = new ArrayList<>();
        for (Formulario form : formularios) {
            normalize(form);
            if (isMongoEnabled()) {
                collection.replaceOne(eq("_id", form.getId()), toDocument(form), new ReplaceOptions().upsert(true));
            } else {
                inMemoryStore.put(form.getId(), form);
            }
            synced.add(form);
        }
        return synced;
    }

    private void normalize(Formulario formulario) {
        if (formulario.getId() == null || formulario.getId().isBlank()) {
            formulario.setId(UUID.randomUUID().toString());
        }
        if (formulario.getFechaRegistro() == null) {
            formulario.setFechaRegistro(new Date());
        }
        formulario.setUpdatedAt(System.currentTimeMillis());
    }

    private boolean isMongoEnabled() {
        return collection != null;
    }

    private Document toDocument(Formulario form) {
        return new Document("_id", form.getId())
                .append("nombre", form.getNombre())
                .append("sector", form.getSector())
                .append("nivelEscolar", form.getNivelEscolar())
                .append("usuarioRegistro", form.getUsuarioRegistro())
                .append("latitud", form.getLatitud())
                .append("longitud", form.getLongitud())
                .append("fotografia", form.getFotografia())
                .append("fechaRegistro", form.getFechaRegistro())
                .append("sincronizado", form.isSincronizado())
                .append("updatedAt", form.getUpdatedAt());
    }

    private Formulario fromDocument(Document doc) {
        Formulario form = new Formulario();
        form.setId(doc.getString("_id"));
        form.setNombre(doc.getString("nombre"));
        form.setSector(doc.getString("sector"));
        form.setNivelEscolar(doc.getString("nivelEscolar"));
        form.setUsuarioRegistro(doc.getString("usuarioRegistro"));
        form.setLatitud(doc.getDouble("latitud") != null ? doc.getDouble("latitud") : 0.0);
        form.setLongitud(doc.getDouble("longitud") != null ? doc.getDouble("longitud") : 0.0);
        form.setFotografia(doc.getString("fotografia"));
        form.setFechaRegistro(doc.getDate("fechaRegistro"));
        form.setSincronizado(Boolean.TRUE.equals(doc.getBoolean("sincronizado")));
        form.setUpdatedAt(doc.getLong("updatedAt"));
        return form;
    }
}
