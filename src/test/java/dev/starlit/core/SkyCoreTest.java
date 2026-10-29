package dev.starlit.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyCoreTest {
    @Test
    void catalogIsStableAndOnTheSphere() {
        List<Star> a = StarCatalog.generate(3000, true, StarCatalog.SEED);
        List<Star> b = StarCatalog.generate(3000, true, StarCatalog.SEED);
        assertEquals(3000, a.size());
        assertEquals(a, b);
        for (Star star : a) {
            float length = (float) Math.sqrt(Vec.dot(star.direction(), star.direction()));
            assertEquals(1.0F, length, 1.0e-3F);
            assertTrue(star.size() >= StarCatalog.MIN_SIZE);
            assertTrue(star.phase() >= 0.0F && star.phase() < 0.99F);
            assertTrue(star.red() <= 1.0F && star.green() <= 1.0F && star.blue() <= 1.0F);
        }
    }

    @Test
    void starsCrowdTowardTheGalacticPlane() {
        List<Star> stars = StarCatalog.generate(20000, false, 3L);
        int near = 0;
        for (Star star : stars) {
            if (Math.abs(Vec.dot(star.direction(), Galaxy.NORMAL)) < 0.15F) {
                near++;
            }
        }
        double uniformShare = 0.15;
        assertTrue(near / (double) stars.size() > uniformShare * 2.0, "only " + near + " stars near the plane");
    }

    @Test
    void mostStarsAreFaint() {
        List<Star> stars = StarCatalog.generate(10000, false, 9L);
        long bright = stars.stream().filter(star -> star.blue() > 0.6F).count();
        assertTrue(bright < 1500, bright + " bright stars");
        assertTrue(bright > 50, bright + " bright stars");
    }

    @Test
    void galacticFrameIsOrthonormalAndCoreIsOverheadAtMidnight() {
        assertEquals(0.0F, Vec.dot(Galaxy.E1, Galaxy.NORMAL), 1.0e-5F);
        assertEquals(0.0F, Vec.dot(Galaxy.E2, Galaxy.NORMAL), 1.0e-5F);
        assertEquals(0.0F, Vec.dot(Galaxy.E1, Galaxy.E2), 1.0e-5F);
        assertTrue(Galaxy.CORE[1] < -0.5F, "core y " + Galaxy.CORE[1]);
    }

    @Test
    void deepSkyAndConstellationsAreAboveTheHorizonAtMidnight() {
        for (DeepSky.Object object : DeepSky.GALAXIES) {
            assertTrue(object.direction()[1] < -0.1F);
        }
        for (Constellations.Shape shape : Constellations.SHAPES) {
            for (float[] star : Constellations.stars(shape)) {
                assertTrue(star[1] < 0.0F, shape.name() + " has a star below the horizon at midnight");
                assertEquals(1.0F, (float) Math.sqrt(Vec.dot(star, star)), 1.0e-4F);
            }
            assertFalse(Constellations.segments(shape).isEmpty());
        }
    }

    @Test
    void constellationsDoNotOverlap() {
        List<Constellations.Shape> shapes = Constellations.SHAPES;
        for (int i = 0; i < shapes.size(); i++) {
            for (int j = i + 1; j < shapes.size(); j++) {
                float angle = (float) Math.toDegrees(Math.acos(Vec.dot(shapes.get(i).center(), shapes.get(j).center())));
                assertTrue(angle > 25.0F, shapes.get(i).name() + " and " + shapes.get(j).name() + " are " + angle + " degrees apart");
            }
        }
    }

    @Test
    void eventsAreDeterministicAndScaleWithChance() {
        int showers = 0;
        int comets = 0;
        int storms = 0;
        for (long night = 0; night < 6000; night++) {
            assertEquals(SkyEvents.meteorShower(night, 15), SkyEvents.meteorShower(night, 15));
            if (SkyEvents.meteorShower(night, 15)) {
                showers++;
            }
            if (SkyEvents.comet(night, 10)) {
                comets++;
            }
            if (SkyEvents.geomagneticStorm(night)) {
                storms++;
            }
            assertFalse(SkyEvents.meteorShower(night, 0));
            assertFalse(SkyEvents.comet(night, 0));
            float activity = SkyEvents.auroraActivity(night);
            assertTrue(activity >= 0.45F && activity <= 1.0F);
        }
        assertEquals(0.075, showers / 6000.0, 0.02);
        assertEquals(0.06, comets / 6000.0, 0.025);
        assertEquals(0.08, storms / 6000.0, 0.02);
    }

    @Test
    void cometsLastAWholeCycleAndPeakInTheMiddle() {
        long night = -1;
        for (long n = 0; n < 2000; n++) {
            if (SkyEvents.comet(n, 50) && Math.floorMod(n, SkyEvents.COMET_CYCLE) == 0) {
                night = n;
                break;
            }
        }
        assertTrue(night >= 0);
        for (int i = 0; i < SkyEvents.COMET_CYCLE; i++) {
            assertTrue(SkyEvents.comet(night + i, 50));
        }
        assertTrue(SkyEvents.cometStrength(night + 3) > SkyEvents.cometStrength(night));
        float[] first = SkyEvents.cometDirection(night);
        float[] last = SkyEvents.cometDirection(night + 5);
        assertTrue(Vec.dot(first, last) > 0.95F && Vec.dot(first, last) < 0.99999F);
    }

    @Test
    void radiantsAreUpAtMidnight() {
        for (long night = 0; night < 500; night++) {
            assertTrue(SkyEvents.radiant(night)[1] < -0.3F);
        }
    }

    @Test
    void timeOfNightCoversDuskToDawn() {
        assertEquals(0.0F, SkyEvents.timeOfNight(12000, 0.0F), 1.0e-6F);
        assertEquals(0.5F, SkyEvents.timeOfNight(18000, 0.0F), 1.0e-6F);
        assertEquals(0.5F, SkyEvents.timeOfNight(24000L * 9 + 18000, 0.0F), 1.0e-6F);
        assertEquals(9L, SkyEvents.night(24000L * 9 + 18000));
        assertTrue(SkyEvents.showerStrength(0.62F) > 0.99F);
        assertEquals(0.0F, SkyEvents.showerStrength(-0.2F));
    }

    @Test
    void meteorsMoveAndFade() {
        Rng rng = new Rng(4L);
        for (int i = 0; i < 200; i++) {
            Meteor meteor = Meteor.sporadic(rng, 10.0, true);
            assertTrue(meteor.start[1] > 0.2F);
            assertEquals(0.0F, Vec.dot(meteor.start, meteor.travel), 1.0e-4F);
            float[] early = meteor.head(10.1);
            float[] late = meteor.head(10.0 + meteor.duration * 0.9);
            assertTrue(Vec.dot(early, late) < 1.0F);
            assertTrue(meteor.fade(10.0 + meteor.duration * 0.5) > 0.0F);
            assertEquals(0.0F, meteor.fade(10.0 + meteor.duration + 0.1));
            assertTrue(meteor.done(10.0 + meteor.duration + 0.1));
        }
    }

    @Test
    void showerMeteorsFlyAwayFromTheRadiant() {
        Rng rng = new Rng(8L);
        float[] radiant = Vec.normalize(Vec.of(0.2F, 0.8F, -0.3F));
        int made = 0;
        for (int i = 0; i < 300; i++) {
            Meteor meteor = Meteor.fromRadiant(rng, 0.0, radiant, false);
            if (meteor == null) {
                continue;
            }
            made++;
            float before = Vec.dot(meteor.head(0.0), radiant);
            float after = Vec.dot(meteor.head(meteor.duration), radiant);
            assertTrue(after < before, "meteor moved toward its radiant");
        }
        assertTrue(made > 200);
    }

    @Test
    void planetsStayOnTheSunsPath() {
        for (int planet = 0; planet < 3; planet++) {
            for (long night = 0; night < 100; night++) {
                float[] dir = SkyEvents.planetDirection(planet, night);
                assertNotNull(dir);
                assertTrue(Math.abs(dir[0]) < 0.06F);
            }
        }
    }
}
