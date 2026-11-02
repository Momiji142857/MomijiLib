以下是ai总结的使用说明

一、注意事项
------------------------------------------------------------
  本库的类都靠 JSON 里的 "type" 指定, 名字必须带 Momiji- 前缀:
    "type": "Momiji-ItemLiquidJunction"
  写成 "ItemLiquidJunction" 会找不到类.

二、本库有哪些类
------------------------------------------------------------
[方块]
  Momiji-MultiCrafter              多配方工厂: 一次运行一个配方, 可切换
  Momiji-OmniCrafter               单配方全能工厂: 物品/液体/电力/热量/随机产出
  Momiji-LinkedBlock               相邻同类自动共享库存的仓库
  Momiji-LinkedDrill               相邻同类自动共享库存的钻头
  Momiji-ItemLiquidJunction        物品和液体都能过的交叉器
  Momiji-BatchDumpBridge           一次倒很多物品的物品桥
  Momiji-SquareOverdriveProjector  范围是方形的超频投影器
[武器]
  Momiji-StatefulWeapon            给部件提供每帧状态的武器 (配合下面两个用)
[部件]
  Momiji-StatefulRegionPart        图层/颜色可以随进度变化的部件
  Momiji-TubePart                  能展开成多根旋转炮管的部件

三、逐个说明
------------------------------------------------------------
[Momiji-MultiCrafter]多配方工厂
  一个方块里放多个配方, 玩家在界面里选一个运行; 支持物品/液体/电力/热量/载荷.
  基于原版 Block; 单个配方的行为对齐 GenericCrafter / HeatCrafter / HeatProducer

  新增字段
    以下格式均为 - 字段名 (类型, 默认值)   介绍

    - recipes (数组, 必填)            配方列表, 至少 1 个; 每个配方的字段见下
    - craftEffect (特效, none)        配方没写 craftEffect 时用这个
    - updateEffect (特效, none)       配方没写 updateEffect 时用这个
    - switchEffect (特效, rotateBlock)  切换配方时的特效
    - dumpExtraLiquid (布尔, true)    液体产物满了也继续生产, 多的排掉
    - ignoreLiquidFullness (布尔, false)  完全不看液体满没满
    - maxEfficiency (float, 1)        热量效率上限
    - overheatScale (float, 1)        超出热量需求后, 多余热量转效率的倍率
    - drawer (绘制器, DrawDefault)    外观, 写法同原版
    - attribute (属性, 无)            环境加成, 例如 "water"
    - baseEfficiency / boostScale / maxBoost (float, 1)  属性加成的底值 / 系数 / 上限

  配方字段
    - inputItems / outputItems (数组, 无)      例如 [{"item": "copper", "amount": 1}]
    - inputLiquids / outputLiquids (数组, 无)  例如 [{"liquid": "water", "amount": 0.1}]
    - inputPower / outputPower (float, 0)      电力, 写法同原版
    - inputHeat / outputHeat (float, 0)        热量
    - inputPayloads / outputPayloads (数组, 无)  载荷, 例如 [{"item": "dagger", "amount": 1}]
    - craftTime (float, 80)                    做一次要多少 tick
    - allowOverdrive (布尔, true)              该配方是否吃超速; 输出热量的配方始终不吃
    - warmupSpeed (float, 0.019)               预热速度
    - craftEffect / updateEffect / switchEffect (特效, 用方块默认)
    - updateEffectChance (float, 0.04) / updateEffectSpread (float, 4)
    - craftSound / updateSound (音效, 无)
    - inputPayloadCapacity (整型, 0) / inputPayloadMultiplier (float, 2)
    - outputPayloadQueueLimit (整型, -1)
    - liquidOutputDirections (整型数组, [-1])  -1 自动, 0-3 指定方向
    - 单数简写: inputItem / inputLiquid / outputItem / outputLiquid
      等价于只放 1 个的数组

  需要注意的继承字段
    - itemCapacity / liquidCapacity 默认 -1, 表示按配方自动算; 写了就用你写的
    - canOverdrive / outputsPower / outputsLiquid / consumesPower / acceptsPayload
      会自动判断, 写了也可能被覆盖
    - 有载荷输出的配方会强制 rotate = true
    - recipes 必须有, 否则方块没有功能

  例子
  {
    "type": "Momiji-MultiCrafter",
    "recipes": [
      {
        "inputItem": {"item": "copper", "amount": 1},
        "outputItem": {"item": "lead", "amount": 1},
        "craftTime": 60
      },
      {
        "inputItem": {"item": "beryllium", "amount": 1},
        "inputPower": 0.5,
        "outputItem": {"item": "oxide", "amount": 1},
        "craftTime": 120,
        "allowOverdrive": false
      }
    ]
  }

[Momiji-OmniCrafter]单配方全能工厂
  一个方块同时具备物品/液体/电力/热量/环境加成/随机产出, 相当于把原版几种工厂合在一起.
  基于原版 GenericCrafter

  新增字段
    - heatRequirement (float, 0)       100% 效率所需热量; 0 = 不耗热
    - overheatScale (float, 1)         超出需求后, 多余热量转换成效率的倍率
    - maxEfficiency (float, 4)         过热后的效率上限
    - heatOutput (float, 0)            100% 效率时输出的热量
    - splitHeat (布尔, false)           热量向四周平分
    - warmupRate (float, 0.15)         热量输出的变化速度
    - attribute (属性, 无)              环境加成, 例如 "heat" / "water"
    - baseEfficiency (float, 1)        属性加成的基础效率
    - boostScale (float, 1)            每点属性的加成系数
    - maxBoost (float, 1)              属性加成上限
    - minEfficiency (float, -1)        属性值低于此数时不工作
    - displayEfficiency (布尔, true) / displayEfficiencyScale (float, 1)  效率条显示
    - scaleLiquidConsumption (布尔, false)  效率提高时液体消耗跟着提高
    - dumpExtraItem (布尔, false)      固定产物超出容量时销毁, 继续生产
    - dumpRandomItem (布尔, false)     随机产物超出容量时销毁, 继续生产
    - ignoreItemFullness (布尔, false) 完全无视物品满仓
    - randomResults (数组, 无)         随机产物, 格式同原版分离机, 例如 [{"item": "scrap", "amount": 1}]
    - randomItemCapacity (整型, -1)    随机产物总容量; -1 = 用 itemCapacity
    - emptyWeight (整型, 0)            轮空权重, 越大越容易不出货
    - payloadSpeed / payloadRotateSpeed / isPayloadRouter  未使用, 不用写

  需要注意的继承字段
    - outputItems / outputLiquids / craftTime / consumes / drawer 等与原版 GenericCrafter 相同
    - heatOutput > 0 时会自动 canOverdrive = false, 超频不会放大热量产出
    - 固定产物满仓会停产, 除非 dumpExtraItem = true
    - 随机产物默认按 randomItemCapacity 算总容量, 满了会停产, 除非 dumpRandomItem = true

  例子
  {
    "type": "Momiji-OmniCrafter",
    "craftTime": 120,
    "consumes": {"power": 0.5},
    "outputItems": ["oxide/1"],
    "heatRequirement": 10,
    "heatOutput": 5,
    "maxEfficiency": 2,
    "attribute": "heat",
    "randomResults": [{"item": "scrap", "amount": 1}],
    "emptyWeight": 9
  }

[Momiji-LinkedBlock]共享库存仓库
  相邻的同类建筑 (同队) 会自动连成一组, 共用 1 个物品池; 液体各存各的, 按需向外排.
  基于原版 Block, 原理类似仓库扩容核心

  新增字段
    - outputLiquids (布尔, true)   是否把自身的液体向外排
    - linkLiquids (布尔, true)     只向同类建筑排液; false = 谁都给
    - maxGroupSize (整型, 64)      1 组最多几个建筑; 满了之后新建筑另起 1 组

  需要注意的继承字段
    - itemCapacity: 组内每种物品的上限 = itemCapacity * 组内建筑数
    - 物品是组内共享的; 拆掉 1 个建筑时, 物品按剩下各组的大小比例分配
    - 只有组长能被拾取; 拾取组员会先退出组
    - allowResupply 默认为 true, envEnabled 默认为 any, flags 默认为 storage
    - 其他同原版方块字段

  例子
  {
    "type": "Momiji-LinkedBlock",
    "itemCapacity": 100,
    "maxGroupSize": 16
  }

[Momiji-LinkedDrill]共享库存钻头
  相邻的同类钻头 (同队) 会自动连成一组, 共用 1 个物品池; 挖掘逻辑与原版钻头相同.
  基于本库 LinkedBlock + 原版 Drill

  新增字段 (和原版钻头 Drill 同名同义)
    - tier (整型, 0)                     能挖的硬度上限
    - drillTime (float, 300)             挖 1 个物品的基准时间
    - hardnessDrillMultiplier (float, 50)  硬度对钻速的影响
    - drillMultipliers (映射, 无)        每种物品的钻速倍率, 一般不用写
    - liquidBoostIntensity (float, 1.6)  液体增幅倍率
    - warmupSpeed (float, 0.015)         启动速度
    - blockedItem (物品, 无)             禁止挖的物品
    - blockedItems (数组, 无)            禁止挖的物品, 多个
    - drawMineItem (布尔, true)          是否画正在挖的物品
    - drillEffect (特效, mine) / drillEffectChance (float, 0.02) / drillEffectRnd (float, -1)
    - updateEffect (特效, pulverizeSmall) / updateEffectChance (float, 0.02)
    - rotateSpeed (float, 2)             钻头旋转速度
    - drawRim / drawSpinSprite (布尔, false / true)
    - heatColor (颜色, ff5512)

  需要注意的继承字段
    - itemCapacity: 组内物品总数上限 = itemCapacity * 组内建筑数
    - 组内共享物品池, 挖到的东西直接进池
    - 贴图按 "名字-rim" / "-rotator" / "-top" / "-item" 加载
    - 其他同原版钻头字段 (size, requirements 等)

  例子
  {
    "type": "Momiji-LinkedDrill",
    "size": 2,
    "tier": 3,
    "drillTime": 300,
    "itemCapacity": 20,
    "maxGroupSize": 8
  }

[Momiji-ItemLiquidJunction]物品 / 液体交叉器
  同时传递物品和液体的交叉器, 相当于原版两种交叉器合并在 1 个方块里.
  基于原版 LiquidJunction, 物品逻辑同 Junction

  新增字段 (含义与原版物品交叉器 Junction 完全一致)
    - speed (float, 26)           物品从一侧进、对侧出要等的 tick 数; 60 = 1 秒
    - capacity (整型, 6)          每一侧最多排队的物品数
    - displayedSpeed (float, 13)  只影响统计面板显示的物品/秒

  需要注意的继承字段
    - update 在构造里设为 true, 不要写 false, 否则物品不动
    - 液体字段 (hasLiquids 等) 同原版液体交叉器, 不用写
    - 其他同原版方块字段

  例子
  {
    "type": "Momiji-ItemLiquidJunction",
    "speed": 26,
    "capacity": 6
  }

[Momiji-BatchDumpBridge]批量倾倒物品桥
  原版物品桥的批量版: 连上桥时按 transportTime 传; 没连桥时一次把库存倒给周围建筑,
  不再一次只倒 1 个.

  写在哪: 方块 JSON 最外层
  基于: 原版 ItemBridge

  新增字段
    - speed (float, -1)          每 tick 传几个物品; -1 = 改用 transportTime; 0 = 不传
    - dumpPerSecond (整型, 60)   没连桥时, 每秒最多倒几次; 0 = 不倒
    - dumpBudget (整型, 128)     每次倒最多几个物品
    - displayedSpeed (float, -1) 只影响统计面板显示的物品/秒; -1 = 自动算

  需要注意的继承字段
    - transportTime 和 speed 写 1 个就行; transportTime > 0 时 speed 不起作用
    - 两个都不写会退回 transportTime = 2 (30 个/秒), 并且日志报错
    - hasPower 在构造里设为 false
    - range / linkSameType 等原版物品桥字段照常

  例子
  {
    "type": "Momiji-BatchDumpBridge",
    "range": 4,
    "speed": 4,
    "dumpBudget": 128,
    "dumpPerSecond": 60
  }

[Momiji-SquareOverdriveProjector]方形超频投影器
  原版超频投影器, 但范围是方形 (和原版回血投影器一样), 范围用格数表示.
  基于原版 OverdriveProjector

  新增 / 变化字段
    - range (整型, 20)             方形范围的边长, 单位是格
                                   注意: 原版 OverdriveProjector 的 range 是像素
    - phaseRangeBoost (float, 20)  有增幅物品时额外增加的范围, 单位仍是像素
    - reload (float, 60)           每隔多少 tick 加 1 次超频
    - speedBoost (float, 1.5)      超频倍率, 1.5 = +50%
    - speedBoostPhase (float, 0.75)  增幅物品额外提供的倍率
    - useTime (float, 400)         消耗 1 次物品的间隔
    - hasBoost (布尔, true)        是否吃增幅物品
    - baseColor / phaseColor (颜色, feb380 / ffd59e)

  需要注意的继承字段
    - 只有 range 的单位和类型变了, 其他超频逻辑同原版
    - 只影响 canOverdrive = true 的建筑

  例子
  {
    "type": "Momiji-SquareOverdriveProjector",
    "range": 10,
    "speedBoost": 1.5
  }

[Momiji-StatefulWeapon]有状态武器
  原版 Weapon 加上 "每 tick 把开火状态和每发间隔传给部件" 的功能, 本身没有新字段.
  炮管类部件 (TubePart) 必须用它才会转.
  写在单位 JSON 的 "weapons" 数组里
  基于原版 Weapon

  新增字段
    - 无

  需要注意的继承字段
    - 其他字段同原版武器 (reload / shoot / x / y / mirror 等)
    - reload 要正常写; 自动转 1 圈 = tubeCount * (reload / shoot.shots), reload = 0 会转得飞快
    - 它只影响 parts, 不影响武器本身的射击

  例子
  "weapons": [
    {
      "type": "Momiji-StatefulWeapon",
      "reload": 30,
      "parts": [
        {"type": "Momiji-TubePart", "suffix": "-tube", "tubeCount": 4, "moveX": 7}
      ]
    }
  ]

[Momiji-StatefulRegionPart]动态图层 / 颜色部件
  原版 RegionPart 加上 2 个可选字段: 图层偏移和颜色可以跟着进度变化.
  两个都不写时, 和原版 RegionPart 完全一样.
  写在武器的 "parts" 数组里
  基于原版 RegionPart

  新增字段
    - layerProgress (进度, 无)  绘制时加到 layerOffset 上; 正数往上, 负数往下
    - colorProgress (进度, 无)  0 = color, 1 = colorTo; 要 color 和 colorTo 都写才变色

  进度的写法 (3 选 1)
    - 字符串常量: "warmup" / "reload" / "smoothReload" / "charge" / "recoil" / "heat" / "life" / "time"
    - 数字: 固定值, 例如 0.001
    - 对象: {"type": "warmup", "op": "mul", "amount": 0.001}
      op 可用 inv / slope / clamp / add / mul / min / sin / absin / mod / loop / curve 等

  需要注意的继承字段
    - 其他字段同原版 RegionPart (suffix / x / y / moveX / regions 等)
    - colorProgress 生效时, 颜色只在 color 和 colorTo 之间插值

  例子
  {
    "type": "Momiji-StatefulRegionPart",
    "suffix": "-glow",
    "layerProgress": {"type": "warmup", "op": "mul", "amount": 0.001},
    "color": "ffffff",
    "colorTo": "ff0000",
    "colorProgress": "warmup"
  }

[Momiji-TubePart]多根旋转炮管
  把 1 根炮管的贴图复制成 1 圈, 让它们绕轴心转: 转到前面的画在上层、颜色亮,
  转到后面的压在下层、颜色暗. 开火时加速转, 停火时慢慢停.

  写在哪: 武器的 "parts" 数组里
  基于: 本库 StatefulRegionPart -> 原版 RegionPart
  必须配合: 同一把武器的 "type" 要写 "Momiji-StatefulWeapon",
            否则部件收不到每帧状态, 炮管不会转 (只会静止显示).

  新增字段
    - tubeCount (整型, 0)         炮管数量; 0 = 不展开. 建议 2-32, 一般 3 到 6 根足够
    - spinTime (float, -1)        转 1 圈要多少 tick; <= 0 时自动 = tubeCount * 每发间隔
    - spinUpSpeed (float, 0.15)   开火后加速的快慢, 越大越快
    - spinDownSpeed (float, 0.05) 停火后减速的快慢, 越大停得越快
    - depthFactor (float, 0.0005) 前后遮挡的图层差; 负数翻转前后
    - shadeAmount (float, 0.5)    前后明暗差; 0 = 不变色, 负数翻转

  需要注意的继承字段
    - moveX (默认 0): 炮管来回摆动的幅度 (像素); 不写 (moveY 也为 0) 时所有炮管会叠在一起
    - color 是转到最前面时的颜色, colorTo 是最后面时的颜色; 想前亮后暗就让 color 更亮
    - progress / mirror / clampProgress / layerProgress / colorProgress
      由本类自动设置, 不要写
    - 其他字段同原版 RegionPart (suffix / x / y / layerOffset / regions 等)

  例子
  "weapons": [
    {
      "type": "Momiji-StatefulWeapon",
      "reload": 30,
      "parts": [
        {
          "type": "Momiji-TubePart",
          "suffix": "-tube",
          "tubeCount": 4,
          "x": 0,
          "y": 5,
          "moveX": 7,
          "color": "ffffff",
          "colorTo": "777777",
          "layerOffset": 0.1
        }
      ]
    }
  ]
