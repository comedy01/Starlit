package dev.starlit.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class StarlitConfig {
    public static final String FILE_NAME = "starlit.json";

    public static final int MIN_STARS = 500;
    public static final int MAX_STARS = 12000;
    public static final int STAR_STEP = 500;

    private static final Logger LOGGER = LoggerFactory.getLogger("starlit");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum AuroraMode {
        OFF, COLD, ALWAYS;

        public AuroraMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public enum AuroraColors {
        CLASSIC, VIVID, CRIMSON, ICE;

        public AuroraColors next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public enum ShaderMode {
        OFF, OVERLAY, REPLACE;

        public ShaderMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    @SerializedName("enabled")
    private boolean enabled = true;

    @SerializedName("brightness")
    private int brightness = 100;

    @SerializedName("starCount")
    private int starCount = 5000;

    @SerializedName("twinkle")
    private int twinkle = 50;

    @SerializedName("starColors")
    private boolean starColors = true;

    @SerializedName("starsAtDusk")
    private boolean starsAtDusk = true;

    @SerializedName("milkyWay")
    private int milkyWay = 100;

    @SerializedName("nebulae")
    private boolean nebulae = true;

    @SerializedName("galaxies")
    private boolean galaxies = true;

    @SerializedName("planets")
    private boolean planets = true;

    @SerializedName("moonGlow")
    private boolean moonGlow = true;

    @SerializedName("constellations")
    private boolean constellations = true;

    @SerializedName("constellationLines")
    private boolean constellationLines = false;

    @SerializedName("shootingStars")
    private int shootingStars = 50;

    @SerializedName("fireballs")
    private boolean fireballs = true;

    @SerializedName("meteorShowers")
    private int meteorShowers = 15;

    @SerializedName("comets")
    private int comets = 10;

    @SerializedName("announce")
    private boolean announce = true;

    @SerializedName("starSpikes")
    private boolean starSpikes = true;

    @SerializedName("endSky")
    private boolean endSky = true;
    @SerializedName("moddedSkies")
    private boolean moddedSkies = true;

    @SerializedName("spyglass")
    private boolean spyglass = true;

    @SerializedName("wishes")
    private boolean wishes = true;

    @SerializedName("auroraMode")
    private AuroraMode auroraMode = AuroraMode.COLD;

    @SerializedName("auroraColors")
    private AuroraColors auroraColors = AuroraColors.CLASSIC;

    @SerializedName("auroraBrightness")
    private int auroraBrightness = 100;

    @SerializedName("atmosphere")
    private boolean atmosphere = true;

    @SerializedName("sunsets")
    private boolean sunsets = true;

    @SerializedName("halos")
    private boolean halos = true;

    @SerializedName("rainbows")
    private boolean rainbows = true;

    @SerializedName("birds")
    private boolean birds = true;

    @SerializedName("shaderMode")
    private ShaderMode shaderMode = ShaderMode.REPLACE;

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public int brightness() {
        return brightness;
    }

    public void setBrightness(int value) {
        brightness = clamp(value, 10, 200);
    }

    public int starCount() {
        return starCount;
    }

    public void setStarCount(int value) {
        int clamped = clamp(value, 0, MAX_STARS);
        starCount = clamped < MIN_STARS ? 0 : Math.round(clamped / (float) STAR_STEP) * STAR_STEP;
    }

    public int twinkle() {
        return twinkle;
    }

    public void setTwinkle(int value) {
        twinkle = clamp(value, 0, 100);
    }

    public boolean starColors() {
        return starColors;
    }

    public void setStarColors(boolean value) {
        starColors = value;
    }

    public boolean starsAtDusk() {
        return starsAtDusk;
    }

    public void setStarsAtDusk(boolean value) {
        starsAtDusk = value;
    }

    public int milkyWay() {
        return milkyWay;
    }

    public void setMilkyWay(int value) {
        milkyWay = clamp(value, 0, 200);
    }

    public boolean nebulae() {
        return nebulae;
    }

    public void setNebulae(boolean value) {
        nebulae = value;
    }

    public boolean galaxies() {
        return galaxies;
    }

    public void setGalaxies(boolean value) {
        galaxies = value;
    }

    public boolean planets() {
        return planets;
    }

    public void setPlanets(boolean value) {
        planets = value;
    }

    public boolean moonGlow() {
        return moonGlow;
    }

    public void setMoonGlow(boolean value) {
        moonGlow = value;
    }

    public boolean constellations() {
        return constellations;
    }

    public void setConstellations(boolean value) {
        constellations = value;
    }

    public boolean constellationLines() {
        return constellationLines;
    }

    public void setConstellationLines(boolean value) {
        constellationLines = value;
    }

    public int shootingStars() {
        return shootingStars;
    }

    public void setShootingStars(int value) {
        shootingStars = clamp(value, 0, 100);
    }

    public boolean fireballs() {
        return fireballs;
    }

    public void setFireballs(boolean value) {
        fireballs = value;
    }

    public int meteorShowers() {
        return meteorShowers;
    }

    public void setMeteorShowers(int value) {
        meteorShowers = clamp(value, 0, 100);
    }

    public int comets() {
        return comets;
    }

    public void setComets(int value) {
        comets = clamp(value, 0, 100);
    }

    public boolean announce() {
        return announce;
    }

    public void setAnnounce(boolean value) {
        announce = value;
    }

    public boolean starSpikes() {
        return starSpikes;
    }

    public void setStarSpikes(boolean value) {
        starSpikes = value;
    }

    public boolean endSky() {
        return endSky;
    }

    public void setEndSky(boolean value) {
        endSky = value;
    }

    public boolean moddedSkies() {
        return moddedSkies;
    }

    public void setModdedSkies(boolean value) {
        moddedSkies = value;
    }

    public boolean spyglass() {
        return spyglass;
    }

    public void setSpyglass(boolean value) {
        spyglass = value;
    }

    public boolean wishes() {
        return wishes;
    }

    public void setWishes(boolean value) {
        wishes = value;
    }

    public AuroraMode auroraMode() {
        return auroraMode;
    }

    public void setAuroraMode(AuroraMode value) {
        auroraMode = value == null ? AuroraMode.COLD : value;
    }

    public AuroraColors auroraColors() {
        return auroraColors;
    }

    public void setAuroraColors(AuroraColors value) {
        auroraColors = value == null ? AuroraColors.CLASSIC : value;
    }

    public int auroraBrightness() {
        return auroraBrightness;
    }

    public void setAuroraBrightness(int value) {
        auroraBrightness = clamp(value, 10, 200);
    }

    public boolean atmosphere() {
        return atmosphere;
    }

    public void setAtmosphere(boolean value) {
        atmosphere = value;
    }

    public boolean sunsets() {
        return sunsets;
    }

    public void setSunsets(boolean value) {
        sunsets = value;
    }

    public boolean halos() {
        return halos;
    }

    public void setHalos(boolean value) {
        halos = value;
    }

    public boolean rainbows() {
        return rainbows;
    }

    public void setRainbows(boolean value) {
        rainbows = value;
    }

    public boolean birds() {
        return birds;
    }

    public void setBirds(boolean value) {
        birds = value;
    }

    public ShaderMode shaderMode() {
        return shaderMode;
    }

    public void setShaderMode(ShaderMode value) {
        shaderMode = value == null ? ShaderMode.REPLACE : value;
    }

    public void resetToDefaults() {
        StarlitConfig fresh = new StarlitConfig();
        enabled = fresh.enabled;
        brightness = fresh.brightness;
        starCount = fresh.starCount;
        twinkle = fresh.twinkle;
        starColors = fresh.starColors;
        starsAtDusk = fresh.starsAtDusk;
        milkyWay = fresh.milkyWay;
        nebulae = fresh.nebulae;
        galaxies = fresh.galaxies;
        planets = fresh.planets;
        moonGlow = fresh.moonGlow;
        constellations = fresh.constellations;
        constellationLines = fresh.constellationLines;
        shootingStars = fresh.shootingStars;
        fireballs = fresh.fireballs;
        meteorShowers = fresh.meteorShowers;
        comets = fresh.comets;
        announce = fresh.announce;
        starSpikes = fresh.starSpikes;
        endSky = fresh.endSky;
        moddedSkies = fresh.moddedSkies;
        spyglass = fresh.spyglass;
        wishes = fresh.wishes;
        auroraMode = fresh.auroraMode;
        auroraColors = fresh.auroraColors;
        auroraBrightness = fresh.auroraBrightness;
        atmosphere = fresh.atmosphere;
        sunsets = fresh.sunsets;
        halos = fresh.halos;
        rainbows = fresh.rainbows;
        birds = fresh.birds;
        shaderMode = fresh.shaderMode;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void sanitize() {
        setBrightness(brightness);
        setStarCount(starCount);
        setTwinkle(twinkle);
        setMilkyWay(milkyWay);
        setShootingStars(shootingStars);
        setMeteorShowers(meteorShowers);
        setComets(comets);
        setAuroraMode(auroraMode);
        setAuroraColors(auroraColors);
        setAuroraBrightness(auroraBrightness);
        setShaderMode(shaderMode);
    }

    public static StarlitConfig load(Path file) {
        if (!Files.isRegularFile(file)) {
            StarlitConfig fresh = new StarlitConfig();
            fresh.saveQuietly(file);
            return fresh;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            StarlitConfig loaded = GSON.fromJson(reader, StarlitConfig.class);
            if (loaded == null) {
                throw new JsonParseException("config file is empty");
            }
            loaded.sanitize();
            return loaded;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {}; using defaults. {}", file, e.toString());
            moveAside(file);
            StarlitConfig fresh = new StarlitConfig();
            fresh.saveQuietly(file);
            return fresh;
        }
    }

    public void save(Path file) throws IOException {
        Path absolute = file.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(this) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void saveQuietly(Path file) {
        try {
            save(file);
        } catch (IOException e) {
            LOGGER.warn("Could not save {}: {}", file, e.toString());
        }
    }

    private static void moveAside(Path file) {
        try {
            Files.move(
                    file,
                    file.resolveSibling(file.getFileName() + ".broken"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Could not back up unreadable config {}: {}", file, e.toString());
        }
    }
}
