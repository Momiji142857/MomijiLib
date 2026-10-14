package momijiLib.type.weapons;

import arc.util.Time;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Unit;
import mindustry.type.Weapon;
import momijiLib.entities.part.MomijiPartProgress;
import momijiLib.entities.part.StatefulRegionPart;

/**
 * 在每 tick 的 update 阶段推进 {@link StatefulRegionPart} 的每单位状态, 并在 draw 前将当前单位传入部件.<p>
 * 状态推进放在 update 而非 draw, 因此不受帧率、相机剔除与 mirror 双份绘制的影响;
 * 部件所需的武器级参数(开火状态、每发间隔)也在这里统一计算后下发.<p>
 * 绘制阶段仅注入 {@link StatefulRegionPart#currentUnit}, 并在结束时清除, 异常时同样保证清除.
 *
 * @author Momiji142857 (with DeepSeek)
 * @see StatefulRegionPart
 * @see MomijiPartProgress
 * @since 2026-05-08
 */
public class StatefulWeapon extends Weapon{

    public StatefulWeapon(){
        super();
    }

    public StatefulWeapon(String name){
        super(name);
    }

    @Override
    public void update(Unit unit, WeaponMount mount){
        super.update(unit, mount);

        //每发子弹之间的平均间隔: 一轮射击打出 shoot.shots 发, 一轮耗时 reload.
        //shoot.shotDelay 会让轮内更密、轮间更疏, 这里取平均值以保证转速平稳.
        float shotInterval = reload / Math.max(1, shoot.shots);

        for(var part : parts){
            if(part instanceof StatefulRegionPart stateful){
                //开火判据用 mount.shoot, 与武器预热速度无关;
                //蓄力(首发延迟)期间即使松开扳机, 已排队的子弹仍会射出, 因此把 charging 一并算作开火
                stateful.updateState(unit, mount.shoot || mount.charging, shotInterval, Time.delta);
            }
        }
    }

    @Override
    public void draw(Unit unit, WeaponMount mount){
        //传入当前单位, 供 StatefulRegionPart 读取自身状态
        StatefulRegionPart.currentUnit = unit;
        try{
            super.draw(unit, mount);
        }finally{
            StatefulRegionPart.currentUnit = null;
        }
    }
}
