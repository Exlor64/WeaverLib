package dev.weaverlib.client;

import dev.weaverlib.rune.AonRecognition.Template;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

final class RuneGuide {
    private static final System.Logger LOGGER = System.getLogger(RuneGuide.class.getName());
    private final Path path;
    private final List<Template> catalog;
    private final List<Template> learned = new ArrayList<>();
    private int selected;

    RuneGuide(Path path, List<Template> catalog) {
        this.path = path;
        this.catalog = catalog;
        try {
            if (Files.exists(path)) {
                Files.readAllLines(path).forEach(this::add);
            }
        } catch (IOException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not open the WeaverLib rune guide", error);
        }
    }

    Template selected() {
        return learned.isEmpty() ? null : learned.get(selected);
    }

    void browse(int direction) {
        if (!learned.isEmpty()) {
            selected = Math.floorMod(selected + direction, learned.size());
        }
    }

    void learn(String id) {
        if (id == null || !add(id)) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            Path pending = Files.createTempFile(path.getParent(), "runes-", ".tmp");
            try {
                Files.write(pending, learned.stream().map(Template::id).toList());
                Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(pending);
            }
        } catch (IOException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not save the WeaverLib rune guide", error);
        }
    }

    private boolean add(String id) {
        for (Template entry : catalog) {
            if (entry.id().equals(id) && !learned.contains(entry)) {
                return learned.add(entry);
            }
        }
        return false;
    }
}
