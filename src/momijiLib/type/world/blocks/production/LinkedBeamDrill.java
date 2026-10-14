package momijiLib.type.world.blocks.production;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Geometry;
import arc.math.geom.Point2;
import arc.struct.EnumSet;
import arc.struct.ObjectFloatMap;
import arc.struct.Seq;
import arc.util.*;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Sounds;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Lod;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.ui.Bar;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.environment.StaticWall;
import mindustry.world.blocks.production.BeamDrill;
import mindustry.world.consumers.ConsumeLiquidBase;
import mindustry.world.meta.*;
import momijiLib.type.world.blocks.storage.LinkedBlock;

import static mindustry.Vars.*;

/**
 * 相邻同类激光钻头共享物品库存的基类.<p>
 * 继承 {@link LinkedBlock} 实现组共享物品池, 挖掘逻辑与原版 {@link BeamDrill} 一致:
 * 沿 rotation 方向扫描 range 格, 挖取 facing 墙体的 wallDrop 并直接加入物品池.<br>
 * 与原版唯一的区别是 itemCapacity 相关的判断全部改为组容量 (组长 groupSize * itemCapacity).
 *
 * @author Momiji142857
 * @see LinkedBlock
 * @see BeamDrill
 * @since 2026-10-14
 */
public class LinkedBeamDrill extends LinkedBlock{
    protected Rand rand = new Rand();

    //原版为 @Load 注解, 此处手动加载
    public TextureRegion laser;
    public TextureRegion laserEnd;
    public TextureRegion laserCenter;
    public TextureRegion laserBoost;
    public TextureRegion laserEndBoost;
    public TextureRegion laserCenterBoost;
    public TextureRegion topRegion;
    public TextureRegion glowRegion;

    public float drillTime = 200f;
    public int range = 5;
    public int tier = 1;
    public float laserWidth = 0.65f;
    /** 可选增幅消费者生效时钻速的倍率 */
    public float optionalBoostIntensity = 2.5f;

    /** 每种物品的钻速倍率, 默认 1 */
    public ObjectFloatMap<Item> drillMultipliers = new ObjectFloatMap<>();
    /** 该钻头无法挖掘的特殊豁免物品 */
    public @Nullable Item blockedItem;
    /** 该钻头无法挖掘的特殊豁免物品 */
    public @Nullable Seq<Item> blockedItems;

    public Color sparkColor = Color.valueOf("fd9e81"), glowColor = Color.white;
    public float glowIntensity = 0.2f, pulseIntensity = 0.07f;
    public float glowScl = 3f;
    public int sparks = 7;
    public float sparkRange = 10f, sparkLife = 27f, sparkRecurrence = 4f, sparkSpread = 45f, sparkSize = 3.5f;

    public Color boostHeatColor = Color.sky.cpy().mul(0.87f);
    public Color heatColor = new Color(1f, 0.35f, 0.35f, 0.9f);
    public float heatPulse = 0.3f, heatPulseScl = 7f;

    public LinkedBeamDrill(String name){
        super(name);

        rotate = true;
        drawArrow = false;
        regionRotated1 = 1;
        ignoreLineRotation = true;
        ambientSoundVolume = 0.05f;
        ambientSound = Sounds.loopMineBeam;

        group = BlockGroup.drills;
        flags = EnumSet.of(BlockFlag.drill);
        envEnabled |= Env.space;

        //与原版 BeamDrill 对齐: 这两项是 LinkedBlock 为仓储类建筑准备的, 钻头用不到
        separateItemCapacity = false;
        allowResupply = false;
    }

    @Override
    public void load(){
        super.load();

        //对应原版 @Load(value = "@-beam", fallback = "drill-laser")
        laser = Core.atlas.find(name + "-beam", "drill-laser");
        laserEnd = Core.atlas.find(name + "-beam-end", "drill-laser-end");
        laserCenter = Core.atlas.find(name + "-beam-center", "drill-laser-center");
        laserBoost = Core.atlas.find(name + "-beam-boost", "drill-laser-boost");
        laserEndBoost = Core.atlas.find(name + "-beam-boost-end", "drill-laser-boost-end");
        laserCenterBoost = Core.atlas.find(name + "-beam-boost-center", "drill-laser-boost-center");
        //对应原版 @Load("@-top") / @Load("@-glow")
        topRegion = Core.atlas.find(name + "-top");
        glowRegion = Core.atlas.find(name + "-glow");
    }

    @Override
    public void init(){
        updateClipRadius((range + 2) * tilesize);
        super.init();
        if(blockedItems == null && blockedItem != null){
            blockedItems = Seq.with(blockedItem);
        }
    }

    @Override
    public void setBars(){
        super.setBars();

        addBar("drillspeed", (LinkedBeamDrillBuild e) ->
            new Bar(() -> Core.bundle.format("bar.drillspeed", Strings.fixed(e.lastDrillSpeed * 60, 2)), () -> Pal.ammo, () -> e.warmup));
    }

    @Override
    public boolean outputsItems(){
        return true;
    }

    @Override
    public boolean rotatedOutput(int x, int y){
        return false;
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{region, topRegion};
    }

    @Override
    public void drawPlanRegion(BuildPlan plan, Eachable<BuildPlan> list){
        Draw.rect(region, plan.drawx(), plan.drawy());
        Draw.rect(topRegion, plan.drawx(), plan.drawy(), plan.rotation * 90);
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(Stat.drillTier, StatValues.drillables(drillTime, 0f, size, drillMultipliers, b ->
            (b instanceof Floor f && f.wallOre && f.itemDrop != null && f.itemDrop.hardness <= tier && (blockedItems == null || !blockedItems.contains(f.itemDrop))) ||
            (b instanceof StaticWall w && w.itemDrop != null && w.itemDrop.hardness <= tier && (blockedItems == null || !blockedItems.contains(w.itemDrop)))
        ));

        stats.add(Stat.drillSpeed, 60f / drillTime * size, StatUnit.itemsSecond);

        if(optionalBoostIntensity != 1 && findConsumer(f -> f instanceof ConsumeLiquidBase && f.booster) instanceof ConsumeLiquidBase consBase){
            stats.replace(Stat.booster,
                StatValues.speedBoosters("{0}" + StatUnit.timesSpeed.localized(),
                consBase.amount, optionalBoostIntensity, false, consBase::consumes)
            );
        }
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        Item item = null, invalidItem = null;
        boolean multiple = false;
        int count = 0;

        for(int i = 0; i < size; i++){
            nearbySide(x, y, rotation, i, Tmp.p1);

            int j = 0;
            Item found = null;
            for(; j < range; j++){
                int rx = Tmp.p1.x + Geometry.d4x(rotation)*j, ry = Tmp.p1.y + Geometry.d4y(rotation)*j;
                Tile other = world.tile(rx, ry);
                if(other != null && other.solid()){
                    Item drop = other.wallDrop();
                    if(drop != null){
                        if(drop.hardness <= tier && (blockedItems == null || !blockedItems.contains(drop))){
                            found = drop;
                            count++;
                        }else{
                            invalidItem = drop;
                        }
                    }
                    break;
                }
            }

            if(found != null){
                //若存在多种可挖物品, 则不显示图标
                if(item != found && item != null){
                    multiple = true;
                }
                item = found;
            }

            int len = Math.min(j, range - 1);
            Drawf.dashLine(found == null ? Pal.remove : Pal.placing,
                Tmp.p1.x * tilesize,
                Tmp.p1.y * tilesize,
                (Tmp.p1.x + Geometry.d4x(rotation)*len) * tilesize,
                (Tmp.p1.y + Geometry.d4y(rotation)*len) * tilesize
            );
        }

        if(item != null){
            float width = drawPlaceText(Core.bundle.formatFloat("bar.drillspeed", 60f / getDrillTime(item) * count, 2), x, y, valid);
            if(!multiple){
                float dx = x * tilesize + offset - width/2f - 4f, dy = y * tilesize + offset + size * tilesize / 2f + 5, s = iconSmall / 4f;
                Draw.mixcol(Color.darkGray, 1f);
                Draw.rect(item.fullIcon, dx, dy - 1, s, s);
                Draw.reset();
                Draw.rect(item.fullIcon, dx, dy, s, s);
            }
        }else if(invalidItem != null){
            drawPlaceText(Core.bundle.get("bar.drilltierreq"), x, y, false);
        }
    }

    @Override
    public boolean canPlaceOn(Tile tile, Team team, int rotation){
        for(int i = 0; i < size; i++){
            nearbySide(tile.x, tile.y, rotation, i, Tmp.p1);
            for(int j = 0; j < range; j++){
                Tile other = world.tile(Tmp.p1.x + Geometry.d4x(rotation)*j, Tmp.p1.y + Geometry.d4y(rotation)*j);
                if(other != null && other.solid()){
                    Item drop = other.wallDrop();
                    if(drop != null && drop.hardness <= tier && (blockedItems == null || !blockedItems.contains(drop))){
                        return true;
                    }
                    break;
                }
            }
        }

        return false;
    }

    public float getDrillTime(Item item){
        return drillTime / drillMultipliers.get(item, 1f);
    }

    public class LinkedBeamDrillBuild extends LinkedBuild{
        public Tile[] facing = new Tile[size];
        public Point2[] lasers = new Point2[size];
        public @Nullable Item lastItem;

        public float time;
        public float warmup, boostWarmup;
        public float lastDrillSpeed;
        public int facingAmount;

        /** 组共享物品池的容量上限 */
        public int groupCapacity(){
            return (groupLeader == null ? groupSize : groupLeader.groupSize) * itemCapacity;
        }

        @Override
        public void drawSelect(){
            drawItemSelection(lastItem);
        }

        @Override
        public boolean shouldConsume(){
            return items.total() < groupCapacity() && facingAmount > 0 && enabled;
        }

        @Override
        public void onProximityUpdate(){
            //先执行 LinkedBuild 的组合并, 再刷新光束朝向
            super.onProximityUpdate();

            //when rotated.
            updateLasers();
            updateFacing();
        }

        @Override
        public void updateTile(){
            //LinkedBuild: 同步组内 items, 输出液体
            super.updateTile();

            if(lasers[0] == null) updateLasers();

            warmup = Mathf.approachDelta(warmup, Mathf.num(efficiency > 0), 1f / 60f);

            updateFacing();

            int cap = groupCapacity();
            float multiplier = Mathf.lerp(1f, optionalBoostIntensity, optionalEfficiency);
            float drillTime = getDrillTime(lastItem);
            boostWarmup = Mathf.lerpDelta(boostWarmup, optionalEfficiency, 0.1f);
            lastDrillSpeed = (facingAmount * multiplier * timeScale()) / drillTime * efficiency;

            time += edelta() * multiplier;

            if(time >= drillTime){
                for(Tile tile : facing){
                    Item drop = tile == null ? null : tile.wallDrop();
                    if(items.total() < cap && drop != null){
                        items.add(drop, 1);
                        produced(drop);
                    }
                }
                time %= drillTime;
            }

            if(timer(timerDump, dumpTime / timeScale())){
                dump();
            }
        }

        protected void updateLasers(){
            for(int i = 0; i < size; i++){
                if(lasers[i] == null) lasers[i] = new Point2();
                nearbySide(tileX(), tileY(), rotation, i, lasers[i]);
            }
        }

        protected void updateFacing(){
            lastItem = null;
            boolean multiple = false;
            int dx = Geometry.d4x(rotation), dy = Geometry.d4y(rotation);
            facingAmount = 0;

            //update facing tiles
            for(int p = 0; p < size; p++){
                Point2 l = lasers[p];
                Tile dest = null;
                for(int i = 0; i < range; i++){
                    int rx = l.x + dx*i, ry = l.y + dy*i;
                    Tile other = world.tile(rx, ry);
                    if(other != null){
                        if(other.solid()){
                            Item drop = other.wallDrop();
                            if(drop != null && drop.hardness <= tier && (blockedItems == null || !blockedItems.contains(drop))){
                                facingAmount ++;
                                if(lastItem != drop && lastItem != null){
                                    multiple = true;
                                }
                                lastItem = drop;
                                dest = other;
                            }
                            break;
                        }
                    }
                }

                facing[p] = dest;
            }

            //存在多种物品时视为无可挖物品
            if(multiple){
                lastItem = null;
            }
        }

        @Override
        public void draw(){
            Draw.rect(block.region, x, y);
            Draw.rect(topRegion, x, y, rotdeg());

            if(isPayload()) return;

            var dir = Geometry.d4(rotation);
            int ddx = Geometry.d4x(rotation + 1), ddy = Geometry.d4y(rotation + 1);

            for(int i = 0; i < size; i++){
                Tile face = facing[i];
                if(face != null){
                    Item drop = face.wallDrop();

                    if(drop == null) continue;
                    Point2 p = lasers[i];
                    float lx = face.worldx() - (dir.x/2f)*tilesize, ly = face.worldy() - (dir.y/2f)*tilesize;

                    float width = (laserWidth + Mathf.absin(Time.time + i*5 + (id % 9)*9, glowScl, pulseIntensity)) * warmup;

                    Draw.z(Layer.power - 1);
                    Draw.mixcol(glowColor, Mathf.absin(Time.time + i*5 + id*9, glowScl, glowIntensity));
                    if(Math.abs(p.x - face.x) + Math.abs(p.y - face.y) == 0){
                        Draw.scl(width);

                        if(boostWarmup < 0.99f){
                            Draw.alpha(1f - boostWarmup);
                            Draw.rect(laserCenter, lx, ly);
                        }

                        if(boostWarmup > 0.01f){
                            Draw.alpha(boostWarmup);
                            Draw.rect(laserCenterBoost, lx, ly);
                        }

                        Draw.scl();
                    }else{
                        float lsx = (p.x - dir.x/2f) * tilesize, lsy = (p.y - dir.y/2f) * tilesize;

                        if(boostWarmup < 0.99f){
                            Draw.alpha(1f - boostWarmup);
                            Drawf.laser(laser, laserEnd, lsx, lsy, lx, ly, width);
                        }

                        if(boostWarmup > 0.001f){
                            Draw.alpha(boostWarmup);
                            Drawf.laser(laserBoost, laserEndBoost, lsx, lsy, lx, ly, width);
                        }
                    }
                    Draw.color();
                    Draw.mixcol();

                    if(Lod.l2){
                        Draw.z(Layer.effect);
                        Lines.stroke(warmup);
                        rand.setState(i, id);
                        Color col = drop.color;
                        Color spark = Tmp.c3.set(sparkColor).lerp(boostHeatColor, boostWarmup);
                        for(int j = 0; j < sparks; j++){
                            float fin = (Time.time / sparkLife + rand.random(sparkRecurrence + 1f)) % sparkRecurrence;
                            float or = rand.range(2f);
                            Tmp.v1.set(sparkRange * fin, 0).rotate(rotdeg() + rand.range(sparkSpread));

                            Color result = Tmp.c1.set(spark).lerp(col, fin);
                            Draw.color(result.r, result.g, result.b, result.a * Lod.alpha2);
                            float px = Tmp.v1.x, py = Tmp.v1.y;
                            if(fin <= 1f) Lines.lineAngle(lx + px + or * ddx, ly + py + or * ddy, Angles.angle(px, py), Mathf.slope(fin) * sparkSize);
                        }
                        Draw.reset();
                    }
                }
            }

            if(glowRegion.found()){
                Draw.z(Layer.blockAdditive);
                Draw.blend(Blending.additive);
                Draw.color(Tmp.c1.set(heatColor).lerp(boostHeatColor, boostWarmup), warmup * (heatColor.a * (1f - heatPulse + Mathf.absin(heatPulseScl, heatPulse))));
                Draw.rect(glowRegion, x, y, rotdeg());
                Draw.blend();
                Draw.color();
            }

            Draw.blend();
            Draw.reset();
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return false;
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(time);
            write.f(warmup);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 1){
                time = read.f();
                warmup = read.f();
            }
        }
    }
}
