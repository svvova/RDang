/*
 * Decompiled with CFR 0.152.
 */
package org.jamhardcore.dungeons;

import java.nio.file.Path;
import org.jamhardcore.dungeons.Model;
import org.jamhardcore.dungeons.SchematicCodec;

interface StructureBackend {
    public void capture(Model.Box var1, Path var2) throws Exception;

    public void paste(SchematicCodec.Image var1, Model.Box var2) throws Exception;

    public void paste(Path var1, Model.Box var2) throws Exception;
}

