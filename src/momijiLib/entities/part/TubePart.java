package momijiLib.entities.part;

import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Nullable;
import arc.util.Time;
import mindustry.entities.part.RegionPart;
import mindustry.gen.Unit;

import java.lang.ref.WeakReference;

/**
 * 可展开为多根旋转炮管的 {@link StatefulRegionPart}.<p>
 * 通过 {@link tubeCount} 设定炮管数量, 展开后的炮管以 base 的 (x, y) 为中心均匀分布,
 * 每根炮管的相位均分一圈.<p>
 * 把炮管组的旋转看作竖直平面上的圆(竖直分量 sin, 纵深分量 cos):
 * <ul>
 *     <li>竖直分量 sin 表现为沿 {@link RegionPart#moveX} 的往复位移, 即与中间位置的偏移量</li>
 *     <li>纵深分量 cos 表现为图层与明暗 —— 转到最前的炮管画在最上层且颜色最亮,
 *         转到最后的被压在下面且最暗, 处于最高/最低点时两者都是中间值</li>
 * </ul>
 * {@link #depthFactor} 与 {@link #shadeAmount} 取负值即可整体翻转前后方向.<p>
 * 转速为连续值: 转速系数由 {@link #spinUpSpeed} 与 {@link #spinDownSpeed} 分别按指数逼近 1 和 0,
 * 因此开火时从慢到快、停火时逐渐停止, 两个速率均与武器预热速度无关, 可 json 配置.<p>
 * 每圈耗时默认按武器属性推导: {@code tubeCount * 每发间隔}, 即每发子弹恰好前进一根炮管;
 * 设为 {@link #spinTime} > 0 可手动覆盖.<p>
 * 相位以 Unit 为 key 存储, 因此 mirror 武器的左右两侧共用同一份状态(同步旋转),
 * 且与其他部件的相位互不影响; 各部件实例以弱引用登记, 需要在入口类中把
 * {@link #clearAll()} 注册到世界加载/重置事件, 以便切换地图时立刻释放上一个世界的残留状态.<p>
 * {@link #tubeCount} <= 0 时退化为普通的 {@link StatefulRegionPart}.
 *
 * @author Momiji142857 (with DeepSeek)
 * @see StatefulRegionPart
 * @since 2026-10-04
 */
public class TubePart extends StatefulRegionPart{

    /** 旋转炮管数量, >0 时在 load 中展开, <=0 时作为普通 StatefulRegionPart */
    public int tubeCount = 0;

    /** 转一整圈所需的 tick 数, <=0 时按武器属性自动推导 */
    public float spinTime = -1f;

    /** 转速加速速率, 指数逼近: 数值越大加速越快, 约 1/spinUpSpeed tick 达到目标转速的 63% */
    public float spinUpSpeed = 0.15f;

    /** 转速减速速率, 指数逼近: 数值越大减速越快 */
    public float spinDownSpeed = 0.05f;

    /** 图层通道幅度, 表现纵深差 (最前/最后), 取负值可翻转前后 */
    public float depthFactor = 0.0005f;

    /** 颜色通道幅度, 0 时颜色恒为 color 与 colorTo 的中间值, 0.5 时在两者之间完整渐变, 取负值可翻转前后 */
    public float shadeAmount = 0.5f;

    /** 展开出的炮管数量上限, 超过时仅警告 */
    private static final int maxTubeCount = 32;

    /** 状态清理间隔 (tick) */
    private static final float cleanInterval = 300f;

    /** 状态过期时间 (tick), 超过此时长未更新的单位视为已消失 */
    private static final float staleTime = 60f;

    /** 已创建的部件实例(弱引用), 供 {@link #clearAll()} 在世界切换时遍历; 弱引用保证 mod 重载后旧实例仍可被回收 */
    private static final Seq<WeakReference<TubePart>> instances = new Seq<>();

    /** 每单位独立的旋转状态 */
    private static class SpinState{
        float phase = 0f;
        float spin = 0f;
        float lastTick = -1f;
        float lastSeen = 0f;
    }

    /** 以 Unit 为 key 的旋转状态 */
    private final ObjectMap<Unit, SpinState> states = new ObjectMap<>();

    /** 当前绘制单位对应的相位, 由 {@link #draw} 写入, 供相位源读取 */
    private float cachedPhase = 0f;

    /** 展开后的炮管数组, tubeCount<=0 时为 null */
    private @Nullable StatefulRegionPart[] tubes;

    /** 上次清理状态的时刻 */
    private float lastClean = 0f;

    public TubePart(String suffix){
        super(suffix);
        instances.add(new WeakReference<>(this));
    }

    public TubePart(){
        super();
        instances.add(new WeakReference<>(this));
    }

    /**
     * 清空所有部件实例的每单位状态, 并剔除已被回收的实例引用.<p>
     * 需要在入口类中注册到 {@code EventType.WorldLoadEvent} 与 {@code EventType.ResetEvent},
     * 使切换地图/返回菜单时立刻释放上一个世界的残留状态:
     * <pre>
     * Events.on(EventType.WorldLoadEvent.class, e -> TubePart.clearAll());
     * Events.on(EventType.ResetEvent.class, e -> TubePart.clearAll());
     * </pre>
     */
    public static void clearAll(){
        var iter = instances.iterator();
        while(iter.hasNext()){
            TubePart part = iter.next().get();
            if(part == null){
                //已回收的旧实例(例如 mod 重载后), 顺手剔除
                iter.remove();
            }else{
                part.states.clear();
            }
        }
    }

    @Override
    public void load(String name){
        super.load(name);

        if(tubeCount <= 0){
            tubes = null;
            return;
        }

        if(tubeCount > maxTubeCount){
            Log.warn("[TubePart] tubeCount @ exceeds @, drawing may be expensive.", tubeCount, maxTubeCount);
        }

        tubes = buildTubes();

        //炮管与 base 使用同一 name 加载, 直接共享区域引用, 避免重复查找图集
        for(var tube : tubes){
            tube.turretShading = turretShading;
            tube.regions = regions;
            tube.outlines = outlines;
            tube.heat = heat;
            tube.light = light;
        }
    }

    @Override
    public void updateState(Unit unit, boolean firing, float shotInterval, float delta){
        if(tubeCount <= 0 || unit == null || !unit.isAdded()) return;

        SpinState state = states.get(unit);
        if(state == null){
            state = new SpinState();
            states.put(unit, state);
        }

        //mirror 武器的两侧 mount 都会调用到本方法, 同一 tick 内只推进一次
        if(state.lastTick == Time.time) return;
        state.lastTick = Time.time;
        state.lastSeen = Time.time;

        //转速系数 0~1: 开火时按 spinUpSpeed 加速, 停火时按 spinDownSpeed 减速
        float target = firing ? 1f : 0f;
        state.spin = Mathf.lerpDelta(state.spin, target, target > state.spin ? spinUpSpeed : spinDownSpeed);

        float spin = Mathf.PI2 / totalSpinTime(shotInterval);
        state.phase = Mathf.mod(state.phase + spin * state.spin * delta, Mathf.PI2);

        if(Time.time - lastClean >= cleanInterval){
            lastClean = Time.time;
            var iter = states.iterator();
            while(iter.hasNext()){
                if(Time.time - iter.next().value.lastSeen > staleTime) iter.remove();
            }
        }
    }

    @Override
    public void draw(PartParams params){
        if(tubes == null){
            super.draw(params);
            return;
        }

        SpinState state = currentUnit == null ? null : states.get(currentUnit);
        cachedPhase = state == null ? 0f : state.phase;

        for(var tube : tubes){
            tube.draw(params);
        }
    }

    /**
     * 转一整圈所需的 tick 数.<p>
     * {@link #spinTime} > 0 时使用手动值, 否则按武器属性推导:
     * {@code tubeCount * shotInterval}, 即每发子弹前进一根炮管.
     *
     * @param shotInterval 该武器每发子弹之间的间隔 (tick)
     */
    public float totalSpinTime(float shotInterval){
        return spinTime > 0f ? spinTime : Math.max(1f, shotInterval * Math.max(1, tubeCount));
    }

    /** 按 tubeCount 展开炮管, 每根炮管的相位均分一圈 */
    private StatefulRegionPart[] buildTubes(){
        int count = Math.max(1, tubeCount);
        StatefulRegionPart[] out = new StatefulRegionPart[count];
        PartProgress phase = p -> cachedPhase;
        float step = Mathf.PI2 / count;

        for(int i = 0; i < count; i++){
            StatefulRegionPart tube = copy();
            PartProgress angle = phase.add(i * step);
            //竖直平面上的圆: 竖直分量表现为位移, 纵深分量表现为图层与明暗
            PartProgress vertical = MomijiPartProgress.sinOf(angle);
            PartProgress depth = MomijiPartProgress.cosOf(angle);

            //sin/cos 会产生负值, 必须关闭 clamp (RegionPart 默认开启), 否则只剩半个圆
            tube.clampProgress = false;
            //展开后每根炮管是独立个体, 不再左右镜像, 否则每根都会绘制两遍
            tube.mirror = false;
            //竖直分量: 与中间位置的偏移量
            tube.progress = vertical;
            //纵深分量: 转到最前的炮管画在最上层, 转到最后的被压在下面
            tube.layerProgress = depth.mul(depthFactor);
            //纵深分量: 最前最亮, 最后最暗
            tube.colorProgress = depth.mul(-shadeAmount).add(0.5f);
            out[i] = tube;
        }
        return out;
    }
}
