package org.jamhardcore.dungeons;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

interface StructureBackend {
    void capture(Model.Box var1, Path var2) throws Exception;

    default void paste(SchematicCodec.Image image, Model.Box box) throws Exception {
        this.paste(image, box, true);
    }

    default void paste(Path file, Model.Box box) throws Exception {
        this.paste(file, box, true);
    }

    void paste(SchematicCodec.Image image, Model.Box box, boolean pasteAir) throws Exception;

    void paste(Path file, Model.Box box, boolean pasteAir) throws Exception;

    default CompletableFuture<Void> pasteAsync(SchematicCodec.Image image, Model.Box box) {
        return this.pasteAsync(image, box, true);
    }

    default CompletableFuture<Void> pasteAsync(Path file, Model.Box box) {
        return this.pasteAsync(file, box, true);
    }

    CompletableFuture<Void> pasteAsync(SchematicCodec.Image image, Model.Box box, boolean pasteAir);

    CompletableFuture<Void> pasteAsync(Path file, Model.Box box, boolean pasteAir);

    CompletableFuture<Void> captureAsync(Model.Box var1, Path var2);

    void clear(Model.Box var1) throws Exception;

    CompletableFuture<Void> clearAsync(Model.Box var1);
}
