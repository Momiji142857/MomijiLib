package momijiLib.type.world.blocks.storage;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.WorldLoadBeginEvent;
import mindustry.game.EventType.WorldLoadEndEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Teamc;
import mindustry.graphics.Pal;
import mindustry.io.SaveFileReader;
import mindustry.io.SaveVersion;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.blocks.storage.StorageBlock;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import mindustry.world.modules.ItemModule;
import mindustry.world.modules.LiquidModule;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static mindustry.Vars.content;

/**
 * 全图同队同种建筑共享同一份物品与液体库存的存储方块.<p>
 * 库存: 每种建筑、每个队伍各一个池, 池的容量由 JSON 的 {@link #itemCapacity}/{@link #liquidCapacity} 决定,
 * 建筑数量只增加接入点, 不增加容量; 因此拆掉所有建筑后池内物品仍然保留 (末影箱语义).<br>
 * 物品计数口径沿用原版 {@link #separateItemCapacity}: true = 每种物品各自上限, false = 所有物品总数上限.<br>
 * 速率: 每栋建筑有独立的输入/输出预算, 按每秒速率补充; 输入在 {@code acceptItem/acceptLiquid} 处拦截,
 * 输出在 {@code removeStack}/{@code itemTaken} 与主动 dump 时扣除.<br>
 * 输出: 默认主动向相邻建筑推送, 也可被装卸器/管道按同一输出预算抽取; 可通过
 * {@link #outputItems}/{@link #outputLiquids} 关闭主动推送.<p>
 * 存档: 每个建筑仍会写一份共享模块作为兜底; 另外注册自定义存档块
 * {@code momijilib-shared-storage}, 保证拆光建筑后池子仍能保存.<p>
 * 本类不依赖 momijiLib 的其他类, 可直接搬入主目录.
 *
 * @author Momiji142857
 * @see StorageBlock
 * @see mindustry.world.blocks.storage.CoreBlock
 * @since 2026-11-17
 */
public class SharedStorage extends StorageBlock{
    /** 每栋建筑每秒最多接受的物品数; 0 表示禁止输入, 负数表示不限. */
    public float itemInputRate = 10f;
    /** 每栋建筑每秒最多输出的物品数; 0 表示禁止输出, 负数表示不限. */
    public float itemOutputRate = 10f;
    /** 每栋建筑每秒最多接受的液体量; 0 表示禁止输入, 负数表示不限. */
    public float liquidInputRate = 60f;
    /** 每栋建筑每秒最多输出的液体量; 0 表示禁止输出, 负数表示不限. */
    public float liquidOutputRate = 60f;
    /** 是否主动向相邻建筑推送物品. */
    public boolean outputItems = true;
    /** 是否主动向相邻建筑推送液体. */
    public boolean outputLiquids = true;
    /** 是否接受任意液体; false 时只在当前液体匹配或罐子几乎为空时接受, 与路由器一致. */
    public boolean acceptAnyLiquid = false;

    /** 每个 (队伍, 建筑名) 一个池; 键见 {@link Network#makeKey(int, String)}. */
    static final ObjectMap<String, Network> networks = new ObjectMap<>();
    /** 自定义存档块只注册一次. */
    private static boolean saveRegistered;

    static{
        registerSaveChunk();
    }

    /**
     * 注册全局池的存档块与清空事件. 类加载时自动调用; 也可以在 LibLoad.init() 里手动调用,
     * 重复调用无副作用.
     */
    public static void registerSaveChunk(){
        if(saveRegistered) return;
        saveRegistered = true;

        SaveVersion.addCustomChunk("momijilib-shared-storage", new SharedStorageChunk());
        //读档/进服时 beginMapLoad 早于建筑重建; 新地图也从这里开始
        Events.on(WorldLoadBeginEvent.class, e -> networks.clear());
        //endMapLoad 在重建 proximity 之前触发, 覆盖 begin 未触发的进服路径
        Events.on(WorldLoadEndEvent.class, e -> networks.clear());
        //返回菜单/新游戏时兜底清空
        Events.on(ResetEvent.class, e -> networks.clear());
    }

    public SharedStorage(String name){
        super(name);

        hasItems = true;
        hasLiquids = true;
        update = true;
        sync = true;
        solid = true;

        //紧挨原版核心时不要被 CoreBuild.onProximityUpdate() 收编, 否则池子会被核心劫持
        coreMerge = false;
        //不允许被其他方块直接覆盖: 否则新方块 overwrote() 会把共享池复制一份
        replaceable = false;

        //原版字段: true = 每种物品各自上限 (容器/核心语义), false = 所有物品总数上限
        separateItemCapacity = true;
        //JSON 应显式覆盖这两个值; 这里给一个安全默认, 避免 liquidCapacity 为 -1
        itemCapacity = 1000;
        liquidCapacity = 1000f;
    }

    @Override
    public void setStats(){
        super.setStats();

        if(itemOutputRate >= 0f){
            stats.add(Stat.itemsMoved, itemOutputRate, StatUnit.itemsSecond);
        }
    }

    @Override
    public void setBars(){
        super.setBars();

        if(hasItems && itemCapacity > 0){
            addBar("items", (SharedStorageBuild entity) -> new Bar(
                () -> Core.bundle.format("bar.items", entity.items.total()),
                () -> Pal.items,
                () -> separateItemCapacity
                    ? Mathf.clamp(entity.items.total() / (float)Math.max(1, itemCapacity * content.items().size))
                    : Mathf.clamp(entity.items.total() / (float)itemCapacity)
            ));
        }

        if(hasLiquids && liquidCapacity > 0f){
            addLiquidBar((SharedStorageBuild entity) -> entity.liquids == null ? null : entity.liquids.current());
        }
    }

    /** 一个 (队伍, 建筑) 的共享池. */
    static class Network{
        final int teamId;
        final String blockName;
        final String key;
        ItemModule items;
        LiquidModule liquids;

        Network(int teamId, String blockName, ItemModule items, LiquidModule liquids){
            this.teamId = teamId;
            this.blockName = blockName;
            this.key = makeKey(teamId, blockName);
            this.items = items;
            this.liquids = liquids;
        }

        static String makeKey(int teamId, String blockName){
            return teamId + "|" + blockName;
        }
    }

    public class SharedStorageBuild extends Building{
        /** 当前所属的共享池, 为 null 表示还没加入. */
        protected Network network;

        /** 本建筑独立的输入/输出预算, 单位分别为物品个、液体单位. */
        protected float itemInBudget, itemOutBudget, liquidInBudget, liquidOutBudget;

        @Override
        public void onProximityUpdate(){
            ensureNetwork();
            super.onProximityUpdate();
        }

        @Override
        public void updateTile(){
            ensureNetwork();

            float dt = delta() / 60f;
            itemInBudget = refill(itemInBudget, itemInputRate, dt);
            itemOutBudget = refill(itemOutBudget, itemOutputRate, dt);
            liquidInBudget = refill(liquidInBudget, liquidInputRate, dt);
            liquidOutBudget = refill(liquidOutBudget, liquidOutputRate, dt);

            if(outputItems) dumpItems();
            if(outputLiquids) dumpLiquids();
        }

        @Override
        public void changeTeam(Team team){
            super.changeTeam(team);
            //不能把旧队伍的池子交给新队伍: ensureNetwork() 检测到 key 变化时会用空模块另起
            ensureNetwork();
        }

        /** 取得/建立本建筑所属的共享池, 并把 items/liquids 指向池模块. */
        protected void ensureNetwork(){
            if(team == null || block == null) return;

            String key = Network.makeKey(team.id, block.name);
            //networks 可能被 WorldLoadBegin/End 事件清空, 必须确认表里仍然是同一个对象
            if(network != null && network.key.equals(key) && networks.get(key) == network){
                if(items != network.items) items = network.items;
                if(liquids != network.liquids) liquids = network.liquids;
                return;
            }

            Network found = networks.get(key);
            if(found == null){
                //首次加入/网络被清空后重建时, 把自己的模块交给网络 (读档时里面已有存档数据);
                //换队导致的换池则不能继承旧池, 用空模块另起
                boolean adopt = network == null || network.key.equals(key);
                found = new Network(team.id, block.name,
                    adopt ? items : new ItemModule(),
                    adopt ? liquids : new LiquidModule());
                networks.put(key, found);
            }

            network = found;
            items = found.items;
            liquids = found.liquids;
        }

        /** 按每秒速率补充预算; 负数表示不限, 0 表示禁用. */
        float refill(float budget, float rate, float dt){
            if(rate < 0f) return Float.MAX_VALUE;
            if(rate <= 0f) return 0f;
            return Math.min(Math.max(rate, 1f), budget + rate * dt);
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            if(items == null || !block.hasItems || (source != null && source.team() != team)) return false;
            //不接收同网络的物品, 否则装卸器会在同一个池子里空转
            if(source instanceof SharedStorageBuild b && b.sameNetwork(this)) return false;

            int room = separateItemCapacity ? block.itemCapacity - items.get(item) : block.itemCapacity - items.total();
            return room > 0 && itemInBudget >= 1f;
        }

        @Override
        public void handleItem(Building source, Item item){
            if(items == null) return;

            int room = separateItemCapacity ? block.itemCapacity - items.get(item) : block.itemCapacity - items.total();
            if(room <= 0) return;

            super.handleItem(source, item);
            itemInBudget = Math.max(0f, itemInBudget - 1f);
        }

        @Override
        public int acceptStack(Item item, int amount, Teamc source){
            if(items == null || !block.hasItems || (source != null && source.team() != team)) return 0;
            if(source instanceof SharedStorageBuild b && b.sameNetwork(this)) return 0;

            int room = separateItemCapacity ? block.itemCapacity - items.get(item) : block.itemCapacity - items.total();
            int budget = (int)Math.min(itemInBudget, Integer.MAX_VALUE);
            return Math.max(0, Math.min(Math.min(room, amount), budget));
        }

        @Override
        public void handleStack(Item item, int amount, Teamc source){
            if(items == null) return;

            int room = separateItemCapacity ? block.itemCapacity - items.get(item) : block.itemCapacity - items.total();
            int add = Math.max(0, Math.min(Math.min(amount, room), (int)Math.min(itemInBudget, Integer.MAX_VALUE)));
            if(add <= 0) return;

            super.handleStack(item, add, source);
            itemInBudget = Math.max(0f, itemInBudget - add);
        }

        /**
         * 被动输出预算门控.<p>
         * 普通 Unloader 和 DirectionalUnloader 都会先调用 canUnload(), 所以这里用预算决定
         * 当前是否允许被抽取; 预算不足时它们不会选中本建筑, 等预算补回来后会重新可选
         * (因为本建筑不是 StorageBuild, Unloader 会把它当作可装载/可卸载对象保留在候选表里).
         */
        @Override
        public boolean canUnload(){
            if(itemOutputRate == 0f) return false;
            if(itemOutputRate < 0f) return super.canUnload();
            return super.canUnload() && itemOutBudget >= 1f;
        }

        /**
         * 不能在这里因为预算不足返回 0: 原版 Unloader 的顺序是
         * {@code to.handleItem(...); from.removeStack(item, 1);} 且不检查返回值,
         * 返回 0 会让目标凭空多一个物品. 因此照常移除, 用预算透支来记账,
         * 再由 canUnload() 在后续 tick 阻止继续抽取.
         */
        @Override
        public int removeStack(Item item, int amount){
            if(items == null || itemOutputRate == 0f) return 0;

            int result = super.removeStack(item, amount);
            if(result > 0 && itemOutputRate > 0f){
                itemOutBudget -= result;
            }
            return result;
        }

        /**
         * DirectionalUnloader 直接调用 {@code back.items.remove(...)}, 不经过 removeStack,
         * 但会在移除后调用 itemTaken(); 在这里补记预算.
         */
        @Override
        public void itemTaken(Item item){
            super.itemTaken(item);
            if(itemOutputRate > 0f){
                itemOutBudget -= 1f;
            }
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            if(liquids == null || !block.hasLiquids || (source != null && source.team() != team)) return false;
            if(source instanceof SharedStorageBuild b && b.sameNetwork(this)) return false;
            if(!(acceptAnyLiquid || liquids.current() == liquid || liquids.currentAmount() < 0.2f)) return false;

            return liquids.get(liquid) < block.liquidCapacity && liquidInBudget > 0.001f;
        }

        @Override
        public void handleLiquid(Building source, Liquid liquid, float amount){
            if(liquids == null) return;

            float add = Math.min(amount, block.liquidCapacity - liquids.get(liquid));
            if(add <= 0f) return;

            liquids.add(liquid, add);
            liquidInBudget = Math.max(0f, liquidInBudget - add);
        }

        @Override
        public boolean canDump(Building to, Item item){
            //不向同网络的建筑倒物品, 那只是把物品从共享池搬回共享池
            return to != null && to.team == team && !sameNetwork(to);
        }

        @Override
        public boolean canDumpLiquid(Building to, Liquid liquid){
            return to != null && to.team == team && !sameNetwork(to);
        }

        /** 是否与本建筑属于同一个共享池. 同队同种建筑一定同池. */
        public boolean sameNetwork(Building other){
            return other instanceof SharedStorageBuild b && b.block == block && b.team == team;
        }

        /**
         * 外部（模组/逻辑/其他方块）也可能直接调用 dump(); 原版 dump() 内部是
         * {@code other.handleItem(...); items.remove(...)}, 不经过 removeStack.
         * 这里统一按输出预算门控; 预算不足直接返回 false, 不移动物品.
         * dump() 和 dumpAccumulate() 最终都会虚调用到这里.
         */
        @Override
        public boolean dump(Item todump){
            if(itemOutputRate == 0f) return false;
            if(itemOutputRate > 0f && itemOutBudget < 1f) return false;

            if(!super.dump(todump)) return false;

            if(itemOutputRate > 0f){
                itemOutBudget -= 1f;
            }
            return true;
        }

        /** 主动输出物品: 每个预算单位移动一个物品, 单 tick 最多 64 个防止负数速率时卡顿. */
        protected void dumpItems(){
            if(items == null || items.total() == 0) return;

            int guard = 0;
            //dump() 内部已经扣预算, 这里不能再扣一次
            while(guard++ < 64 && dump()){}
        }

        /**
         * 液体侧同名旁路: 原版 dumpLiquid() 通过 transferLiquid() 直接 liquids.remove().
         * 外部显式调用我们的建筑时也按液体输出预算门控.
         */
        @Override
        public void dumpLiquid(Liquid liquid, float scaling, int outputDir){
            if(liquids == null || liquidOutputRate == 0f || liquidOutBudget <= 0f) return;

            float before = liquids.get(liquid);
            super.dumpLiquid(liquid, scaling, outputDir);
            float moved = before - liquids.get(liquid);

            if(moved > 0f && liquidOutputRate > 0f){
                liquidOutBudget -= moved;
            }
        }

        /** 主动输出液体: 遍历所有有库存的液体与相邻建筑, 按邻居余量与本建筑预算推进. */
        protected void dumpLiquids(){
            if(liquids == null || liquidOutBudget <= 0f) return;

            for(Liquid liquid : content.liquids()){
                if(liquids.get(liquid) <= 0.0001f) continue;

                for(int i = 0; i < proximity.size && liquidOutBudget > 0f; i++){
                    Building other = proximity.get(i);
                    if(other == null || other == this) continue;

                    other = other.getLiquidDestination(this, liquid);
                    if(other == null || other == this || other.team != team || other.liquids == null || !other.block.hasLiquids) continue;
                    if(!canDumpLiquid(other, liquid) || !other.acceptLiquid(this, liquid)) continue;

                    float room = other.block.liquidCapacity - other.liquids.get(liquid);
                    if(room <= 0.0001f) continue;

                    float amount = Math.min(Math.min(liquidOutBudget, liquids.get(liquid)), room);
                    if(amount <= 0.0001f) continue;

                    other.handleLiquid(this, liquid, amount);
                    liquids.remove(liquid, amount);
                    liquidOutBudget -= amount;
                }
            }

            if(liquidOutBudget < 0f) liquidOutBudget = 0f;
        }

        /**
         * 被其他方块替换时, 只吸收普通容器/工厂残留的物品与液体.<p>
         * 共享池的方块不搬运 (它的池子还在), 核心及其附属仓库也不搬运 (否则会把核心库存复制一份).
         */
        @Override
        public void overwrote(Seq<Building> previous){
            ensureNetwork();

            for(Building other : previous){
                if(other == null || other == this || other.items == null || other.items == items) continue;
                if(other instanceof SharedStorageBuild || other instanceof CoreBuild) continue;
                if(other instanceof StorageBuild b && b.linkedCore != null) continue;

                if(items != null){
                    other.items.each((item, amount) -> {
                        int room = separateItemCapacity ? block.itemCapacity - items.get(item) : block.itemCapacity - items.total();
                        if(room > 0){
                            items.add(item, Math.min(amount, room));
                        }
                    });
                }

                if(liquids != null && other.liquids != null){
                    for(Liquid liquid : content.liquids()){
                        float amount = other.liquids.get(liquid);
                        if(amount > 0f){
                            float room = block.liquidCapacity - liquids.get(liquid);
                            if(room > 0f) liquids.add(liquid, Math.min(amount, room));
                        }
                    }
                }
            }
        }

        @Override
        public boolean canBeReplaced(Block other){
            //防止别的方块直接覆盖本建筑时把共享池模块复刻到新方块里
            return false;
        }

        @Override
        public boolean canPickup(){
            //被 payload 拾取会把共享池写成 payload 的物品数据, 造成复制
            return false;
        }
    }

    /** 把全局池写进存档的 custom 区域, 并在读档/进服时回填到同名网络. */
    static class SharedStorageChunk implements SaveFileReader.CustomChunk{
        @Override
        public void write(DataOutput stream) throws IOException{
            stream.writeInt(networks.size);

            for(var entry : networks){
                Network net = entry.value;
                stream.writeInt(net.teamId);
                stream.writeUTF(net.blockName);

                if(net.items != null){
                    net.items.write(new Writes(stream));
                }else{
                    stream.writeShort(0);
                }

                if(net.liquids != null){
                    net.liquids.write(new Writes(stream));
                }else{
                    stream.writeShort(0);
                }
            }
        }

        @Override
        public void read(DataInput stream) throws IOException{
            int count = stream.readInt();
            for(int i = 0; i < count; i++){
                int teamId = stream.readInt();
                String blockName = stream.readUTF();
                String key = Network.makeKey(teamId, blockName);

                Network net = networks.get(key);
                if(net == null){
                    net = new Network(teamId, blockName, new ItemModule(), new LiquidModule());
                    networks.put(key, net);
                }

                //模块缺失时补一个空模块再读; 写存档时缺失会写成 count=0, 读进来同样是空
                if(net.items == null) net.items = new ItemModule();
                if(net.liquids == null) net.liquids = new LiquidModule();

                net.items.read(new Reads(stream), false);
                net.liquids.read(new Reads(stream), false);
            }
        }

        @Override
        public boolean shouldWrite(){
            return true;
        }

        @Override
        public boolean writeNet(){
            return true;
        }
    }
}
