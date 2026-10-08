package momijiLib.entities.part;

import arc.math.Mathf;
import arc.util.Log;
import arc.util.Nullable;

/**
 * 可展开为多根旋转炮管的 {@link #RegionPart}.<p>
 * 通过 {@link tubeCount} 设定炮管数量.<p>
 * 通过 {@link reload} 计算炮管旋转速度, 一般与所属 Weapon.reload 保持一致.<p>
 * {@link tubeCount} <= 0 时时退化为普通的 {@link StatefulRegionPart}.
 *
 * @author Momiji142857 (with DeepSeek)
 * @see StatefulRegionPart
 * @since 2026-10-04
 */
public class TubePart extends StatefulRegionPart{

    /** 炮管旋转速度同步系数, 理论推导为 PI2 / (reload * count), 实测需再除以此系数才能与射击频率同步 */
    private static final float rotationSyncFactor = 4f;

    /** 旋转炮管数量, >0 时在 load 中展开, <=0 时作为普通 StatefulRegionPart */
    public int tubeCount = 0;

    /** 武器 reload, 用于计算炮管旋转速度, 一般与所属 Weapon.reload 保持一致 */
    public float reload = 1f;

    /** 展开后的炮管数组, tubeCount<=0 时为 null */
    private @Nullable StatefulRegionPart[] tubes;

    public TubePart(String suffix){
        super(suffix);
    }

    public TubePart(){
        super();
    }

    @Override
    public void load(String name){
        super.load(name);
        if(tubeCount > 0){
            tubes = buildTubes(tubeCount, this, reload);
            for(var tube : tubes){
                tube.load(name);
            }
        }
    }

    @Override
    public void draw(PartParams params){
        if(tubes != null){
            for(var tube : tubes){
                tube.draw(params);
            }
        }else{
            super.draw(params);
        }
    }

    @Override
    public void cleanDeadUnits(){
        super.cleanDeadUnits();
        if(tubes != null){
            for(var tube : tubes){
                tube.cleanDeadUnits();
            }
        }
    }

    /**
     * 创建一组旋转炮管, 旋转速度与射击间隔同步.<p>
     * 开火时每根炮管旋转一周的时间 = reload * count ticks, 每次射击时下一根炮管恰好转到当前位置.<p>
     * base 中仅有部分外观参数生效, 详见 {@link StatefulRegionPart#copy()}.<p>
     * speedFunc, progress, layerProgress, colorProgress 由本方法自动覆写.
     *
     * @param count  炮管数量, 有效范围 1~9, 超出时返回仅含 base 的单元素数组并在日志输出警告
     * @param base   炮管基础配置, 部分参数生效
     * @param reload 武器 reload, 用于计算旋转速度
     * @return 炮管数组, 首元素为累加器 master, 其余为只读 children
     */
    public static StatefulRegionPart[] buildTubes(int count, StatefulRegionPart base, float reload){
        if(count < 1 || count > 9){
            Log.warn("[TubePart] buildTubes: count must be in range [1, 9], got @. Returning base unchanged.", count);
            return new StatefulRegionPart[] {base};
        }

        StatefulRegionPart[] tubes = new StatefulRegionPart[count];

        float speed = Mathf.PI2 / (reload * count) / rotationSyncFactor;
        PartProgress masterAcc = null;

        for(int i = 0; i < count; i++){
            float phase = i * Mathf.PI2 / count;
            tubes[i] = base.copy();

            PartProgress acc;
            if(i == 0){
                //master: 带累加器, 转速受 heat 控制, 开火全速停火衰减
                tubes[i].speedFunc = PartProgress.heat.min(PartProgress.constant(0.9f))  // heat 上限 0.9, 避免开火瞬间波动
                                                      .mul(speed / 0.9f);
                tubes[i].modAmount = Mathf.PI2;
                acc = tubes[i].accumulator;
                masterAcc = acc;
            }else{
                //children: 共享 master 累加器, 各自偏移相位
                acc = masterAcc.add(phase);
            }

            //旋转位移与层级/颜色渐变基于同一个相位源
            tubes[i].progress = MomijiPartProgress.sinOf(acc);
            tubes[i].layerProgress = MomijiPartProgress.cosOf(acc).mul(0.0005f);
            tubes[i].colorProgress = MomijiPartProgress.cosOf(acc).mul(0.5f).add(0.5f);
        }
        return tubes;
    }
}
