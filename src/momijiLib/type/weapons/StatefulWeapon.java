package momijiLib.type.weapons;

import mindustry.entities.units.WeaponMount;
import mindustry.gen.Unit;
import mindustry.type.Weapon;
import momijiLib.entities.part.MomijiPartProgress;
import momijiLib.entities.part.StatefulRegionPart;
import momijiLib.entities.part.TubePart;

/**
 * 在 draw 前将当前单位传入 {@link StatefulRegionPart}, 使其累加器能以 Unit 为 key 区分不同单位.<p>
 * 提供 {@link #createTubes} 快捷生成旋转炮管组, 旋转速度与武器射击间隔自动同步.<p>
 * 每 {@value #CLEAN_INTERVAL} ticks 清理一次已死亡单位的残留状态.
 *
 * @author Momiji142857 (with DeepSeek)
 * @see StatefulRegionPart
 * @see MomijiPartProgress
 * @since 2026-05-08
 */
public class StatefulWeapon extends Weapon{

    /** 清理计时器, 每帧 +1 */
    private int cleanTimer = 0;

    /** 清理间隔 (ticks) */
    private static final int CLEAN_INTERVAL = 900;

    public StatefulWeapon(){
        super();
    }

    public StatefulWeapon(String name){
        super(name);
    }

    @Override
    public void draw(Unit unit, WeaponMount mount){
        //传入当前单位, 供 StatefulRegionPart 使用
        StatefulRegionPart.currentUnit = unit;
        super.draw(unit, mount);
        StatefulRegionPart.currentUnit = null;

        //定期清理已死亡单位的累加状态
        if(++cleanTimer >= CLEAN_INTERVAL){
            cleanTimer = 0;
            for(var part : parts){
                if(part instanceof StatefulRegionPart sp) sp.cleanDeadUnits();
            }
        }
    }

    /**
     * 创建一组旋转炮管, 旋转速度与武器射击间隔同步.<p>
     * 详见详见 {@link TubePart#buildTubes(int, StatefulRegionPart, float)}.
     */
    public StatefulRegionPart[] createTubes(int count, StatefulRegionPart base){
        return TubePart.buildTubes(count, base, reload);
    }
}
