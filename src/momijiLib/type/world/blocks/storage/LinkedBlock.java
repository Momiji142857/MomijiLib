package momijiLib.type.world.blocks.storage;

import arc.struct.EnumSet;
import arc.struct.IntSet;
import arc.struct.Queue;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.world.Block;
import mindustry.world.blocks.liquid.LiquidRouter;
import mindustry.world.blocks.storage.StorageBlock;
import mindustry.world.meta.BlockFlag;
import mindustry.world.meta.BlockGroup;
import mindustry.world.meta.Env;
import mindustry.world.modules.ItemModule;

import static mindustry.Vars.content;
import static mindustry.Vars.world;

/**
 * 相邻同类建筑之间共享物品库存的基类, 可附加路由器式液体输出.<p>
 * 物品: 相邻同类建筑自动连接成共享组, 容量叠加, 拆除时按比例分配.<br>
 * 液体: 独立于组, 每个建筑单独存储, 由 {@link #outputLiquids} 控制输出行为.<p>
 * 每组最多 {@link #maxGroupSize} 个建筑: 组满后相邻的其他建筑不会并入该组,
 * 而是另起新组; 对这些建筑而言, 满组建筑与普通建筑无异.
 *
 * @author Momiji142857
 * @see StorageBlock
 * @see LiquidRouter
 * @since 2026-07-14
 */
public class LinkedBlock extends Block{
    /** 启用液体输出, 需同时设置 {@link #outputsLiquid} 为 true */
    public boolean outputLiquids = true;
    /** 仅向同类建筑输出液体 (outputLiquids 为 true 时生效) */
    public boolean linkLiquids = true;
    /** 单个共享组的最大成员数, 按建筑个数计 (多格建筑算 1 个); 达到上限后其他建筑另起新组 */
    public int maxGroupSize = 64;

    public LinkedBlock(String name){
        super(name);
        hasItems = true;
        hasLiquids = true;
        solid = true;
        update = true;
        sync = true;
        destructible = true;
        separateItemCapacity = true;
        group = BlockGroup.transportation;
        flags = EnumSet.of(BlockFlag.storage);
        allowResupply = true;
        envEnabled = Env.any;
    }

    @Override
    public boolean outputsItems(){
        return false;
    }

    public class LinkedBuild extends Building{
        /** 共享组组长, 所有组员的 items 都指向组长的模块, 为null时自身为组长 */
        public @Nullable LinkedBuild groupLeader;
        /** 组成员总数, 仅组长的此字段有效 */
        public int groupSize = 1;
        /** 是否正在被移除, 拆除过程中不能被重新并入任何组 */
        protected boolean removed;
        /** 组员写存档时用的空物品池: 共享池只由组长写, 否则读档后每个组员各存一份, 重建 proximity 时会重复求和 */
        private final ItemModule emptyPool = new ItemModule();

        /** 生效的组成员上限, 内容定义误写为 0 或负数时兜底为 1 */
        int groupLimit(){
            return Math.max(1, maxGroupSize);
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return items.get(item) < getMaximumAccepted(item);
        }

        @Override
        public int getMaximumAccepted(Item item){
            return (groupLeader == null ? groupSize : groupLeader.groupSize) * itemCapacity;
        }

        @Override
        public boolean canDump(Building to, Item item){
            //不向同组成员输出物品, 共享同一个物品池, 输出无效
            return to.block != block || !(to instanceof LinkedBuild lb) || lb.team != team || lb.leader() != leader();
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            return outputLiquids && (liquids.current() == liquid || liquids.currentAmount() < 0.2f);
        }

        @Override
        public boolean canDumpLiquid(Building to, Liquid liquid){
            return outputLiquids && (!linkLiquids || to.block == block);
        }

        @Override
        public void onProximityUpdate(){
            super.onProximityUpdate();

            LinkedBuild selfLeader = leader();
            IntSet groupSet = new IntSet();
            Seq<LinkedBuild> groups = new Seq<>();

            for(int i = 0; i < proximity.size; i++){
                Building other = proximity.get(i);
                if(!(other.block == block && other instanceof LinkedBuild lb && lb.team == team)) continue;
                //正在拆除的建筑不参与合并
                if(lb.removed) continue;

                LinkedBuild otherLeader = lb.leader();
                if(otherLeader != selfLeader && groupSet.add(otherLeader.tile.pos())){
                    groups.add(otherLeader);
                }
            }

            if(groups.size > 0){
                mergeGroups(selfLeader, groups);
            }
        }

        /**
         * 合并多个组, 选 tile.pos() 最小的组长作为新组长.<p>
         * 组内成员数受 {@link #maxGroupSize} 限制: 自身组满后不再吸收任何邻组;
         * 自身组未满时, 邻组按 tile.pos() 升序依次纳入, 装不下的组保持独立.
         * 自身组必须参与合并, 否则仅通过自身相连的两个邻组会被并成不连通的一组,
         * 导致 {@link #collectGroup} 无法完整收集该组.
         */
        void mergeGroups(LinkedBuild selfLeader, Seq<LinkedBuild> others){
            int limit = groupLimit();

            //满组不再吸收新成员
            if(selfLeader.groupSize >= limit) return;

            //按 tile.pos() 升序: 与组长选取规则一致, 且结果与 onProximityUpdate 的调用顺序无关
            others.sort(l -> l.tile.pos());

            Seq<LinkedBuild> merged = new Seq<>();
            int totalSize = selfLeader.groupSize;

            merged.add(selfLeader);

            for(int i = 0; i < others.size; i++){
                LinkedBuild leader = others.get(i);
                //装不下的组保持独立, 等之后放置新建筑时再尝试
                if(totalSize + leader.groupSize > limit) continue;

                merged.add(leader);
                totalSize += leader.groupSize;
            }

            if(merged.size <= 1) return;

            LinkedBuild newLeader = merged.get(0);
            for(int i = 1; i < merged.size; i++){
                if(merged.get(i).tile.pos() < newLeader.tile.pos()){
                    newLeader = merged.get(i);
                }
            }

            ItemModule mergedItems = new ItemModule();

            //只累加被选中组的物品池, 未合并的组保持原样
            for(int i = 0; i < merged.size; i++){
                mergedItems.add(merged.get(i).items);
            }

            newLeader.items = mergedItems;
            newLeader.groupLeader = null;
            newLeader.groupSize = totalSize;

            for(int i = 0; i < merged.size; i++){
                LinkedBuild leader = merged.get(i);
                if(leader == newLeader) continue;

                Seq<LinkedBuild> members = collectGroup(leader, leader, new IntSet());
                for(int j = 0; j < members.size; j++){
                    LinkedBuild m = members.get(j);
                    m.items = mergedItems;
                    m.groupLeader = newLeader;
                    m.groupSize = 1;
                }
            }

            newLeader.clampResources();
        }

        /** 将一个建筑加入本组, 达到成员上限时不加入, 由调用方另开新组 */
        public void addToGroup(LinkedBuild other){
            LinkedBuild actualLeader = leader();
            if(other.leader() == actualLeader) return;

            //只并入 other 这一个建筑, 超过上限时整体拒绝
            if(actualLeader.groupSize + 1 > groupLimit()) return;

            if(other.groupLeader != null){
                other.removeFromGroup();
            }

            if(actualLeader.items != other.items){
                actualLeader.items.add(other.items);
            }

            other.items = actualLeader.items;
            other.groupLeader = actualLeader;

            actualLeader.groupSize++;
            other.groupSize = 1;
        }

        /** 从组中移除, 处理组分裂 */
        public void removeFromGroup(){
            boolean isLeader = groupLeader == null;
            LinkedBuild oldLeader = isLeader ? this : groupLeader;
            ItemModule oldItems = items;

            Seq<LinkedBuild> neighbors = new Seq<>();
            for(int i = 0; i < proximity.size; i++){
                Building other = proximity.get(i);
                if(other.block == block && other instanceof LinkedBuild lb && lb.team == team && !lb.removed && lb.leader() == oldLeader){
                    neighbors.add(lb);
                }
            }

            if(!isLeader){
                groupLeader = null;
                items = new ItemModule();
                groupSize = 1;
                oldLeader.groupSize--;
            }else{
                items = new ItemModule();
                groupLeader = null;
                groupSize = 1;
            }

            splitCheck(oldLeader, oldItems, neighbors);
        }

        /** 检查组分裂, 按各子组容量比例分配资源 */
        void splitCheck(LinkedBuild oldLeader, ItemModule oldItems, Seq<LinkedBuild> neighbors){
            if(neighbors.size == 0) return;

            Seq<Seq<LinkedBuild>> subGroups = new Seq<>();
            IntSet visited = new IntSet();

            for(int i = 0; i < neighbors.size; i++){
                LinkedBuild nb = neighbors.get(i);
                if(visited.contains(nb.tile.pos())) continue;
                subGroups.add(collectGroup(nb, oldLeader, visited));
            }

            if(subGroups.size == 1){
                setupNewLeader(subGroups.get(0), oldItems);
                return;
            }

            int totalSize = 0;
            for(int i = 0; i < subGroups.size; i++){
                totalSize += subGroups.get(i).size;
            }

            Seq<ItemModule> subItemsList = new Seq<>();
            for(int i = 0; i < subGroups.size; i++){
                int size = subGroups.get(i).size;
                float ratio = totalSize > 0 ? (float) size / totalSize : 0f;

                ItemModule subItems = new ItemModule();
                for(Item item : content.items()){
                    subItems.set(item, (int) (oldItems.get(item) * ratio));
                }
                subItemsList.add(subItems);
            }

            //补足整数分配的余数, 确保物品总数不变
            for(Item item : content.items()){
                int original = oldItems.get(item);
                int assigned = 0;
                for(int i = 0; i < subItemsList.size; i++){
                    assigned += subItemsList.get(i).get(item);
                }
                int remainder = original - assigned;
                for(int i = 0; i < remainder && i < subItemsList.size; i++){
                    subItemsList.get(i).add(item, 1);
                }
            }

            for(int i = 0; i < subGroups.size; i++){
                setupNewLeader(subGroups.get(i), subItemsList.get(i));
            }
        }

        /** 从 start 出发 BFS 收集同组成员 */
        Seq<LinkedBuild> collectGroup(LinkedBuild start, LinkedBuild targetLeader, IntSet visited){
            Seq<LinkedBuild> result = new Seq<>();
            Queue<LinkedBuild> queue = new Queue<>();

            queue.add(start);
            visited.add(start.tile.pos());
            result.add(start);

            while(queue.size > 0){
                LinkedBuild current = queue.removeFirst();
                for(int i = 0; i < current.proximity.size; i++){
                    Building other = current.proximity.get(i);
                    if(!(other.block == block && other instanceof LinkedBuild lb && lb.team == team)) continue;
                    //组长被拆除时 onRemoved 早于 removeFromProximity, 自身仍残留在邻居的 proximity 中,
                    //不排除的话会被当成组员重新收编, 导致组容量虚高 / groupLeader 指向已删除建筑
                    if(lb.removed) continue;

                    if(lb.leader() != targetLeader || !visited.add(lb.tile.pos())) continue;

                    result.add(lb);
                    queue.add(lb);
                }
            }
            return result;
        }

        /** 为一组建筑选新组长并同步 items */
        void setupNewLeader(Seq<LinkedBuild> members, ItemModule newItems){
            LinkedBuild newLeader = members.get(0);
            for(int i = 1; i < members.size; i++){
                if(members.get(i).tile.pos() < newLeader.tile.pos()){
                    newLeader = members.get(i);
                }
            }

            newLeader.items = newItems;
            newLeader.groupLeader = null;
            newLeader.groupSize = members.size;

            for(int i = 0; i < members.size; i++){
                LinkedBuild m = members.get(i);
                if(m == newLeader) continue;

                m.items = newItems;
                m.groupLeader = newLeader;
                m.groupSize = 1;
            }

            newLeader.clampResources();
        }

        /** 将物品裁剪到容量上限内 */
        void clampResources(){
            if(world.isGenerating()) return;

            int cap = groupSize * itemCapacity;
            for(Item item : content.items()){
                if(items.get(item) > cap){
                    items.set(item, cap);
                }
            }
        }

        /** 获取组长, 自身为组长时返回 this */
        LinkedBuild leader(){
            return groupLeader == null ? this : groupLeader;
        }

        /** 获取组内所有成员 */
        public Seq<LinkedBuild> getGroupMembers(){
            LinkedBuild l = leader();
            return collectGroup(l, l, new IntSet());
        }

        @Override
        public void onRemoved(){
            //必须先标记: onRemoved 早于 removeFromProximity 执行
            removed = true;
            if(groupLeader != null || groupSize > 1){
                removeFromGroup();
            }
            super.onRemoved();
        }

        @Override
        public void updateTile(){
            if(groupLeader != null && groupLeader.items != items){
                items = groupLeader.items;
            }

            if(outputLiquids){
                dumpLiquid(liquids.current());
            }
        }

        /**
         * 存档: 共享物品池只由组长写, 组员写空.
         * 原因: 读档时 World.endMapLoad() 会逐格重建 proximity 并触发 mergeGroups(),
         * 而 mergeGroups() 是把各组的 items 相加; 如果每个组员都存了一份完整的组总量,
         * 读档后就会被重复求和 (N 个组员 => N 倍). 而且读档期间 world.isGenerating()
         * 为 true, clampResources() 不会兜底. 所以只让组长写, 组员写空池.
         */
        @Override
        public void writeAll(Writes write){
            ItemModule real = items;
            boolean member = groupLeader != null && groupLeader != this;
            if(member) items = emptyPool;
            try{
                super.writeAll(write);
            }finally{
                items = real;
            }
        }

        /** 网络同步必须写完整物品: 客户端 readSync() 会把读到的数据直接写进当前模块(可能就是共享池) */
        @Override
        public void writeSync(Writes write){
            writeBase(write);
            write(write);
        }

        @Override
        public boolean canPickup(){
            return groupLeader == null;
        }

        @Override
        public void pickedUp(){
            if(groupLeader != null){
                removeFromGroup();
            }
        }
    }
}
