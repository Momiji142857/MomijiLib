package momijiLib.entities.part;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.util.Nullable;
import mindustry.entities.part.RegionPart;
import mindustry.gen.Unit;
import momijiLib.type.weapons.StatefulWeapon;

/**
 * 支持动态图层与动态颜色的 {@link RegionPart}.<p>
 * {@link layerProgress} 在绘制时叠加到 {@link layerOffset}, {@link colorProgress} 在 {@link color} 与
 * {@link colorTo} 之间插值; 两者均为 null 时行为与普通 {@link RegionPart} 完全一致.<p>
 * 旋转部件所需的每单位状态由 {@link StatefulWeapon} 在 update 阶段推进,
 * 本类只负责在 draw 阶段读取 {@link #currentUnit} 对应的进度, 因此绘制结果与帧率、相机剔除无关.
 *
 * @author Momiji142857 (with DeepSeek)
 * @see StatefulWeapon
 * @since 2026-05-08
 */
public class StatefulRegionPart extends RegionPart{

    /** 由 {@link StatefulWeapon} 在 draw 前设置, draw 后清除 */
    public static @Nullable Unit currentUnit;

    //动态属性

    /** 动态 layerOffset 增量, null 时使用原值 */
    public @Nullable PartProgress layerProgress;

    /**
     * 动态颜色插值系数, null 时使用原值.
     * 在 color 与 colorTo 之间按此值 lerp, colorTo 为 null 时颜色不变.
     */
    public @Nullable PartProgress colorProgress;

    /** 动态颜色插值结果的载体, 使用自有 Color 而非共享的 Tmp.c1, 避免与官方内部绘制相互覆盖 */
    private final Color dynamicColor = new Color();

    public StatefulRegionPart(String suffix){
        super(suffix);
    }

    public StatefulRegionPart(){
        super();
    }

    /**
     * 每 tick 由 {@link StatefulWeapon} 调用一次, 用于推进本部件的每单位状态.<p>
     * 基类无状态, 默认无操作, 由需要的子类覆写.
     *
     * @param unit         当前单位
     * @param firing       武器是否处于开火状态(含蓄力等待首发的阶段)
     * @param shotInterval 该武器每发子弹之间的间隔 (tick)
     * @param delta        时间步长
     */
    public void updateState(Unit unit, boolean firing, float shotInterval, float delta){
    }

    @Override
    public void draw(PartParams params){
        if(layerProgress == null && colorProgress == null){
            super.draw(params);
            return;
        }

        //保存动态属性的原始值
        Color originalColor = color, originalColorTo = colorTo;
        float originalLayer = layerOffset;

        //动态颜色: 在 color 与 colorTo 之间插值, 插值结果写入自有临时色后只以单色绘制
        if(colorProgress != null){
            float p = Mathf.clamp(colorProgress.get(params));
            color = originalColor != null && originalColorTo != null ? dynamicColor.set(originalColor).lerp(originalColorTo, p) : originalColor;
            colorTo = null;
        }

        //动态层级
        if(layerProgress != null){
            layerOffset += layerProgress.get(params);
        }

        super.draw(params);

        //恢复原始值
        color = originalColor;
        colorTo = originalColorTo;
        layerOffset = originalLayer;
    }

    /** 浅拷贝外观属性, 不包含相位源等运行时字段 */
    public StatefulRegionPart copy(){
        StatefulRegionPart p = new StatefulRegionPart(suffix);
        copyBase(p);
        return p;
    }

    /** 拷贝 RegionPart 的全部外观属性供子炮管复用 */
    private void copyBase(StatefulRegionPart p){
        //DrawPart
        p.turretShading = turretShading;
        p.under = under;
        p.weaponIndex = weaponIndex;
        p.recoilIndex = recoilIndex;

        //形态
        p.mirror = mirror;
        p.clampProgress = clampProgress;
        p.layer = layer;
        p.layerOffset = layerOffset;
        p.heatLayerOffset = heatLayerOffset;
        p.turretHeatLayer = turretHeatLayer;
        p.outlineLayerOffset = outlineLayerOffset;
        p.x = x;
        p.y = y;
        p.xScl = xScl;
        p.yScl = yScl;
        p.rotation = rotation;
        p.originX = originX;
        p.originY = originY;
        p.moveX = moveX;
        p.moveY = moveY;
        p.growX = growX;
        p.growY = growY;
        p.moveRot = moveRot;

        //进度
        p.growProgress = growProgress;
        p.heatProgress = heatProgress;

        //绘制
        p.name = name;
        p.outline = outline;
        p.replaceOutline = replaceOutline;
        p.drawRegion = drawRegion;
        p.heatLight = heatLight;
        p.heatLightOpacity = heatLightOpacity;
        p.blending = blending;
        p.color = color;
        p.colorTo = colorTo;
        p.mixColor = mixColor;
        p.mixColorTo = mixColorTo;
        p.heatColor = heatColor;

        //注意: progress / moves / children / regions 等由构建方决定, 此处不拷贝
    }
}
