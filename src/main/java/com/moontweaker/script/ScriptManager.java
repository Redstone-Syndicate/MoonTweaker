package com.moontweaker.script;

import com.moontweaker.advancement.AdvancementApi;
import com.moontweaker.advancement.AdvancementCallbacks;
import com.moontweaker.api.ItemApi;
import com.moontweaker.api.JsonApi;
import com.moontweaker.api.MoonTweakerApi;
import com.moontweaker.api.PlayerApi;
import com.moontweaker.api.ServerApi;
import com.moontweaker.event.EventApi;
import com.moontweaker.expansion.ExpansionApi;
import com.moontweaker.loot.LootApi;
import com.moontweaker.lua.RecipeApi;
import com.moontweaker.registry.RegistryApi;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.CoroutineLib;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.jse.JseMathLib;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ScriptManager {

    public static class ScriptError {
        public final String file;
        public final String message;
        public final int line;

        public ScriptError(String file, String message) {
            this(file, message, -1);
        }

        public ScriptError(String file, String message, int line) {
            this.file = file;
            this.message = message;
            this.line = line;
        }

        @Override
        public String toString() {
            if (line > 0) {
                return file + ":" + line + ": " + message;
            }
            return file + ": " + message;
        }
    }

    public final CopyOnWriteArrayList<ScriptError> errors = new CopyOnWriteArrayList<>();
    private int loadedCount = 0;

    private final Logger logger;
    private final java.util.Map<String, LuaValue> modules = new java.util.HashMap<>();
    private int recipeCounter = 0;

    public ScriptManager(Logger logger) {
        this.logger = logger;
    }

    public int getLoadedCount() {
        return loadedCount;
    }

    public String nextRecipeId() {
        return "script_" + (recipeCounter++);
    }

    public File scriptDir() {
        File configDir = Loader.instance().getConfigDir();
        File gameDir = configDir != null ? configDir.getParentFile() : new File(".");
        return new File(gameDir, "lua_scripts");
    }

    public void loadStartup() {
        load("startup");
    }

    public void loadRegistry() {
        load("registry");
    }

    public void loadServer() {
        load("server");
    }

    public void reload() {
        errors.clear();
        recipeCounter = 0;
        loadedCount = 0;
        loadServer();
    }

    private void load(String stage) {
        modules.clear();
        MoonTweakerApi.currentStage = stage;
        switch (stage) {
            case "registry":
                RegistryApi.reset();
                break;
            case "server":
                EventApi.clear();
                LootApi.reset();
                ExpansionApi.reset();
                AdvancementApi.reset();
                AdvancementCallbacks.clear();
                break;
        }
        File dir = new File(scriptDir(), stage);
        if (!dir.isDirectory()) {
            dir.mkdirs();
            return;
        }
        Globals globals = createGlobals();
        int count = 0;
        List<File> files = new ArrayList<>();
        collectLuaFiles(dir, files);
        files.sort(Comparator.comparing(File::getPath));
        for (File file : files) {
            if (runFile(globals, file, dir)) {
                count++;
            }
        }
        loadedCount = count;
        logger.info("MoonTweaker: stage '{}' loaded, {} scripts, {} errors", stage, count, errors.size());
    }

    private void collectLuaFiles(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isFile() && child.getName().endsWith(".lua")) {
                out.add(child);
            } else if (child.isDirectory()) {
                collectLuaFiles(child, out);
            }
        }
    }

    private boolean runFile(Globals globals, File file, File root) {
        String name = "@" + root.toPath().relativize(file.toPath()).toString().replace('\\', '/');
        try {
            globals.load(readFile(file), name).call();
            return true;
        } catch (LuaError e) {
            int line = extractLineNumber(e.getMessage());
            errors.add(new ScriptError(name.substring(1), e.getMessage() != null ? e.getMessage() : "unknown Lua error", line));
            return false;
        } catch (Throwable e) {
            errors.add(new ScriptError(name.substring(1), e.toString()));
            return false;
        }
    }

    private int extractLineNumber(String errorMsg) {
        if (errorMsg == null) return -1;
        // LuaJ error format: "filename:line: message" or "filename:line: in function 'func' ... message"
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("@?[^:]+:(\\d+):");
        java.util.regex.Matcher m = p.matcher(errorMsg);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    private String readFile(File file) {
        try {
            return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Globals createGlobals() {
        Globals g = new Globals();
        g.load(new BaseLib());
        g.load(new PackageLib());
        g.load(new Bit32Lib());
        g.load(new TableLib());
        g.load(new StringLib());
        g.load(new CoroutineLib());
        g.load(new JseMathLib());
        LoadState.install(g);
        LuaC.install(g);
        for (String name : java.util.Arrays.asList("luajava", "io", "package", "debug", "dofile", "loadfile", "load")) {
            g.set(name, LuaValue.NIL);
        }
        g.set("os", safeOs());
        g.set("print", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                logger.info("[lua] {}", arg.tojstring());
                return LuaValue.NIL;
            }
        });
        g.set("require", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return requireModule(g, arg.checkjstring());
            }
        });
        g.set("log", logTable());
        g.set("ore", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return LuaValue.valueOf("ore:" + arg.checkjstring());
            }
        });
        RecipeApi.install(g, logger, this);
        EventApi.install(g, logger);
        LootApi.install(g);
        RegistryApi.install(g);
        ExpansionApi.install(g);
        AdvancementApi.install(g);
        AdvancementCallbacks.install(g, logger);
        ServerApi.install(g, logger);
        PlayerApi.install(g, logger);
        ItemApi.install(g, logger);
        JsonApi.install(g, logger);
        return g;
    }

    private LuaValue requireModule(Globals g, String name) {
        LuaValue cached = modules.get(name);
        if (cached != null) return cached;
        String rel = name.replace('.', '/') + ".lua";
        File root = new File(scriptDir(), MoonTweakerApi.currentStage);
        try {
            root = root.getCanonicalFile();
        } catch (Exception e) {
            throw new LuaError("invalid module root");
        }
        File file = new File(root, rel);
        try {
            file = file.getCanonicalFile();
        } catch (Exception e) {
            throw new LuaError("invalid module: " + name);
        }
        if (!file.toPath().startsWith(root.toPath())) throw new LuaError("invalid module: " + name);
        if (!file.isFile()) throw new LuaError("module not found: " + name);
        LuaValue result = g.load(readFile(file), "@" + rel).call();
        LuaValue module = result.isnil() ? LuaValue.TRUE : result;
        modules.put(name, module);
        return module;
    }

    private LuaValue safeOs() {
        LuaValue safe = LuaValue.tableOf();
        safe.set("time", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return LuaValue.valueOf(System.currentTimeMillis() / 1000.0);
            }
        });
        safe.set("clock", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return LuaValue.valueOf(System.nanoTime() / 1_000_000_000.0);
            }
        });
        safe.set("difftime", new org.luaj.vm2.lib.TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue a, LuaValue b) {
                return LuaValue.valueOf(a.todouble() - b.todouble());
            }
        });
        return safe;
    }

    private LuaValue logTable() {
        LuaValue t = LuaValue.tableOf();
        t.set("info", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                logger.info("[lua] {}", arg.tojstring());
                return LuaValue.NIL;
            }
        });
        t.set("warn", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                logger.warn("[lua] {}", arg.tojstring());
                return LuaValue.NIL;
            }
        });
        t.set("error", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                logger.error("[lua] {}", arg.tojstring());
                return LuaValue.NIL;
            }
        });
        return t;
    }
}