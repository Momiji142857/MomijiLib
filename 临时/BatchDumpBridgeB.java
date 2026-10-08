package momiji;

import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.world.blocks.distribution.BufferedItemBridge;

import static mindustry.Vars.content;

/**
 * 批量倾倒版缓冲物品桥.<p>
 * 完全复用原版 {@link BufferedItemBridge} 的桥间传输逻辑 (timer(4) + buffer.poll(age) + buffer/write/read),
 * 仅重写 dump(Item) 把单次 1 个改为一次批量倒空, 解决原版桥通过相邻输出 (doDump 路径) 时的吞吐瓶颈.
 * </p>
 *
 * @author Momiji142857
 * @since 2026-08-01
 * @see BufferedItemBridge
 */
public class BatchDumpBridgeB extends BufferedItemBridge {

    /** 单次 dump() 调用的物品预算上限. 默认值略大于原版桥链 300 个/s 的常用规模. */
    public int dumpBudget = 512;

    public BatchDumpBridgeB(String name) {
        super(name);
    }

    public class BatchDumpBridgeBuild extends BufferedItemBridgeBuild {

        @Override
        public boolean dump(Item todump) {
            if (!block.hasItems || items.total() == 0 || proximity.size == 0
                    || (todump != null && !items.has(todump))) return false;

            int proxSize = proximity.size;
            int startDir = cdump;
            int totalItemTypes = content.items().size;
            Object[] itemArray = content.items().items;
            int budget = Math.min(items.total(), dumpBudget);
            int dumped = 0;
            boolean anyPushed = false;

            while (dumped < budget && items.total() > 0) {
                int prevDumped = dumped;
                for (int dirStep = 0; dirStep < proxSize && dumped < budget; dirStep++) {
                    int dirIdx = (startDir + dumped / proxSize + dirStep) % proxSize;
                    Building other = proximity.get(dirIdx);
                    if (other == null) continue;

                    if (todump == null) {
                        for (int typeIdx = 0; typeIdx < totalItemTypes && items.total() > 0; typeIdx++) {
                            Item item = (Item) itemArray[typeIdx];
                            if (item == null || !items.has(item) || !canDump(other, item)) continue;

                            while (dumped < budget && items.has(item) && canDump(other, item)) {
                                if (other.acceptItem(this, item)) {
                                    other.handleItem(this, item);
                                    items.remove(item, 1);
                                    incrementDump(proxSize);
                                    dumped++;
                                    anyPushed = true;
                                } else {
                                    break;
                                }
                            }
                            if (dumped >= budget) break;
                        }
                    } else {
                        if (!canDump(other, todump)) continue;
                        while (dumped < budget && items.has(todump) && canDump(other, todump)) {
                            if (other.acceptItem(this, todump)) {
                                other.handleItem(this, todump);
                                items.remove(todump, 1);
                                incrementDump(proxSize);
                                dumped++;
                                anyPushed = true;
                            } else {
                                break;
                            }
                        }
                    }
                }
                if (dumped == prevDumped) break;
            }
            return anyPushed;
        }
    }
}
