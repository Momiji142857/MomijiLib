package momijiLib.entities.part;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.ObjectFloatMap;
import arc.util.Nullable;
import arc.util.Tmp;
import mindustry.entities.part.RegionPart;
import mindustry.gen.Unit;
import momijiLib.type.weapons.StatefulWeapon;

/**
 * 支持有状态累加器与动态层级的 {@link RegionPart}.<p>
 * 通过 {@link speedFunc} 定义每帧累加量, 内部以 Unit 为 key 存储独立状态.<p>
 * 通过 {@link layerProgress} 动态修改 {@link layerOffset}.<p>
 * 通过 {@link colorProgress} 在 {@link color} 与 {@link colorTo} 之间动态渐变.<p>
 * {@link speedFunc}, {@link layerProgress}, {@link colorProgress} 均为 null 时退化为普通 {@link RegionPart}.
 *
 * @author Momiji142857 (with DeepSeek)
 * @see StatefulWeapon
 * @since 2026-05-08
 */
public class StatefulRegionPart extends RegionPart{

    /** 由 {@link StatefulWeapon} 在 draw 前设置, draw 后清除 */
    public static Unit currentUnit;

    //累加器

    /** 每帧累加增量, null 时跳过 */
    public @Nullable PartProgress speedFunc;

    /** 累加取模周期, 默认 2π */
    public float modAmount = Mathf.PI2;

    /** 累加初始值 */
    public float initial = 0f;

    /** 以 Unit 为 key 的独立累加状态 */
    private final ObjectFloatMap<Unit> stateMap = new ObjectFloatMap<>();

    /** 当前帧累加结果的缓存 */
    private float cachedPhase;

    /** 累加器, 作为 PartProgress 供外部 sinOf 等函数引用 */
    public final PartProgress accumulator = p -> cachedPhase;

    //动态属性

    /** 动态 layerOffset 增量, null 时使用原值 */
    public @Nullable PartProgress layerProgress;

    /**
     * 动态颜色插值系数, null 时使用原值.
     * 在 color 与 colorTo 之间按此值 lerp, colorTo 为 null 时颜色不变.
     */
    public @Nullable PartProgress colorProgress;

    public StatefulRegionPart(String suffix){
        super(suffix);
    }

    public StatefulRegionPart(){
        super();
    }

    @Override
    public void draw(PartParams params){
        if(currentUnit == null){
            super.draw(params);
            return;
        }

        //即时清理已死亡单位
        if(!currentUnit.isAdded()){
            stateMap.remove(currentUnit, 0f);
            super.draw(params);
            return;
        }

        if(speedFunc == null && layerProgress == null && colorProgress == null){
            super.draw(params);
            return;
        }

        //累加器: 更新 cachedPhase
        if(speedFunc != null){
            float speed = speedFunc.get(params);
            float phase = stateMap.get(currentUnit, initial);
            phase = Mathf.mod(phase + speed, modAmount);
            stateMap.put(currentUnit, phase);
            cachedPhase = phase;
        }

        //保存动态属性的原始值
        Color originalColor = color, originalColorTo = colorTo;
        float originalLayer = layerOffset;

        //动态颜色
        if(colorProgress != null){
            float p = Mathf.clamp(colorProgress.get(params));
            color = originalColorTo != null ? Tmp.c1.set(originalColor).lerp(originalColorTo, p) : originalColor;
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

    /** 清理已死亡单位的累加状态, 由 StatefulWeapon 定期调用 */
    public void cleanDeadUnits(){
        if(speedFunc == null) return;
        var iter = stateMap.entries();
        while(iter.hasNext()){
            if(!iter.next().key.isAdded()){
                iter.remove();
            }
        }
    }

    /** 浅拷贝外观属性, 不包含累加器状态和动态进度字段 */
    public StatefulRegionPart copy(){
        StatefulRegionPart p = new StatefulRegionPart(suffix);
        copyBase(p);
        return p;
    }

    /** 拷贝外观属性供子炮管复用 */
    private void copyBase(StatefulRegionPart p){
        p.mirror = mirror;
        p.clampProgress = clampProgress;
        p.heatProgress = heatProgress;
        p.layerOffset = layerOffset;
        p.x = x;
        p.y = y;
        p.moveX = moveX;
        p.moveY = moveY;
        p.color = color;
        p.colorTo = colorTo;
        p.under = under;
    }
}
