package com.moontweaker.api;

/**
 * Shared state for MoonTweaker's script stages.
 *
 * All Lua APIs are built into MoonTweaker. This class only tracks which
 * {@code lua_scripts} folder is currently being loaded, so stage-specific APIs can
 * refuse to run in the wrong place.
 */
public final class MoonTweakerApi {
    public static String currentStage = "";
    private MoonTweakerApi() {}
}
