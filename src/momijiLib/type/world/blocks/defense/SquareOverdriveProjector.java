package momijiLib.type.world.blocks.defense;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.annotations.Annotations.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.logic.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

/**
 * 与官方 OverdriveProjector 相同，仅把圆形范围改成 RegenProjector 那样的方形范围。
 * 范围判定、绘制与统计口径均与 RegenProjector 一致：range 为方形边长（格），使用 Rect 判定与 Drawf.dashSquare 绘制。
 * 其余行为（超频增益、消耗、光照、音效、动画、存档读写）与官方一致。
 */
public class SquareOverdriveProjector extends Block{
    public TextureRegion topRegion;
    public float reload = 60f;
    /** 方形范围边长，单位：格（与 RegenProjector 的 range 约定一致）。 */
    public int range = 20;
    public float speedBoost = 1.5f;
    public float speedBoostPhase = 0.75f;
    public float useTime = 400f;
    public float phaseRangeBoost = 20f;
    public boolean hasBoost = true;
    public Color baseColor = Color.valueOf("feb380");
    public Color phaseColor = Color.valueOf("ffd59e");

    public SquareOverdriveProjector(String name){
        super(name);
        solid = true;
        update = true;
        group = BlockGroup.projectors;
        hasPower = true;
        hasItems = true;
        canOverdrive = false;
        emitLight = true;
        lightRadius = 50f;
        envEnabled |= Env.space;
        ambientSound = Sounds.loopCircuit;
        ambientSoundVolume = 0.13f;
    }

    @Override
    public void load(){
        super.load();

        region = Core.atlas.find(name);
        topRegion = Core.atlas.find(name + "-top");
    }

    @Override
    public boolean outputsItems(){
        return false;
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);

        float wx = x * tilesize + offset, wy = y * tilesize + offset;

        Drawf.dashSquare(baseColor, wx, wy, range * tilesize);

        indexer.eachBlock(player.team(), Tmp.r1.setCentered(wx, wy, range * tilesize), other -> other.block.canOverdrive, other -> Drawf.selected(other, Tmp.c1.set(baseColor).a(Mathf.absin(4f, 1f))));
    }

    @Override
    public void setStats(){
        stats.timePeriod = useTime;
        super.setStats();

        stats.add(Stat.speedIncrease, "+" + (int)(speedBoost * 100f - 100) + "%");
        stats.add(Stat.range, range, StatUnit.blocks);
        stats.add(Stat.productionTime, useTime / 60f, StatUnit.seconds);

        if(hasBoost && findConsumer(f -> f instanceof ConsumeItems) instanceof ConsumeItems items){
            stats.remove(Stat.booster);
            stats.add(Stat.booster, StatValues.itemBoosters("+{0}%", stats.timePeriod, speedBoostPhase * 100f, phaseRangeBoost, items.items));
        }
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("boost", (OverdriveBuild entity) -> new Bar(() -> Core.bundle.format("bar.boost", Mathf.round(Math.max((entity.realBoost() * 100 - 100), 0))), () -> Pal.accent, () -> entity.realBoost() / (hasBoost ? speedBoost + speedBoostPhase : speedBoost)));
    }

    public class OverdriveBuild extends Building implements Ranged{
        public float heat, charge = Mathf.random(reload), phaseHeat, smoothEfficiency, useProgress;

        @Override
        public float range(){
            return range * tilesize;
        }

        @Override
        public void drawLight(){
            Drawf.light(x, y, lightRadius * smoothEfficiency, baseColor, 0.7f * smoothEfficiency);
        }

        @Override
        public void updateTile(){
            smoothEfficiency = Mathf.lerpDelta(smoothEfficiency, efficiency, 0.08f);
            heat = Mathf.lerpDelta(heat, efficiency > 0 ? 1f : 0f, 0.08f);
            charge += heat * Time.delta;

            if(hasBoost){
                phaseHeat = Mathf.lerpDelta(phaseHeat, optionalEfficiency, 0.1f);
            }

            if(charge >= reload){
                float realRange = range * tilesize + phaseHeat * phaseRangeBoost;

                charge = 0f;
                indexer.eachBlock(team, Tmp.r1.setCentered(x, y, realRange), other -> other.block.canOverdrive, other -> other.applyBoost(realBoost(), reload + 1f));
            }

            if(efficiency > 0){
                useProgress += delta();
            }

            if(useProgress >= useTime){
                consume();
                useProgress %= useTime;
            }
        }

        public float realBoost(){
            return (speedBoost + phaseHeat * speedBoostPhase) * efficiency;
        }

        @Override
        public void drawSelect(){
            float realRange = range * tilesize + phaseHeat * phaseRangeBoost;

            indexer.eachBlock(team, Tmp.r1.setCentered(x, y, realRange), other -> other.block.canOverdrive, other -> Drawf.selected(other, Tmp.c1.set(baseColor).a(Mathf.absin(4f, 1f))));

            Drawf.dashSquare(baseColor, x, y, realRange);
        }

        @Override
        public void draw(){
            super.draw();

            if(!Lod.l2) return;

            float f = 1f - (Time.time / 100f) % 1f;

            Draw.color(baseColor, phaseColor, phaseHeat);
            Draw.alpha(heat * Mathf.absin(Time.time, 50f / Mathf.PI2, 1f) * 0.5f * Lod.alpha2);
            Draw.rect(topRegion, x, y);
            Draw.alpha(Lod.alpha2);
            Lines.stroke((2f * f + 0.1f) * heat);

            float r = Math.max(0f, Mathf.clamp(2f - f * 2f) * size * tilesize / 2f - f - 0.2f), w = Mathf.clamp(0.5f - f) * size * tilesize;
            Lines.beginLine();
            for(int i = 0; i < 4; i++){
                Lines.linePoint(x + Geometry.d4(i).x * r + Geometry.d4(i).y * w, y + Geometry.d4(i).y * r - Geometry.d4(i).x * w);
                if(f < 0.5f) Lines.linePoint(x + Geometry.d4(i).x * r - Geometry.d4(i).y * w, y + Geometry.d4(i).y * r + Geometry.d4(i).x * w);
            }
            Lines.endLine(true);

            Draw.reset();
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(heat);
            write.f(phaseHeat);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            heat = read.f();
            phaseHeat = read.f();
        }
    }
}
