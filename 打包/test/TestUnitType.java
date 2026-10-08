package momijiLib.test;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.math.Mathf;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.content.UnitTypes;
import mindustry.entities.abilities.EnergyFieldAbility;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.ContinuousBulletType;
import mindustry.entities.bullet.ExplosionBulletType;
import mindustry.entities.effect.MultiEffect;
import mindustry.entities.effect.SeqEffect;
import mindustry.entities.effect.WaveEffect;
import mindustry.entities.part.*;
import mindustry.gen.ElevationMoveUnit;
import mindustry.gen.Sounds;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.type.unit.MissileUnitType;
import mindustry.world.meta.BlockFlag;
import momijiLib.entities.part.MomijiPartProgress;
import momijiLib.entities.part.StatefulRegionPart;
import momijiLib.type.weapons.StatefulWeapon;

import static momijiLib.LibLoad.addModName;

/**
 * 掌管测试用单位.
 *
 * @see UnitTypes
 * @since 2026-10-04
 */
public class TestUnitType{
    public static UnitType hurricane, hurricaneMissile;

    public static void load(){

        hurricane = new UnitType("hurricane"){{
            constructor = ElevationMoveUnit::create;
            health = 9000f;
            armor = 10f;
            maxRange = 520f;
            hitSize = 45f;
            itemCapacity = 160;
            canDrown = false;
            drownTimeMultiplier = 99999f; // -
            fogRadius = 240f;
            researchCostMultiplier = 0.05f;
            flying = false; // -
            speed = 1.5f;
            strafePenalty = 1f;
            rotateSpeed = 2f;
            drag = 0.04f;
            accel = 0.04f;
            hovering = true;
            groundLayer = 70f;
            lowAltitude = true;
            shadowElevation = 0.2f;
            setEnginesMirror(
                    new UnitType.UnitEngine(4.5f, -19.5f, 3f, -70f),
                    new UnitType.UnitEngine(-4.5f, -19.5f, 3f, -110f)
            );
            engineOffset = 22f;
            engineSize = 4f;
            useEngineElevation = false;
            lightColor = Color.valueOf("90dbff");
            deathSound = Sounds.unitExplode1;
            loopSound = Sounds.loopHover;
            loopSoundVolume = 0.6f;
            immunities.addAll(StatusEffects.burning, StatusEffects.wet, StatusEffects.tarred);
            targetFlags = new BlockFlag[] {BlockFlag.drill, BlockFlag.battery, BlockFlag.reactor, BlockFlag.repair, BlockFlag.storage};

            parts.addAll(
                    new HoverPart(){{
                        radius = 13f;
                        x = 18.5f;
                        y = -18.5f;
                        rotation = 45f;
                        phase = 65f;
                        stroke = 4f;
                        minStroke = 0f;
                        sides = 4; // -
                        color = Color.valueOf("90dbff");
                        mirror = true;
                        layerOffset = -0.001f;
                    }},
                    new HoverPart(){{
                        radius = 7f;
                        x = 14f;
                        y = 16f;
                        rotation = 45f;
                        phase = 65f;
                        stroke = 2.5f;
                        minStroke = 0f;
                        sides = 4; // -
                        color = Color.valueOf("90dbff");
                        mirror = true;
                        layerOffset = -0.001f;
                    }},
                    new HaloPart(){{
                        tri = true;
                        shapes = 2;
                        radius = 2f;
                        triLength = 5.5f;
                        triLengthTo = 0f;
                        haloRadius = 4.9f;
                        haloRadiusTo = 3f;
                        haloRotateSpeed = -2.5f;
                        x = 0f;
                        y = 0f;
                        shapeRotation = 70f;
                        rotateSpeed = 0f;
                        color = Color.valueOf("90dbff");
                        mirror = false; // -
                        layer = Layer.effect;
                    }},
                    new HaloPart(){{
                        tri = true;
                        shapes = 2;
                        radius = 2f;
                        triLength = 0f;
                        triLengthTo = 5.5f;
                        haloRadius = 3f;
                        haloRadiusTo = 4.9f;
                        haloRotateSpeed = -7.5f;
                        x = 0f;
                        y = 0f;
                        shapeRotation = 70f;
                        rotateSpeed = 0f;
                        color = Color.valueOf("90dbff");
                        mirror = false; // -
                        layer = Layer.effect;
                    }},
                    new ShapePart(){{
                        circle = true;
                        hollow = true;
                        radius = 4f;
                        stroke = 3.5f;
                        strokeTo = 4f;
                        x = 0f;
                        y = 0f;
                        color = Color.valueOf("90dbff");
                        mirror = false; // -
                        layer = Layer.effect;
                    }},
                    new RegionPart("-glow"){{
                        outline = false;
                        blending = Blending.additive;
                        layerOffset = 0.001f;
                        x = 0f;
                        y = 0f;
                        color = mixColor = Color.valueOf("90dbffaa");
                    }}
            );

            abilities.add(new EnergyFieldAbility(20f, 30f, 128f){{
                healEffect = Fx.none;
                hitEffect = Fx.hitLaserBlast; // -
                damageEffect = Fx.chainLightning; // -
                status = StatusEffects.none;
                shootSound = Sounds.none;
                statusDuration = 20f * 60f;
                x = 0f;
                y = 0f;
                targetGround = false;
                targetAir = true; // -
                hitBuildings = false;
                maxTargets = 1024;
                healPercent = 0.1f;
                displayHeal = false;
                effectRadius = 0f;
                sectorRad = 0.2f;
                rotateSpeed = -7.5f;
                sectors = 2;
                color = Color.valueOf("90dbff");
            }});
            weapons.add(
                    new Weapon(){{
                        display = false;
                        mirror = false;
                        useAttackRange = false;
                        x = 0f;
                        y = 0f; // -
                        minWarmup = 2f;
                    }},
                    new StatefulWeapon(addModName("hurricane-gatling")){{
                        mirror = true; // -
                        alternate = false;
                        rotate = true;
                        rotateSpeed = 2f;
                        rotationLimit = 90f;
                        top = true; // -
                        layerOffset = 0.01f;
                        shootY = 22.5f;
                        x = 14f;
                        y = -5f;
                        shadow = 20f;
                        shake = 2f;
                        ejectEffect = Fx.casing2;
                        recoil = 0f;
                        recoilTime = 60f;
                        cooldownTime = 300f;
                        shootWarmupSpeed = 0.001f;
                        linearWarmup = true;
                        soundPitchMin = 0.9f;
                        soundPitchMax = 1f; // -
                        shootSound = Sounds.none;
                        shootSoundVolume = 1f; // -
                        reload = 2f;
                        inaccuracy = 1f;
                        velocityRnd = 0.1f;
                        shootCone = 361f;

                        StatefulRegionPart hurricaneTube1 = new StatefulRegionPart("-tube"){{
                            speedFunc = DrawPart.PartProgress.heat.min(DrawPart.PartProgress.constant(0.9f)).mul(Mathf.halfPi / 8f / 0.9f);
                            modAmount = Mathf.halfPi;
                            progress = MomijiPartProgress.sinOf(accumulator);
                            mirror = false; // -
                            heatProgress = DrawPart.PartProgress.warmup;
                            layerOffset = -0.0005f;
                            x = 0f;
                            y = 0f;
                            moveX = 1.5f;
                            moveY = 0f;
                            color = Color.white;
                            colorTo = Color.valueOf("dddddd");
                            under = true;
                        }};
                        parts.add(
                                hurricaneTube1,
                                new RegionPart("-tube"){{
                                    progress = MomijiPartProgress.sinOf(hurricaneTube1.accumulator.add(Mathf.halfPi));
                                    mirror = false; // -
                                    heatProgress = PartProgress.warmup;
                                    layerOffset = -0.0015f;
                                    x = 0f;
                                    y = 0f;
                                    moveX = 1.5f;
                                    moveY = 0f;
                                    color = Color.valueOf("bbbbbb");
                                    colorTo = Color.valueOf("dddddd");
                                    under = true;
                                }},
                                new RegionPart("-tube"){{
                                    progress = MomijiPartProgress.sinOf(hurricaneTube1.accumulator);
                                    mirror = false; // -
                                    heatProgress = PartProgress.warmup;
                                    layerOffset = -0.0020f;
                                    x = 0;
                                    y = 0f;
                                    moveX = -1.5f;
                                    moveY = 0f;
                                    color = Color.valueOf("bbbbbb");
                                    colorTo = Color.valueOf("dddddd");
                                    under = true;
                                }},
                                new RegionPart("-tube"){{
                                    progress = MomijiPartProgress.sinOf(hurricaneTube1.accumulator.add(Mathf.halfPi));
                                    mirror = false; // -
                                    heatProgress = PartProgress.warmup;
                                    layerOffset = -0.0010f;
                                    x = 0f;
                                    y = 0f;
                                    moveX = -1.5f;
                                    moveY = 0f;
                                    color = Color.white;
                                    colorTo = Color.valueOf("dddddd");
                                    under = true;
                                }}
                        );

                        bullet = new BasicBulletType(13f, 29f){{
                            backColor = Color.valueOf("90dbff");
                            frontColor = Color.white;
                            width = 6f;
                            height = 20f;
                            shrinkY = 0f;
                            lifetime = 36f;
                            maxRange = 468f;
                            rangeOverride = 468f;
                            buildingDamageMultiplier = 1.4f;
                            shieldDamageMultiplier = 1.5f;
                            pierceArmor = true;
                            pierce = true;
                            pierceBuilding = false;
                            pierceCap = 3;
                            knockback = 4f;
                            impact = true;
                            status = StatusEffects.none;
                            statusDuration = 3f * 60f;
                            hitEffect = new MultiEffect(
                                    Fx.hitBulletColor,
                                    new WaveEffect(){{
                                        colorTo = lightColor = Color.valueOf("90dbff");
                                        sizeTo = 24f;
                                        sides = 4;
                                        strokeFrom = 4f;
                                        lifetime = 5f;
                                    }}
                                    /*
                                    new SeqEffect(new WaveEffect() {{
                                        colorTo = lightColor = Color.valueOf("90dbff");
                                        sizeTo = 24f;
                                        sides = 4;
                                        strokeFrom = 4f;
                                        lifetime = 5f;
                                    }}
                                    )
                                    */
                            );
                            hitColor = lightColor = Color.valueOf("90dbff");
                            despawnEffect = Fx.hitBulletColor;
                            shootEffect = Fx.shootBigColor;
                            smokeEffect = Fx.colorSpark;
                        }};
                    }},
                    new Weapon(){{
                        display = false;
                        alternate = false;
                        useAttackRange = false;
                        rotate = true;
                        rotateSpeed = 2f;
                        rotationLimit = 90f;
                        shootY = 17.5f; // 22.5
                        x = 14f;
                        y = -5f;
                        minWarmup = 0.99f;
                        shootWarmupSpeed = 0.001f; // overheaten
                        linearWarmup = true;
                        soundPitchMin = 1f;
                        soundPitchMax = 1f; // -
                        shootSound = Sounds.none;
                        shootSoundVolume = 1.2f;
                        initialShootSound = Sounds.none;
                        // continuous = true;
                        reload = 30f;
                        shootCone = 361f;
                        shootStatus = StatusEffects.disarmed;
                        shootStatusDuration = 7f * 60f;
                        parts.add(new EffectSpawnerPart(){{
                            y = 12.75f;
                            width = 5.5f;
                            height = 12.5f;
                            effectChance = 0.25f;
                            effect = Fx.fuelburn;
                            progress = PartProgress.warmup.mul(PartProgress.warmup)
                                                          .mul(PartProgress.warmup)
                                                          .add(PartProgress.reload);
                        }});
                        bullet = new ContinuousBulletType(){{
                            length = 0f;
                            lifetime = 420f;
                            maxRange = 468f;
                            damage = 0f;
                            collides = false; // -
                            incendAmount = 0; // -
                            hitEffect = Fx.none;
                            despawnEffect = Fx.none; // -
                            smokeEffect = Fx.smokeCloud;
                        }};
                    }},
                    new Weapon(addModName("hurricane-missile-weapon")){{
                        mirror = true; // -
                        useAttackRange = false;
                        rotate = true;
                        rotateSpeed = 5f;
                        top = false;
                        x = 8.75f;
                        y = -15f;
                        recoil = 0f;
                        shootSound = Sounds.shootHorizon;
                        predictTarget = false;
                        reload = 600f;
                        parts.add(new RegionPart("-missile"){{
                            progress = PartProgress.reload.mul(3f).add(0f);
                            color = Color.white;
                            colorTo = Color.valueOf("ffffff00");
                            under = false;
                        }});
                        bullet = new BasicBulletType(999f, 1f){{
                            rangeOverride = 520f;
                            spawnUnit = hurricaneMissile;
                            shootEffect = new WaveEffect(){{
                                colorTo = lightColor = Color.valueOf("90dbff");
                                sizeTo = 24f;
                                sides = 4;
                                strokeFrom = 4f;
                                lifetime = 15f;
                            }};
                            smokeEffect = Fx.none;
                        }};
                    }}
            );
        }};

        hurricaneMissile = new MissileUnitType("hurricane-missile"){{
            health = 100f;
            maxRange = 6f;
            hitSize = 7f;
            fogRadius = 6f;
            hidden = true; // -
            speed = 5f;
            rotateSpeed = 0.5f;
            lowAltitude = true;
            drawCell = false;
            outlineColor = Pal.darkerMetal;
            engineOffset = 7f;
            engineSize = 2.25f;
            engineColor = trailColor = lightColor = Color.valueOf("90dbff");
            trailLength = 15;
            deathSound = Sounds.none;
            loopSound = Sounds.none;
            loopSoundVolume = 0f;
            deathExplosionEffect = Fx.massiveExplosion;
            lifetime = 2.5f * 60f;
            missileAccelTime = 20f;
            immunities.addAll(StatusEffects.unmoving, StatusEffects.slow, StatusEffects.fast, StatusEffects.disarmed, StatusEffects.electrified, StatusEffects.invincible);
            weapons.add(new Weapon(){{
                mirror = false;
                shootOnDeath = true;
                shake = 10f;
                shootSound = Sounds.none;
                reload = 1f;
                shootCone = 360f;
                bullet = new ExplosionBulletType(20f, 128f){{
                    lifetime = 45f;
                    damage = 5f;
                    knockback = 4f;
                    status = StatusEffects.none;
                    statusDuration = 20f * 60f;
                    shootEffect = new MultiEffect(Fx.massiveExplosion, Fx.scatheLight, new SeqEffect(new WaveEffect(){{
                        colorTo = lightColor = Color.valueOf("90dbff");
                        sizeTo = 128f;
                        sides = 24;
                        strokeFrom = 4f;
                        lifetime = 5f;
                    }}, new WaveEffect(){{
                        colorTo = lightColor = Color.valueOf("90dbff");
                        sizeTo = 114f;
                        sides = 24;
                        strokeFrom = 6f;
                        lifetime = 5f;
                    }}, new WaveEffect(){{
                        colorTo = lightColor = Color.valueOf("90dbff");
                        sizeTo = 100f; // -
                        sides = 24;
                        strokeFrom = 6f;
                        lifetime = 5f;
                    }}, new WaveEffect(){{
                        colorTo = lightColor = Color.valueOf("90dbff");
                        sizeTo = 86f;
                        sides = 24;
                        strokeFrom = 6f;
                        lifetime = 5f;
                    }}, new WaveEffect(){{
                        colorTo = lightColor = Color.valueOf("90dbff");
                        sizeTo = 72f;
                        sides = 24;
                        strokeFrom = 6f;
                        lifetime = 5f;
                    }}));
                    lightRadius = 128f;
                    lightColor = Color.valueOf("90dbff");
                }};
            }});
            alwaysUnlocked = true;
        }};

    }
}
