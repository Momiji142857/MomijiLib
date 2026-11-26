package momijiLib.type.world.blocks.distribution;

import arc.struct.ObjectMap;
import arc.util.Log;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.world.ItemBuffer;
import mindustry.world.blocks.distribution.ItemBridge;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.content;

/**
 * 带输入筛选与跨桥转化的缓冲物品桥。<p>
 * 相对原版 {@code mindustry.world.blocks.distribution.BufferedItemBridge} 改三处:
 * <ol>
 *   <li>{@link #outputs} 规定允许输入的物品, 并为每种物品指定一个输出物品</li>
 *   <li>已配对的输入端把物品交给对端时, 按 {@link #outputs} 替换成输出物品</li>
 *   <li>{@link #minLinkGap}/{@link #maxLinkGap} 限制两桥之间允许的间隔格数</li>
 * </ol>
 * 物品进入输入端后先按官方 {@link ItemBuffer} 机制在缓冲区内停留 {@link #speed} tick,
 * 停留时间满足后跨桥并立即变成对应的输出物品, 再由输出端 dump 给相邻建筑。<p>
 * 映射表是方块级配置, 不写入存档; 为空时退回原版行为(不过滤、不转化)。
 * Java 里用 {@link #output(Item, Item)} 配置, JSON 里用 {@link #itemMap} 配置。
 *
 * @author Momiji142857
 * @see ItemBridge
 * @since 2026-11-26
 */
public class TransformItemBridge extends ItemBridge{
    public final int timerAccept = timers++;

    /** 物品在缓冲区内停留的时间, 单位 tick (60 tick = 1 秒). 受 overdrive 影响. */
    public float speed = 40f;
    /** 缓冲区容量, 即同时在途的物品数上限. */
    public int bufferCapacity = 50;
    /** 统计栏显示的吞吐量 (物品/秒). */
    public float displayedSpeed = 11f;

    /** 输入物品 -> 输出物品; key 集合同时作为输入白名单. 为空时不过滤、不转化. */
    public ObjectMap<Item, Item> outputs = new ObjectMap<>();
    /** JSON 配置用: 每项形如 "输入物品名:输出物品名", 在 init() 时解析并合并进 {@link #outputs}. */
    public String[] itemMap = {};
    /** 允许连接的桥之间的最小间隔格数 (两桥中心距 - 1); -1 表示不启用间隔限制. */
    public int minLinkGap = -1;
    /** 允许连接的桥之间的最大间隔格数; 启用间隔限制时若为 -1 则取 minLinkGap. */
    public int maxLinkGap = -1;

    public TransformItemBridge(String name){
        super(name);
        hasPower = false;
        hasItems = true;
        canOverdrive = true;
    }

    /** 追加一条"输入物品 -> 输出物品"映射, 返回自身以便链式配置. */
    public TransformItemBridge output(Item input, Item output){
        outputs.put(input, output);
        return this;
    }

    @Override
    public void init(){
        // 先解析 JSON 配置, 再规范化连接间隔
        parseItemMap();

        // 任一间隔字段 >= 0 即启用间隔限制
        if(minLinkGap >= 0 || maxLinkGap >= 0){
            if(minLinkGap < 0) minLinkGap = 0;
            if(maxLinkGap < minLinkGap) maxLinkGap = minLinkGap;
            // range 同时驱动 updateClipRadius、放置高亮与连线绘制, 必须覆盖到最大可连距离
            range = maxLinkGap + 1;
        }

        super.init();
    }

    /** 把 {@link #itemMap} 里的 "输入:输出" 字符串解析成物品映射. 名字无效时跳过并告警. */
    protected void parseItemMap(){
        for(String entry : itemMap){
            if(entry == null || entry.isEmpty()) continue;

            int split = entry.indexOf(':');
            if(split <= 0 || split >= entry.length() - 1){
                Log.warn("[@] Invalid itemMap entry: '@' (expected 'input:output')", name, entry);
                continue;
            }

            Item input = content.item(entry.substring(0, split).trim());
            Item output = content.item(entry.substring(split + 1).trim());

            if(input == null || output == null){
                Log.warn("[@] Unknown item in itemMap entry: '@'", name, entry);
                continue;
            }

            outputs.put(input, output);
        }
    }

    @Override
    public void setStats(){
        super.setStats();

        //Hard to calculate, fps and overdive reliant. Movement speed taken from testing
        stats.add(Stat.itemsMoved, displayedSpeed, StatUnit.itemsSecond);
    }

    @Override
    public boolean positionsValid(int x1, int y1, int x2, int y2, int baseRange){
        // 未启用间隔限制时沿用原版: 共线且距离不超过 baseRange
        if(maxLinkGap < 0) return super.positionsValid(x1, y1, x2, y2, baseRange);

        int dist;
        if(x1 == x2){
            dist = Math.abs(y1 - y2);
        }else if(y1 == y2){
            dist = Math.abs(x1 - x2);
        }else{
            return false;
        }

        int gap = dist - 1;
        return gap >= minLinkGap && gap <= maxLinkGap;
    }

    public class TransformItemBridgeBuild extends ItemBridgeBuild{
        ItemBuffer buffer = new ItemBuffer(bufferCapacity);

        @Override
        public void updateTransport(Building other){
            if(buffer.accepts() && items.total() > 0){
                buffer.accept(items.take());
            }

            Item item = buffer.poll(speed / timeScale);
            if(timer(timerAccept, 4 / timeScale) && item != null){
                // 跨桥的瞬间替换成对应的输出物品
                Item out = outputs.get(item, item);
                if(other.acceptItem(this, out)){
                    moved = true;
                    other.handleItem(this, out);
                    buffer.remove();
                }
            }
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            if(!super.acceptItem(source, item)) return false;

            // 对端推过来的是已经转化过的物品, 不受输入白名单限制
            if(linked(source)) return true;

            return outputs.isEmpty() || outputs.containsKey(item);
        }

        @Override
        public void doDump(){
            dump();
        }

        @Override
        public void write(Writes write){
            super.write(write);
            buffer.write(write);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            buffer.read(read);
        }
    }
}
