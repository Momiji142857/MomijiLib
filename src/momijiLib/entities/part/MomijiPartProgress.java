package momijiLib.entities.part;

import arc.math.Mathf;
import arc.util.Time;
import mindustry.entities.part.DrawPart;

/**
 * 扩展的 PartProgress 工具类, 提供自定义进度函数。<p>
 * <ul>
 *     <li>{@link #sinOf(DrawPart.PartProgress, float, float)} - 以任意 Progress 为相位源的正弦波</li>
 *     <li>{@link #cosOf(DrawPart.PartProgress, float, float)} - 以任意 Progress 为相位源的余弦波</li>
 *     <li>{@link #sawtooth(DrawPart.PartProgress, float)} - 以任意 Progress 为相位源的锯齿波</li>
 *     <li>{@link #sawtooth(float)} - 以 {@link Time#time} 为相位源的锯齿波</li>
 *     <li>{@link #sawtooth()} - 以 {@link Time#time} 为相位源, 以 60f 为周期的锯齿波</li>
 * </ul>
 *
 * @author Momiji142857 (with DeepSeek)
 * @see DrawPart.PartProgress
 * @since 2026-04-26
 */
public class MomijiPartProgress{

    private MomijiPartProgress(){}

    /**
     * 与内置的 {@code PartProgress.sin()} 不同, 允许使用任意 PartProgress 作为相位源.<p>
     * 数学形式: {@code output = sin(source / scl) * mag}
     *
     * @param source 相位源
     * @param scl    周期
     * @param mag    振幅
     * @return 正弦波 Progress, 输出范围 [-mag, mag]
     */
    public static DrawPart.PartProgress sinOf(DrawPart.PartProgress source, float scl, float mag){
        return p -> Mathf.sin(source.get(p), scl, mag);
    }

    /**
     * 默认周期 1f, 振幅 1f 的 {@link #sinOf(DrawPart.PartProgress, float, float)}.<p>
     * 等价于 {@code sinOf(source, 1f, 1f)}, 输出范围 [-1, 1].
     */
    public static DrawPart.PartProgress sinOf(DrawPart.PartProgress source){
        return sinOf(source, 1f, 1f);
    }

    /**
     * 以任意 Progress 为相位源构造余弦波.<p>
     * 数学形式: {@code output = cos(source / scl) * mag}
     *
     * @param source 相位源
     * @param scl    周期
     * @param mag    振幅
     * @return 余弦波 Progress, 输出范围 [-mag, mag]
     */
    public static DrawPart.PartProgress cosOf(DrawPart.PartProgress source, float scl, float mag){
        return p -> Mathf.cos(source.get(p), scl, mag);
    }

    /**
     * 默认周期 1f, 振幅 1f 的 {@link #cosOf(DrawPart.PartProgress, float, float)}.<p>
     * 等价于 {@code cosOf(source, 1f, 1f)}, 输出范围 [-1, 1].
     */
    public static DrawPart.PartProgress cosOf(DrawPart.PartProgress source){
        return cosOf(source, 1f, 1f);
    }

    /**
     * 返回一个在 [0, 1) 之间循环的锯齿波 Progress, 以任意 Progress 为相位源, 周期为 period.<p>
     * period <= 0 时默认周期 60 ticks.<p>
     * 数学形式: {@code output = mod(source, period) / period}
     *
     * @param source 相位源
     * @param period 周期
     * @return 锯齿波 Progress, 输出范围 [0, 1)
     */
    public static DrawPart.PartProgress sawtooth(DrawPart.PartProgress source, float period){
        float t = period <= 0f ? 60f : period;
        return p -> Mathf.mod(source.get(p), t) / t;
    }

    /**
     * 默认相位源为 Time.time 的 {@link #sawtooth(DrawPart.PartProgress, float)}.<p>
     * 等价于 {@code sawtooth(p -> Time.time, period)}.
     *
     * @param period 周期
     * @return 锯齿波 Progress, 输出范围 [0, 1)
     */
    public static DrawPart.PartProgress sawtooth(float period){
        return sawtooth(p -> Time.time, period);
    }

    /**
     * 默认周期 60f 的 {@link #sawtooth(float)}.<p>
     * 等价于 {@code sawtooth(60f)}.
     */
    public static DrawPart.PartProgress sawtooth(){
        return sawtooth(60f);
    }
}
