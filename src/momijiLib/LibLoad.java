package momijiLib;

import arc.util.Log;
import mindustry.mod.ClassMap;
import mindustry.mod.Mod;
import momijiLib.entities.part.StatefulRegionPart;
import momijiLib.entities.part.TubePart;
import momijiLib.type.weapons.StatefulWeapon;
import momijiLib.type.world.blocks.distribution.BatchDumpBridge;
import momijiLib.type.world.blocks.distribution.ItemLiquidJunction;
import momijiLib.type.world.blocks.production.LinkedDrill;
import momijiLib.type.world.blocks.production.MultiCrafter;
import momijiLib.type.world.blocks.production.OmniCrafter;
import momijiLib.type.world.blocks.storage.LinkedBlock;

public class LibLoad extends Mod{

    //是否启用测试内容.
    public boolean test = false;

    public static String addModName(String add){
        return "momiji-lib" + "-" + add;
    }

    public LibLoad(){
        Log.info("Loaded MomijiLib constructor.");
    }

    @Override
    public void loadContent(){
        //使用时把下面这几行加到自己模组的 loadContent() 函数里就好.
        ClassMap.classes.put("StatefulRegionPart", StatefulRegionPart.class);
        ClassMap.classes.put("TubePart", TubePart.class);
        ClassMap.classes.put("StatefulWeapon", StatefulWeapon.class);
        ClassMap.classes.put("BatchDumpBridge", BatchDumpBridge.class);
        ClassMap.classes.put("ItemLiquidJunction", ItemLiquidJunction.class);
        ClassMap.classes.put("LinkedDrill", LinkedDrill.class);
        ClassMap.classes.put("MultiCrafter", MultiCrafter.class);
        ClassMap.classes.put("OmniCrafter", OmniCrafter.class);
        ClassMap.classes.put("LinkedBlock", LinkedBlock.class);

        //启用测试内容.
        if(test){
            // TestBlocks.load();
            // TestUnitType.load();
        }
    }
}
