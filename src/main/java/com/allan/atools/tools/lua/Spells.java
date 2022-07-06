package com.allan.atools.tools.lua;

import java.util.ArrayList;

public final class Spells {
    /*


local function pairsByKeys(t)
    local a = {}
    for n in pairs(t) do a[#a + 1] = n end
    table.sort(a)
    local i = 0
    return function ()
        i = i + 1
        return a[i], t[a[i]]
    end
end

local tmp
print("ArrayList<Spells.Source> list = new ArrayList<>();")
for level, spellsAtLevel in pairsByKeys(tab) do
        for _, spell in ipairs(spellsAtLevel) do
			tmp =  "list.add(new Spells.Source("..level..", "
			tmp = tmp..spell.id..", "..spell.cost..", "

			if (spell.requiredIds ~= nil) then
			    tmp = tmp.."new int[]{"
                for _, reqId in ipairs(spell.requiredIds) do
					tmp = tmp..reqId..", "
                end
				tmp = tmp.."}, "
      		else
				tmp = tmp.."null, "
			end

			if spell.requiredTalentId ~= nil then
				tmp = tmp..spell.requiredTalentId..", "
		    else
				tmp = tmp.."0, "
			end

			if spell.faction ~= nil then
				tmp = tmp.."\""..spell.faction.."\""
		    else
				tmp = tmp.."null"
			end

			tmp = tmp.."));"
			print(tmp)
		end
end


     */
    static ArrayList<Source> list = new ArrayList<Source>();
    static ArrayList<Target> spells = new ArrayList<>(); //不做全列表，最后再转。这里按照链表往下追
    static ArrayList<Target> hasRequiresSpells = new ArrayList<>();

    public static void main(String[] args) {
        parse();
    }

    public static void parse() {
//        list.addAll(LuaImport.LuaTbcImport.圣骑());
        list.addAll(LuaImport.LuaTbcImport.法师());
//        list.addAll(LuaImport.LuaTbcImport.战士());
//        list.addAll(LuaImport.LuaTbcImport.术士());
//        list.addAll(LuaImport.LuaTbcImport.潜行());
//        list.addAll(LuaImport.LuaTbcImport.牧师());
//        list.addAll(LuaImport.LuaTbcImport.猎人());
//        list.addAll(LuaImport.LuaTbcImport.萨满());
//        list.addAll(LuaImport.LuaTbcImport.德鲁伊());

        for (int i = 0; i < list.size(); i++) { //就是让size变化
            if (list.get(i).requiredIds == null) {
                var src = list.remove(i--);
                var t = new Target(src);
                t.rank = 1;
                spells.add(t);
            } else {
                var src = list.remove(i);
                hasRequiresSpells.add(new Target(src));
            }
        }
        //这样我们得到了一堆，不需要依赖的刚刚学会的基础技能。当然还漏掉了一堆出生就送的技能，稍后从reqIds里面拿
        printCurrentSpells();

        //再次将requiresIds中找不到的当做基础法术搞进来
        for (int i = 0; i < list.size(); i++) { //就是让size变化
            var curSrc = list.get(i);
            if (curSrc.requiredIds != null && curSrc.requiredIds.length == 1) {
                var reqId = curSrc.requiredIds[0];
                if(findTargetById(reqId) == null) {
                    System.out.println("todo 应该是默认1级送的技能 " + reqId);
                    var t = new Target();
                    t.spellId = reqId;
                    t.rank = 1;
                    spells.add(t);
                }
            }
        }

        printCurrentSpells();

        //接着，
        while(list.size() > 0) {
            for (int i = 0; i < list.size(); i++) { //就是让size变化
                var curSrc = list.get(i);
                if (curSrc.requiredIds != null && curSrc.requiredIds.length == 1) {
                    var reqId = list.get(i).requiredIds[0];
                    var prevTarget = findTargetById(reqId);
                    if (prevTarget != null) {
                        var t = new Target(curSrc);
                        t.rank = prevTarget.rank + 1;
                        prevTarget.next = t;
                        list.remove(curSrc);
                    } else {
                        throw new RuntimeException("err: " + reqId);
                        //targetList.add(t);
                    }
                }
            }

            if (isOnlyRequireIdsBigthan1()) {
                break;
            }
        }
    }

    private static void printCurrentSpells() {
        StringBuilder sb = new StringBuilder("{");
        for (var t : spells) {
            //System.out.println(t);
            sb.append(t.spellId).append(',');
        }
        sb.append('}');
        System.out.println("用于游戏中遍历法术：" + sb);
    }

    private static boolean isOnlyRequireIdsBigthan1() {
        for (var target : list) {
            if (target.requiredIds != null && target.requiredIds.length == 1) {
                return false;
            }
        }

        return true;
    }

    private static Target findTargetById(int id) {
        for (var target : spells) {
            Target t = target;
            while(t != null) {
                if (t.spellId == id) {
                    return t;
                }

                t = t.next;
            }
        }
        return null;
    }

    static class Source{
        public Source(int level, int id, int cost) {
            this.level = level;
            this.id = id;
            this.cost = cost;
        }

        public Source(int level, int id, int cost, int[] requiredIds, int requiredTalentId, String faction) {
            this.level = level;
            this.id = id;
            this.cost = cost;
            this.requiredIds = requiredIds;
            this.requiredTalentId = requiredTalentId;
            this.faction = faction;
        }

        static final String Faction_ALLIANCE = "Alliance";
        static final String Faction_HORDE = "Horde";

        public int level; //key
        public int id;
        public int cost;
        public int[] requiredIds;
        public int requiredTalentId;
        public String faction;
    }

    static class Target{
        public Target() {}

        public Target(Source src) {
            spellId = src.id;
            level = src.level;
        }

        public int spellId; //本法术id  key
        public int level; //多少级可以学这个法术
        public int rank; //法术是第几级
        public Target next = null; //下一级别法术id是什么 如果是-1则表示spellId已经是最高

        @Override
        public String toString() {
            return "{" +
                    "spellId=" + spellId +
                    ", level=" + level +
                    ", rank=" + rank +
                    '}';
        }
    }
}
